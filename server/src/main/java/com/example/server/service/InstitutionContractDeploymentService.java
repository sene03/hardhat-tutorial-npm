package com.example.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

import com.example.server.config.BesuProperties;
import com.example.server.domain.ContractName;
import com.example.server.domain.DeployedContract;
import com.example.server.domain.Institution;
import com.example.server.dto.DeployContractRequest;
import com.example.server.dto.DeployContractResponse;
import com.example.server.repository.DeployedContractRepository;
import com.example.server.repository.InstitutionRepository;

@Service
public class InstitutionContractDeploymentService {

    private static final BigInteger DEPLOY_GAS_LIMIT = BigInteger.valueOf(4_000_000);
    private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
    private static final int RECEIPT_POLLING_ATTEMPTS = 60;
    private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;
	private static final Long CBDC_INSTITUTION_ID = 1L;

    private final BesuProperties besuProperties;
    private final TokenArtifactLoader tokenArtifactLoader;
    private final WalletKeyCipher walletKeyCipher;
    private final InstitutionRepository institutionRepository;
    private final DeployedContractRepository deployedContractRepository;

    public InstitutionContractDeploymentService(
            BesuProperties besuProperties,
            TokenArtifactLoader tokenArtifactLoader,
            WalletKeyCipher walletKeyCipher,
            InstitutionRepository institutionRepository,
            DeployedContractRepository deployedContractRepository
    ) {
        this.besuProperties = besuProperties;
        this.tokenArtifactLoader = tokenArtifactLoader;
        this.walletKeyCipher = walletKeyCipher;
        this.institutionRepository = institutionRepository;
        this.deployedContractRepository = deployedContractRepository;
    }

public DeployContractResponse deploy(Long institutionId, DeployContractRequest request) {
    Institution institution = institutionRepository.findById(institutionId)
            .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Institution not found: " + institutionId
            ));

    validateInstitutionDeploymentInfo(institution);

    ContractName contractName = resolveContractName(institution, request);

    deployedContractRepository.findByInstitutionIdAndName(institutionId, contractName)
            .ifPresent(existing -> {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Contract already deployed for institution "
                                + institutionId
                                + " and name "
                                + contractName
                                + ": "
                                + existing.getAddress()
                );
            });

    Credentials credentials = walletKeyCipher.decryptCredentials(
            institution.getEncryptedPrivateKey()
    );

    validateSignerAddress(institution, credentials);

    Web3j web3j = Web3j.build(new HttpService(institution.getRpcEndpoint()));

    try {
        RawTransactionManager transactionManager = new RawTransactionManager(
                web3j,
                credentials,
                besuProperties.chainId()
        );

        EthGetTransactionCount nonceResponse = web3j.ethGetTransactionCount(
                credentials.getAddress(),
                DefaultBlockParameterName.PENDING
        ).send();

        if (nonceResponse.hasError()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, nonceResponse.getError().getMessage());
        }

String bytecode = resolveBytecode(contractName, institution);

RawTransaction deployTransaction = RawTransaction.createContractTransaction(
        nonceResponse.getTransactionCount(),
        PRIVATE_NETWORK_GAS_PRICE,
        DEPLOY_GAS_LIMIT,
        BigInteger.ZERO,
        bytecode
);

        EthSendTransaction sendResponse = transactionManager.signAndSend(deployTransaction);

        if (sendResponse.hasError()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
        }

        TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

        if (!receipt.isStatusOK()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Contract deployment reverted: " + receipt.getTransactionHash()
            );
        }

        if (receipt.getContractAddress() == null || receipt.getContractAddress().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Deployment receipt did not include a contract address"
            );
        }

        DeployedContract deployedContract = deployedContractRepository.save(
                new DeployedContract(
                        institutionId,
                        contractName,
                        receipt.getContractAddress()
                )
        );

