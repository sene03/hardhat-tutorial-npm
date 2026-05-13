package com.example.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import com.example.server.config.BesuProperties;
import com.example.server.domain.BankWallet;
import com.example.server.domain.ContractName;
import com.example.server.domain.DeployedContract;
import com.example.server.domain.Institution;
import com.example.server.dto.BalanceResponse;
import com.example.server.dto.TransferRequest;
import com.example.server.dto.TransferResponse;
import com.example.server.dto.WalletResponse;
import com.example.server.repository.BankWalletRepository;
import com.example.server.repository.DeployedContractRepository;
import com.example.server.repository.InstitutionRepository;

@Service
public class TokenService {

    private static final BigInteger TRANSFER_GAS_LIMIT = BigInteger.valueOf(500_000);
    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;
    private static final Long CBDC_INSTITUTION_ID = 1L;

    private final BesuProperties besuProperties;
    private final DeployedContractRepository deployedContractRepository;
    private final InstitutionRepository institutionRepository;
    private final BankWalletRepository bankWalletRepository;
    private final WalletKeyCipher walletKeyCipher;

    // 디버깅용 콘솔 로그 출력
    private static void log(String message) {
        System.out.println("[토큰서비스] " + message);
    }

    // TokenService에서 사용하는 저장소, 설정, 암호화 컴포넌트 주입
    public TokenService(
            BesuProperties besuProperties,
            DeployedContractRepository deployedContractRepository,
            InstitutionRepository institutionRepository,
            BankWalletRepository bankWalletRepository,
            WalletKeyCipher walletKeyCipher
    ) {
        this.besuProperties = besuProperties;
        this.deployedContractRepository = deployedContractRepository;
        this.institutionRepository = institutionRepository;
        this.bankWalletRepository = bankWalletRepository;
        this.walletKeyCipher = walletKeyCipher;
    }

//region 계약 종류 결정
/* institutionId를 기반으로 사용할 토큰 컨트랙트 종류 결정
중앙은행(1번)은 CBDC, 나머지는 예금토큰 사용 */
private ContractName resolveContractNameByInstitutionId(Long institutionId) {
    if (CBDC_INSTITUTION_ID.equals(institutionId)) {
        return ContractName.CBDC;
    }

    return ContractName.DEPOSIT_TOKEN;
}
//endregion

//region ERC20 토큰 잔액 조회
// 지갑 주소를 받아 해당 기관 토큰 컨트랙트의 balanceOf 조회
public BalanceResponse balanceOf(String address) {
    validateAddress(address, "address");

    Long contractInstitutionId = resolveWalletContractInstitutionId(address);
    ContractName contractName = resolveContractNameByInstitutionId(contractInstitutionId);
    String contractAddress = resolveContractAddress(contractInstitutionId, contractName);
    Institution institution = resolveInstitution(contractInstitutionId);

    Web3j web3j = Web3j.build(new HttpService(institution.getRpcEndpoint()));

    try {
        BigInteger balance = balanceOf(web3j, contractAddress, address);
        return new BalanceResponse(address, balance);
    }
    catch (IOException e) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Besu RPC call failed: " + e.getMessage());
    }
    finally {
        web3j.shutdown();
    }
}
//endregion

