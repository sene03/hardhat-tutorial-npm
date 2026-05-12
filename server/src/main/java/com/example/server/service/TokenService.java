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
import org.web3j.protocol.core.methods.response.EthGetBalance;
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

    private static final BigInteger TRANSFER_GAS_LIMIT = BigInteger.valueOf(100_000);
    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;
    private static final Long CBDC_INSTITUTION_ID = 1L;

    private final BesuProperties besuProperties;
    private final DeployedContractRepository deployedContractRepository;
    private final InstitutionRepository institutionRepository;
    private final BankWalletRepository bankWalletRepository;
    private final WalletKeyCipher walletKeyCipher;

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

	private ContractName resolveContractNameByInstitutionId(Long institutionId) {
    if (CBDC_INSTITUTION_ID.equals(institutionId)) {
        return ContractName.CBDC;
    }

    return ContractName.DEPOSIT_TOKEN;
}

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

    public TransferResponse transfer(TransferRequest request) {
        validateTransferRequest(request);

        Credentials credentials = resolveSigningCredentials(request.from());
        String contractAddress = resolveContractAddress(CBDC_INSTITUTION_ID, ContractName.CBDC);
        Institution institution = resolveInstitution(CBDC_INSTITUTION_ID);

        BalanceResponse balance = balanceOf(request.from());
if (balance.balance().compareTo(request.amount()) < 0){
    throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient token balance");
}

        Web3j web3j = Web3j.build(new HttpService(institution.getRpcEndpoint()));

        try {
            Function function = new Function(
                    "transfer",
                    List.of(new Address(request.to()), new Uint256(request.amount())),
                    List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {
                    })
            );

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
                    FunctionEncoder.encode(function)
            );

            if (sendResponse.hasError()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
            }

            TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

            if (!receipt.isStatusOK()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Transaction reverted: " + sendResponse.getTransactionHash()
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
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Besu RPC transaction failed: " + e.getMessage());
        }
        finally {
            web3j.shutdown();
        }
    }

    public TransferResponse operatorTransfer(TransferRequest request) {
        validateTransferRequest(request);

        Institution operatorInstitution = resolveInstitution(CBDC_INSTITUTION_ID);

        if (!StringUtils.hasText(operatorInstitution.getEncryptedPrivateKey())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Operator encrypted private key not found for institution " + CBDC_INSTITUTION_ID
            );
        }

        Credentials operatorCredentials = walletKeyCipher.decryptCredentials(
                operatorInstitution.getEncryptedPrivateKey()
        );

        String contractAddress = resolveContractAddress(CBDC_INSTITUTION_ID, ContractName.CBDC);

        Web3j web3j = Web3j.build(new HttpService(operatorInstitution.getRpcEndpoint()));

        try {
            BigInteger balance = balanceOf(web3j, contractAddress, request.from());
            if (balance.compareTo(request.amount()) < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient token balance");
            }

            Function function = new Function(
                    "operatorTransfer",
                    List.of(new Address(request.from()), new Address(request.to()), new Uint256(request.amount())),
                    List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {
                    })
            );

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
                    FunctionEncoder.encode(function)
            );

            if (sendResponse.hasError()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
            }

            TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

            if (!receipt.isStatusOK()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Transaction reverted: " + sendResponse.getTransactionHash()
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
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Besu RPC operator transaction failed: " + e.getMessage());
        }
        finally {
            web3j.shutdown();
        }
    }

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

private Long resolveWalletContractInstitutionId(String address) {
    return institutionRepository.findByWalletAddressIgnoreCase(address)
            .map(Institution::getId)
            .or(() -> bankWalletRepository.findByWalletAddressIgnoreCase(address)
                    .map(BankWallet::getInstitutionId))
            .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "No wallet found for address: " + address
            ));
}

// 기관 지갑에서 찾고 없으면 은행지갑에서 찾음
private Credentials resolveSigningCredentials(String address) {
    return institutionRepository.findByWalletAddressIgnoreCase(address)
            .map(Institution::getEncryptedPrivateKey)
            .or(() -> bankWalletRepository.findByWalletAddressIgnoreCase(address)
                    .map(BankWallet::getEncryptedPrivateKey))
            .map(walletKeyCipher::decryptCredentials)
            .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "No wallet found for address: " + address
            ));
}

    private String resolveContractAddress(Long institutionId, ContractName contractName) {
        return deployedContractRepository.findByInstitutionIdAndName(institutionId, contractName)
                .map(DeployedContract::getAddress)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "No " + contractName + " contract deployed for institution " + institutionId + ". Call deploy first."
                ));
    }

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

        return institution;
    }

    private BigInteger balanceOf(Web3j web3j, String contractAddress, String address) throws IOException {
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
            throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
        }

        var decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());

        if (decoded.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "No ERC-20 response from contract. Check the contract is deployed on this Besu network."
            );
        }

        return (BigInteger) decoded.getFirst().getValue();
    }

    private EthSendTransaction signAndSendTransaction(
            Web3j web3j,
            RawTransactionManager transactionManager,
            String senderAddress,
            String contractAddress,
            String data
    ) throws IOException {
        assertNativeBalanceAvailable(web3j, senderAddress);

        BigInteger latestNonce = getTransactionCount(web3j, senderAddress, DefaultBlockParameterName.LATEST);
        BigInteger pendingNonce = getTransactionCount(web3j, senderAddress, DefaultBlockParameterName.PENDING);

        if (!latestNonce.equals(pendingNonce)) {
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

        return transactionManager.signAndSend(transaction);
    }

    private void assertNativeBalanceAvailable(Web3j web3j, String address) throws IOException {
        EthGetBalance response = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();

        if (response.hasError()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
        }

        if (response.getBalance().equals(BigInteger.ZERO)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Sender native balance is zero. Fund the wallet with native coin before sending transactions."
            );
        }
    }

    private BigInteger getTransactionCount(
            Web3j web3j,
            String address,
            DefaultBlockParameterName blockParameterName
    ) throws IOException {
        EthGetTransactionCount response = web3j.ethGetTransactionCount(address, blockParameterName).send();

        if (response.hasError()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
        }

        return response.getTransactionCount();
    }

    private TransactionReceipt waitForReceipt(Web3j web3j, String transactionHash) {
        try {
            PollingTransactionReceiptProcessor processor = new PollingTransactionReceiptProcessor(
                    web3j,
                    RECEIPT_POLLING_INTERVAL_MS,
                    RECEIPT_POLLING_ATTEMPTS
            );

            return processor.waitForTransactionReceipt(transactionHash);
        }
        catch (Exception e) {
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Timed out waiting for transaction receipt " + transactionHash + ": " + e.getMessage()
            );
        }
    }

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

    private static void validateAddress(String address, String fieldName) {
        if (!StringUtils.hasText(address) || !WalletUtils.isValidAddress(address)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid Ethereum address");
        }
    }
}