if (contractName == ContractName.CONTRACT) {
    registerSettlementAsOperator(deployedContract.getAddress());
	registerBanksInSettlement(deployedContract.getAddress());
}

        return new DeployContractResponse(
                institution.getId(),
                institution.getInstitutionName(),
                deployedContract.getName(),
                deployedContract.getAddress(),
                receipt.getTransactionHash(),
                institution.getRpcEndpoint(),
                credentials.getAddress()
        );
    }
    catch (IOException e) {
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Besu RPC deployment failed: " + e.getMessage()
        );
    }
    finally {
        web3j.shutdown();
    }
}
private void registerBanksInSettlement(String settlementAddress) throws IOException {
    Institution centralBank = institutionRepository.findById(CBDC_INSTITUTION_ID)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Central bank not found"));

    Credentials credentials = walletKeyCipher.decryptCredentials(
            centralBank.getEncryptedPrivateKey()
    );

    Web3j web3j = Web3j.build(new HttpService(centralBank.getRpcEndpoint()));

    try {
        RawTransactionManager transactionManager = new RawTransactionManager(
                web3j,
                credentials,
                besuProperties.chainId()
        );

        List<DeployedContract> contracts = deployedContractRepository.findAll();

        for (DeployedContract contract : contracts) {
            if (contract.getName() != ContractName.DEPOSIT_TOKEN) {
                continue;
            }

            Institution bank = institutionRepository.findById(contract.getInstitutionId())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "Institution not found: " + contract.getInstitutionId()
                    ));

            callSetBank(
                    web3j,
                    transactionManager,
                    settlementAddress,
                    bank.getId(),
                    contract.getAddress(),
                    bank.getWalletAddress()
            );
        }
    }
    finally {
        web3j.shutdown();
    }
}

private TransactionReceipt callSetBank(
        Web3j web3j,
        RawTransactionManager transactionManager,
        String settlementAddress,
        Long institutionId,
        String tokenAddress,
        String reserveWallet
) throws IOException {
    Function function = new Function(
            "setBank",
            List.of(
                    new Uint256(BigInteger.valueOf(institutionId)),
                    new Address(tokenAddress),
                    new Address(reserveWallet)
            ),
            List.of()
    );

    EthGetTransactionCount nonceResponse = web3j.ethGetTransactionCount(
            transactionManager.getFromAddress(),
            DefaultBlockParameterName.PENDING
    ).send();

    if (nonceResponse.hasError()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, nonceResponse.getError().getMessage());
    }

    RawTransaction transaction = RawTransaction.createTransaction(
            nonceResponse.getTransactionCount(),
            PRIVATE_NETWORK_GAS_PRICE,
            BigInteger.valueOf(200_000),
            settlementAddress,
            BigInteger.ZERO,
            FunctionEncoder.encode(function)
    );

    EthSendTransaction sendResponse = transactionManager.signAndSend(transaction);

    if (sendResponse.hasError()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
    }

    TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

    if (!receipt.isStatusOK()) {
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "setBank reverted: " + receipt.getTransactionHash()
        );
    }

    return receipt;
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
                    "Timed out waiting for deployment receipt: " + e.getMessage()
            );
        }
    }

	private TransactionReceipt callSetOperator(
        Web3j web3j,
        RawTransactionManager transactionManager,
        String tokenAddress,
        String operatorAddress
) throws IOException {
    Function function = new Function(
            "setOperator",
            List.of(
                    new Address(operatorAddress),
                    new Bool(true)
            ),
            List.of()
    );

    EthGetTransactionCount nonceResponse = web3j.ethGetTransactionCount(
            transactionManager.getFromAddress(),
            DefaultBlockParameterName.PENDING
    ).send();

    if (nonceResponse.hasError()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, nonceResponse.getError().getMessage());
    }

    RawTransaction transaction = RawTransaction.createTransaction(
            nonceResponse.getTransactionCount(),
            PRIVATE_NETWORK_GAS_PRICE,
            BigInteger.valueOf(100_000),
            tokenAddress,
            BigInteger.ZERO,
            FunctionEncoder.encode(function)
    );

    EthSendTransaction sendResponse = transactionManager.signAndSend(transaction);

    if (sendResponse.hasError()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
    }

    TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());

    if (!receipt.isStatusOK()) {
        throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "setOperator reverted: " + receipt.getTransactionHash()
        );
    }

    return receipt;
}