//region CBDC 직접 송금
// 중앙은행 지갑이 다른 기관/사용자에게 CBDC 직접 송금
private TransferResponse cbdcTransfer(TransferRequest request) {
    log("CBDC 송금 시작: from=" + request.from()
            + ", to=" + request.to()
            + ", amount=" + request.amount());

    Credentials credentials = resolveSigningCredentials(request.from());
    String contractAddress = resolveContractAddress(CBDC_INSTITUTION_ID, ContractName.CBDC);
    Institution institution = resolveInstitution(CBDC_INSTITUTION_ID);

    log("CBDC 송금 컨텍스트: signer=" + credentials.getAddress()
            + ", contract=" + contractAddress
            + ", rpc=" + institution.getRpcEndpoint());

    Web3j web3j = Web3j.build(new HttpService(institution.getRpcEndpoint()));

    try {
        Function function = new Function(
                "transfer",
                List.of(new Address(request.to()), new Uint256(request.amount())),
                List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
        );
        String encodedFunction = FunctionEncoder.encode(function);
        log("CBDC 송금 함수 인코딩: bytes=" + ((encodedFunction.length() - 2) / 2)
                + ", selector=" + encodedFunction.substring(0, Math.min(encodedFunction.length(), 10)));

        RawTransactionManager transactionManager = new RawTransactionManager(
                web3j,
                credentials,
                besuProperties.chainId()
        );

        EthSendTransaction sendResponse = signAndSendTransaction(
                web3j,
                transactionManager,
                credentials.getAddress(),
                contractAddress,
                encodedFunction
        );

        if (sendResponse.hasError()) {
            log("CBDC 송금 전송 오류: " + sendResponse.getError().getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
        }

        log("CBDC 송금 전송 완료: txHash=" + sendResponse.getTransactionHash());

        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());
        log("CBDC 송금 receipt 확인: txHash=" + sendResponse.getTransactionHash()
                + ", status=" + receipt.getStatus()
                + ", gasUsed=" + receipt.getGasUsed()
                + ", blockNumber=" + receipt.getBlockNumber());

        if (!receipt.isStatusOK()) {
            log("CBDC 송금 revert 발생: txHash=" + sendResponse.getTransactionHash());
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "CBDC transaction reverted: " + sendResponse.getTransactionHash()
            );
        }

        return new TransferResponse(
                sendResponse.getTransactionHash(),
                credentials.getAddress(),
                request.to(),
                receipt.getStatus()
        );
    }
    catch (IOException e) {
        log("CBDC 송금 IOException: " + e.getMessage());
        throw new ApiException(HttpStatus.BAD_GATEWAY, "CBDC transaction failed: " + e.getMessage());
    }
    finally {
        log("CBDC 송금 web3 종료");
        web3j.shutdown();
    }
}
//endregion

//region 통합 토큰 송금
/* 요청받은 송금을 상황에 따라
 * CBDC 송금 / 기관간 정산 / 동일기관 operator 송금으로 분기 처리
 */
public TransferResponse transfer(TransferRequest request) {
    log("송금 요청 수신: from="
            + (request == null ? null : request.from())
            + ", to=" + (request == null ? null : request.to())
            + ", amount=" + (request == null ? null : request.amount()));

    validateTransferRequest(request);
    log("송금 요청 검증 완료");

    Long fromInstitutionId = resolveWalletContractInstitutionId(request.from());
    Long toInstitutionId = resolveWalletContractInstitutionId(request.to());
    log("송금 기관 판별 완료: fromInstitutionId=" + fromInstitutionId
            + ", toInstitutionId=" + toInstitutionId);

    // 중앙은행 → CBDC 직접 송금
    if (CBDC_INSTITUTION_ID.equals(fromInstitutionId)) {
        log("송금 분기 선택: CBDC 직접 송금");
        return cbdcTransfer(request);
    }

    // 기관 간 송금 → Settlement 정산
    if (!fromInstitutionId.equals(toInstitutionId)) {
        log("송금 분기 선택: 기관간 Settlement 정산");
        return settlementTransfer(request, fromInstitutionId, toInstitutionId);
    }

    // 동일 기관 내부 송금 → 기관 지갑이 operatorTransfer로 대리 송금
    log("송금 분기 선택: 동일기관 operator 송금");
    return sameInstitutionOperatorTransfer(request, fromInstitutionId);
}
//endregion

//region 동일기관 내부 operator 송금
/* 같은 기관 소속 지갑끼리 송금할 때
 * 사용자 지갑이 직접 서명하지 않고 기관 지갑이 operatorTransfer로 대리 송금
 */
private TransferResponse sameInstitutionOperatorTransfer(
        TransferRequest request,
        Long institutionId
) {
    log("동일기관 operator 송금 시작: institutionId=" + institutionId
            + ", from=" + request.from()
            + ", to=" + request.to()
            + ", amount=" + request.amount());

    Institution institution = resolveInstitution(institutionId);

    Credentials operatorCredentials = walletKeyCipher.decryptCredentials(
            institution.getEncryptedPrivateKey()
    );

    ContractName contractName = resolveContractNameByInstitutionId(institutionId);
    String contractAddress = resolveContractAddress(institutionId, contractName);
    log("동일기관 operator 송금 컨텍스트: operator=" + operatorCredentials.getAddress()
            + ", contractName=" + contractName
            + ", contract=" + contractAddress
            + ", rpc=" + institution.getRpcEndpoint());

    Web3j web3j = Web3j.build(new HttpService(institution.getRpcEndpoint()));

    try {
        BigInteger balance = balanceOf(web3j, contractAddress, request.from());
        log("동일기관 operator 송금 잔액 확인: from=" + request.from()
                + ", balance=" + balance
                + ", amount=" + request.amount());

        if (balance.compareTo(request.amount()) < 0) {
            log("동일기관 operator 송금 실패: 잔액 부족");
            throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient token balance");
        }

        Function function = new Function(
                "operatorTransfer",
                List.of(
                        new Address(request.from()),
                        new Address(request.to()),
                        new Uint256(request.amount())
                ),
                List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
        );
        String encodedFunction = FunctionEncoder.encode(function);
        log("동일기관 operator 송금 함수 인코딩: bytes="
                + ((encodedFunction.length() - 2) / 2)
                + ", selector=" + encodedFunction.substring(0, Math.min(encodedFunction.length(), 10)));

        RawTransactionManager transactionManager = new RawTransactionManager(
                web3j,
                operatorCredentials,
                besuProperties.chainId()
        );

        EthSendTransaction sendResponse = signAndSendTransaction(
                web3j,
                transactionManager,
                operatorCredentials.getAddress(),
                contractAddress,
                encodedFunction
        );

        if (sendResponse.hasError()) {
            log("동일기관 operator 송금 전송 오류: "
                    + sendResponse.getError().getMessage());
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    sendResponse.getError().getMessage()
            );
        }

        log("동일기관 operator 송금 전송 완료: txHash="
                + sendResponse.getTransactionHash());

        TransactionReceipt receipt = waitForReceipt(
                web3j,
                sendResponse.getTransactionHash()
        );
        log("동일기관 operator 송금 receipt 확인: txHash="
                + sendResponse.getTransactionHash()
                + ", status=" + receipt.getStatus()
                + ", gasUsed=" + receipt.getGasUsed()
                + ", blockNumber=" + receipt.getBlockNumber());

        if (!receipt.isStatusOK()) {
            log("동일기관 operator 송금 revert 발생: txHash="
                    + sendResponse.getTransactionHash());
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Same institution operator transfer reverted: "
                            + sendResponse.getTransactionHash()
            );
        }

        return new TransferResponse(
                sendResponse.getTransactionHash(),
                request.from(),
                request.to(),
                receipt.getStatus()
        );
    }
    catch (IOException e) {
        log("동일기관 operator 송금 IOException: " + e.getMessage());
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Same institution operator transfer failed: " + e.getMessage()
        );
    }
    finally {
        log("동일기관 operator 송금 web3 종료");
        web3j.shutdown();
    }
}
//endregion