private void registerSettlementAsOperator(
        String settlementAddress
) throws IOException {
    List<DeployedContract> contracts = deployedContractRepository.findAll();

    for (DeployedContract contract : contracts) {
        if (contract.getName() != ContractName.CBDC
                && contract.getName() != ContractName.DEPOSIT_TOKEN) {
            continue;
        }

        Institution ownerInstitution = institutionRepository.findById(contract.getInstitutionId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Institution not found: " + contract.getInstitutionId()
                ));

        Credentials ownerCredentials = walletKeyCipher.decryptCredentials(
                ownerInstitution.getEncryptedPrivateKey()
        );

        Web3j ownerWeb3j = Web3j.build(new HttpService(ownerInstitution.getRpcEndpoint()));

        try {
            RawTransactionManager ownerTransactionManager = new RawTransactionManager(
                    ownerWeb3j,
                    ownerCredentials,
                    besuProperties.chainId()
            );

            callSetOperator(
                    ownerWeb3j,
                    ownerTransactionManager,
                    contract.getAddress(),
                    settlementAddress
            );
        }
        finally {
            ownerWeb3j.shutdown();
        }
    }
}


    private static ContractName resolveContractName(
            Institution institution,
            DeployContractRequest request
    ) {
        if (request != null && request.name() != null) {
            return request.name();
        }

        if ("CB".equalsIgnoreCase(institution.getInstitutionCode())) {
            return ContractName.CBDC;
        }

        return ContractName.DEPOSIT_TOKEN;
    }

    private static void validateSignerAddress(
            Institution institution,
            Credentials credentials
    ) {
        if (!institution.getWalletAddress().equalsIgnoreCase(credentials.getAddress())) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Encrypted key does not match institution wallet address: "
                            + institution.getWalletAddress()
            );
        }
    }

    private static void validateInstitutionDeploymentInfo(Institution institution) {
        if (institution.getWalletAddress() == null || institution.getWalletAddress().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Institution wallet address not found: " + institution.getId()
            );
        }

        if (institution.getEncryptedPrivateKey() == null || institution.getEncryptedPrivateKey().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Institution encrypted private key not found: " + institution.getId()
            );
        }

        if (institution.getRpcEndpoint() == null || institution.getRpcEndpoint().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Institution RPC endpoint not found: " + institution.getId()
            );
        }
    }
private String resolveBytecode(
        ContractName contractName,
        Institution institution
) {
    return switch (contractName) {

        case CBDC ->
                tokenArtifactLoader.cbdcArtifact().bytecode();

        case DEPOSIT_TOKEN ->
                appendConstructorArgs(
                        tokenArtifactLoader.depositTokenArtifact().bytecode(),
                        List.of(
                                new Uint256(BigInteger.valueOf(institution.getId())),
                                new Utf8String(institution.getInstitutionName()),
                                new Utf8String("BANK" + institution.getId())
                        )
                );

        case CONTRACT ->
                appendConstructorArgs(
                        tokenArtifactLoader.settlementArtifact().bytecode(),
                        List.of(
                                new Address(
                                        resolveContractAddress(
                                                CBDC_INSTITUTION_ID,
                                                ContractName.CBDC
                                                )
                                )
                        )
                );
    };
}

private static String appendConstructorArgs(
        String bytecode,
        List<Type> constructorArgs
) {
    return bytecode + stripHexPrefix(
            FunctionEncoder.encodeConstructor(constructorArgs)
    );
}

private static String stripHexPrefix(String value) {
    if (value.startsWith("0x") || value.startsWith("0X")) {
        return value.substring(2);
    }

    return value;
}

private String resolveContractAddress(
        Long institutionId,
        ContractName contractName
) {
    return deployedContractRepository
            .findByInstitutionIdAndName(institutionId, contractName)
            .map(DeployedContract::getAddress)
            .orElseThrow(() -> new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "No " + contractName
                            + " contract deployed for institution "
                            + institutionId
            ));
}
}