//region 기관 간 Settlement 정산
// 서로 다른 기관 간 송금을 Settlement 컨트랙트로 처리
// 보내는 기관 토큰 burn → CBDC reserve 이동 → 받는 기관 토큰 mint
private TransferResponse settlementTransfer(
        TransferRequest request,
        Long fromInstitutionId,
        Long toInstitutionId
) {
    log("기관간 Settlement 정산 시작: fromInstitutionId=" + fromInstitutionId
            + ", toInstitutionId=" + toInstitutionId
            + ", from=" + request.from()
            + ", to=" + request.to()
            + ", amount=" + request.amount());

    Institution settlementCaller = resolveInstitution(CBDC_INSTITUTION_ID);
    Credentials credentials = walletKeyCipher.decryptCredentials(
            settlementCaller.getEncryptedPrivateKey()
    );

    String settlementAddress = resolveContractAddress(
            CBDC_INSTITUTION_ID,
            ContractName.CONTRACT
    );
    String fromTokenAddress = resolveContractAddress(
            fromInstitutionId,
            resolveContractNameByInstitutionId(fromInstitutionId)
    );
    String toTokenAddress = resolveContractAddress(
            toInstitutionId,
            resolveContractNameByInstitutionId(toInstitutionId)
    );
    String cbdcAddress = resolveContractAddress(CBDC_INSTITUTION_ID, ContractName.CBDC);

    log("기관간 Settlement 정산 컨텍스트: caller=" + credentials.getAddress()
            + ", rpc=" + settlementCaller.getRpcEndpoint()
            + ", settlement=" + settlementAddress
            + ", cbdc=" + cbdcAddress
            + ", fromToken=" + fromTokenAddress
            + ", toToken=" + toTokenAddress);

    Web3j web3j = Web3j.build(new HttpService(settlementCaller.getRpcEndpoint()));

    try {
        BigInteger fromBalance = balanceOf(web3j, fromTokenAddress, request.from());
        BigInteger toBalance = balanceOf(web3j, toTokenAddress, request.to());
        String fromReserveWallet = bankReserveWallet(web3j, settlementAddress, fromInstitutionId);
        String toReserveWallet = bankReserveWallet(web3j, settlementAddress, toInstitutionId);
        BigInteger fromReserveCbdcBalance = balanceOf(web3j, cbdcAddress, fromReserveWallet);
        log("기관간 Settlement 정산 잔액 스냅샷: fromBalance=" + fromBalance
                + ", toBalance=" + toBalance
                + ", amount=" + request.amount()
                + ", fromReserve=" + fromReserveWallet
                + ", toReserve=" + toReserveWallet
                + ", fromReserveCbdcBalance=" + fromReserveCbdcBalance);

        // 송신자의 예금 토큰 잔액이 이체 금액보다 부족하면 정산 불가
        if (fromBalance.compareTo(request.amount()) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient deposit token balance. address="
                            + request.from()
                            + ", balance="
                            + fromBalance
                            + ", amount="
                            + request.amount()
            );
        }

        // 송신 기관의 CBDC 준비금 잔액이 이체 금액보다 부족하면 정산 불가
        if (fromReserveCbdcBalance.compareTo(request.amount()) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient CBDC reserve balance. institutionId="
                            + fromInstitutionId
                            + ", reserveWallet="
                            + fromReserveWallet
                            + ", balance="
                            + fromReserveCbdcBalance
                            + ", amount="
                            + request.amount()
            );
        }

        Function function = new Function(
                "settle",
                List.of(
                        new Uint256(BigInteger.valueOf(fromInstitutionId)),
                        new Uint256(BigInteger.valueOf(toInstitutionId)),
                        new Address(request.from()),
                        new Address(request.to()),
                        new Uint256(request.amount())
                ),
                List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {})
        );
        String encodedFunction = FunctionEncoder.encode(function);
        log("기관간 Settlement 정산 함수 인코딩: bytes="
                + ((encodedFunction.length() - 2) / 2)
                + ", selector=" + encodedFunction.substring(0, Math.min(encodedFunction.length(), 10)));

        RawTransactionManager transactionManager = new RawTransactionManager(
                web3j,
                credentials,
                besuProperties.chainId()
        );

        EthSendTransaction sendResponse = signAndSendTransaction(
                web3j,
                transactionManager,
                credentials.getAddress(),
                settlementAddress,
                encodedFunction
        );

        if (sendResponse.hasError()) {
            log("기관간 Settlement 정산 전송 오류: " + sendResponse.getError().getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
        }

        log("기관간 Settlement 정산 전송 완료: txHash=" + sendResponse.getTransactionHash());

        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());
        log("기관간 Settlement 정산 receipt 확인: txHash=" + sendResponse.getTransactionHash()
                + ", status=" + receipt.getStatus()
                + ", gasUsed=" + receipt.getGasUsed()
                + ", blockNumber=" + receipt.getBlockNumber());

        if (!receipt.isStatusOK()) {
            log("기관간 Settlement 정산 revert 발생: txHash=" + sendResponse.getTransactionHash());
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Settlement transaction reverted: " + sendResponse.getTransactionHash()
            );
        }

        return new TransferResponse(
                sendResponse.getTransactionHash(),
                request.from(),
                request.to(),
                receipt.getStatus()
        );
    }
    catch (IOException e) {
        log("기관간 Settlement 정산 IOException: " + e.getMessage());
        throw new ApiException(HttpStatus.BAD_GATEWAY, "Settlement transaction failed: " + e.getMessage());
    }
    finally {
        log("기관간 Settlement 정산 web3 종료");
        web3j.shutdown();
    }
}
//endregion

//region 은행 사용자 지갑 생성
// 기관 ID를 받아 새 지갑을 생성하고 private key를 암호화해 bank_wallet 테이블에 저장
    public WalletResponse createWallet(Long institutionId) {
        Institution institution = resolveInstitution(institutionId);

        try {
            Credentials credentials = Credentials.create(Keys.createEcKeyPair());

            String privateKey = Numeric.toHexStringWithPrefixZeroPadded(
                    credentials.getEcKeyPair().getPrivateKey(),
                    64
            );

            String encryptedPrivateKey = walletKeyCipher.encryptPrivateKey(privateKey);

            BankWallet bankWallet = new BankWallet(
                    institution.getId(),
                    credentials.getAddress(),
                    encryptedPrivateKey
            );

            bankWalletRepository.save(bankWallet);

            return new WalletResponse(
                    credentials.getAddress(),
                    encryptedPrivateKey
            );
        }
        catch (Exception e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create wallet: " + e.getMessage()
            );
        }
    }
//endregion

//region 지갑/기관/컨트랙트 조회 헬퍼
// 기관 지갑에서 찾고 없으면 은행지갑에서 찾음
private Long resolveWalletContractInstitutionId(String address) {
    Long institutionId = institutionRepository.findByWalletAddressIgnoreCase(address)
            .map(Institution::getId)
            .or(() -> bankWalletRepository.findByWalletAddressIgnoreCase(address)
                    .map(BankWallet::getInstitutionId))
            .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "No wallet found for address: " + address
            ));

    log("지갑 소속 기관 판별: address=" + address
            + ", institutionId=" + institutionId);

    return institutionId;
}

// 서명에 사용할 Credentials를 기관 지갑에서 찾고 없으면 은행지갑에서 찾음
private Credentials resolveSigningCredentials(String address) {
    Credentials credentials = institutionRepository.findByWalletAddressIgnoreCase(address)
            .map(Institution::getEncryptedPrivateKey)
            .or(() -> bankWalletRepository.findByWalletAddressIgnoreCase(address)
                    .map(BankWallet::getEncryptedPrivateKey))
            .map(walletKeyCipher::decryptCredentials)
            .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "No wallet found for address: " + address
            ));

    log("서명 지갑 확인: requestedAddress=" + address
            + ", signerAddress=" + credentials.getAddress());

    return credentials;
}

    // 기관 ID와 컨트랙트 이름으로 DB에 저장된 배포 컨트랙트 주소 조회
    private String resolveContractAddress(Long institutionId, ContractName contractName) {
        String contractAddress = deployedContractRepository.findByInstitutionIdAndName(institutionId, contractName)
                .map(DeployedContract::getAddress)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "No " + contractName + " contract deployed for institution " + institutionId + ". Call deploy first."
                ));

        log("컨트랙트 주소 조회: institutionId=" + institutionId
                + ", contractName=" + contractName
                + ", address=" + contractAddress);

        return contractAddress;
    }

    // 기관 정보를 조회하고 Besu RPC endpoint가 등록되어 있는지 검증
    private Institution resolveInstitution(Long institutionId) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Institution not found: " + institutionId
                ));

        if (!StringUtils.hasText(institution.getRpcEndpoint())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Institution RPC endpoint not found: " + institutionId
            );
        }

        log("기관 정보 조회: institutionId=" + institutionId
                + ", code=" + institution.getInstitutionCode()
                + ", name=" + institution.getInstitutionName()
                + ", wallet=" + institution.getWalletAddress()
                + ", rpc=" + institution.getRpcEndpoint());

        return institution;
    }
//endregion

//region ERC20 balanceOf eth_call
    // ERC20 balanceOf를 eth_call로 호출하고 uint256 잔액을 디코딩
    private BigInteger balanceOf(Web3j web3j, String contractAddress, String address) throws IOException {
        log("잔액 조회 호출: contract=" + contractAddress + ", address=" + address);

        Function function = new Function(
                "balanceOf",
                List.of(new Address(address)),
                List.of(new TypeReference<Uint256>() {
                })
        );

        String data = FunctionEncoder.encode(function);
        Transaction transaction = Transaction.createEthCallTransaction(null, contractAddress, data);
        EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            log("잔액 조회 오류: " + response.getError().getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
        }

        var decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

        if (decoded.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "No ERC-20 response from contract. Check the contract is deployed on this Besu network."
            );
        }

        BigInteger balance = (BigInteger) decoded.getFirst().getValue();
        log("잔액 조회 결과: contract=" + contractAddress
                + ", address=" + address
                + ", balance=" + balance);

        return balance;
    }

    // settlement 컨트랙트에서 기관 ID에 매핑된 CBDC 준비금 지갑 주소를 조회한다.
    // 주소가 없거나 zero address이면 준비금 미등록으로 간주해 예외를 던진다.
    private String bankReserveWallet(
            Web3j web3j,
            String settlementAddress,
            Long institutionId
    ) throws IOException {
        Function function = new Function(
                "bankReserveWallet",
                List.of(new Uint256(BigInteger.valueOf(institutionId))),
                List.of(new TypeReference<Address>() {
                })
        );

        String data = FunctionEncoder.encode(function);
        Transaction transaction = Transaction.createEthCallTransaction(null, settlementAddress, data);
        EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
        }

        var decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

        if (decoded.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "No reserve wallet response from settlement contract. institutionId=" + institutionId
            );
        }

        String reserveWallet = decoded.getFirst().getValue().toString();

        if (!WalletUtils.isValidAddress(reserveWallet)
                || "0x0000000000000000000000000000000000000000".equalsIgnoreCase(reserveWallet)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Settlement reserve wallet is not registered. institutionId=" + institutionId
            );
        }

        return reserveWallet;
    }
//endregion

//region 트랜잭션 서명/전송 공통 처리
    // pending nonce 충돌을 확인한 뒤 raw transaction에 서명해 Besu로 전송
    private EthSendTransaction signAndSendTransaction(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String senderAddress,
            String contractAddress,
            String data
    ) throws IOException {

        BigInteger latestNonce = getTransactionCount(web3j, senderAddress, DefaultBlockParameterName.LATEST);
        BigInteger pendingNonce = getTransactionCount(web3j, senderAddress, DefaultBlockParameterName.PENDING);
        log("트랜잭션 서명/전송 nonce 확인: sender=" + senderAddress
                + ", latestNonce=" + latestNonce
                + ", pendingNonce=" + pendingNonce
                + ", contract=" + contractAddress
                + ", dataBytes=" + ((data.length() - 2) / 2)
                + ", gasLimit=" + TRANSFER_GAS_LIMIT
                + ", gasPrice=" + PRIVATE_NETWORK_GAS_PRICE);

        if (!latestNonce.equals(pendingNonce)) {
            log("트랜잭션 서명/전송 중단: pending transaction 존재");
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Sender has pending transactions. latestNonce=" + latestNonce + ", pendingNonce=" + pendingNonce
            );
        }

        RawTransaction transaction = RawTransaction.createTransaction(
                pendingNonce,
                PRIVATE_NETWORK_GAS_PRICE,
                TRANSFER_GAS_LIMIT,
                contractAddress,
                BigInteger.ZERO,
                data
        );

        EthSendTransaction response = transactionManager.signAndSend(transaction);

        if (response.hasError()) {
            log("트랜잭션 전송 응답 오류: "
                    + response.getError().getMessage());
        }
        else {
            log("트랜잭션 전송 응답 hash: "
                    + response.getTransactionHash());
        }

        return response;
    }

    // latest 또는 pending 기준으로 지갑 nonce 조회
    private BigInteger getTransactionCount(
            Web3j web3j,
            String address,
            DefaultBlockParameterName blockParameterName
    ) throws IOException {
        EthGetTransactionCount response = web3j.ethGetTransactionCount(address, blockParameterName).send();

        if (response.hasError()) {
            log("nonce 조회 오류: address=" + address
                    + ", block=" + blockParameterName
                    + ", error=" + response.getError().getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
        }

        log("nonce 조회 결과: address=" + address
                + ", block=" + blockParameterName
                + ", nonce=" + response.getTransactionCount());

        return response.getTransactionCount();
    }

    // 트랜잭션 receipt가 생성될 때까지 polling
    private TransactionReceipt waitForReceipt(Web3j web3j, String transactionHash) {
        try {
            log("receipt 대기 시작: txHash=" + transactionHash);

            PollingTransactionReceiptProcessor processor = new PollingTransactionReceiptProcessor(
                    web3j,
                    RECEIPT_POLLING_INTERVAL_MS,
                    RECEIPT_POLLING_ATTEMPTS
            );

            TransactionReceipt receipt = processor.waitForTransactionReceipt(transactionHash);
            log("receipt 대기 완료: txHash=" + transactionHash
                    + ", status=" + receipt.getStatus()
                    + ", gasUsed=" + receipt.getGasUsed()
                    + ", blockNumber=" + receipt.getBlockNumber());

            return receipt;
        }
        catch (Exception e) {
            log("receipt 대기 실패: txHash=" + transactionHash
                    + ", error=" + e.getMessage());
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Timed out waiting for transaction receipt " + transactionHash + ": " + e.getMessage()
            );
        }
    }
//endregion

//region 요청 검증
    // transfer 요청 body, 주소 형식, 금액 유효성 검증
    private static void validateTransferRequest(TransferRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        validateAddress(request.from(), "from");
        validateAddress(request.to(), "to");

        if (request.amount() == null || request.amount().compareTo(BigInteger.ZERO) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "amount must be a positive integer in token base units"
            );
        }
    }

    // Ethereum 주소 형식 검증
    private static void validateAddress(String address, String fieldName) {
        if (!StringUtils.hasText(address) || !WalletUtils.isValidAddress(address)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid Ethereum address");
        }
    }
//endregion
}
