package com.example.server.service;

import java.io.IOException;
import java.math.BigInteger;

import com.example.server.config.BesuProperties;
import com.example.server.domain.BesuNode;
import com.example.server.domain.ContractName;
import com.example.server.domain.DeployedContract;
import com.example.server.domain.Institution;
import com.example.server.domain.InstitutionType;
import com.example.server.domain.InstitutionWallet;
import com.example.server.dto.DeployContractRequest;
import com.example.server.dto.DeployContractResponse;
import com.example.server.repository.BesuNodeRepository;
import com.example.server.repository.DeployedContractRepository;
import com.example.server.repository.InstitutionRepository;
import com.example.server.repository.InstitutionWalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

@Service
public class InstitutionContractDeploymentService {

	private static final BigInteger DEPLOY_GAS_LIMIT = BigInteger.valueOf(4_000_000);
	private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
	private static final int RECEIPT_POLLING_ATTEMPTS = 60;
	private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;

	private final BesuProperties besuProperties;
	private final TokenArtifactLoader tokenArtifactLoader;
	private final WalletKeyCipher walletKeyCipher;
	private final InstitutionRepository institutionRepository;
	private final InstitutionWalletRepository walletRepository;
	private final BesuNodeRepository besuNodeRepository;
	private final DeployedContractRepository deployedContractRepository;

	public InstitutionContractDeploymentService(
			BesuProperties besuProperties,
			TokenArtifactLoader tokenArtifactLoader,
			WalletKeyCipher walletKeyCipher,
			InstitutionRepository institutionRepository,
			InstitutionWalletRepository walletRepository,
			BesuNodeRepository besuNodeRepository,
			DeployedContractRepository deployedContractRepository) {
		this.besuProperties = besuProperties;
		this.tokenArtifactLoader = tokenArtifactLoader;
		this.walletKeyCipher = walletKeyCipher;
		this.institutionRepository = institutionRepository;
		this.walletRepository = walletRepository;
		this.besuNodeRepository = besuNodeRepository;
		this.deployedContractRepository = deployedContractRepository;
	}

	public DeployContractResponse deploy(Long institutionId, DeployContractRequest request) {
		Institution institution = institutionRepository.findById(institutionId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Institution not found: " + institutionId));
		InstitutionWallet wallet = walletRepository.findByInstitutionId(institutionId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Institution wallet not found: " + institutionId));
		BesuNode besuNode = besuNodeRepository.findByInstitutionId(institutionId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Besu node not found: " + institutionId));

		deployedContractRepository.findByInstitutionId(institutionId)
				.ifPresent(existing -> {
					throw new ApiException(
							HttpStatus.CONFLICT,
							"Contract already deployed for institution " + institutionId + ": " + existing.getAddress());
				});

		ContractName contractName = resolveContractName(institution, request);
		Credentials credentials = walletKeyCipher.decryptCredentials(wallet.getEncryptedKey());
		validateSignerAddress(wallet, credentials);

		Web3j web3j = Web3j.build(new HttpService(besuNode.getRpcEndpoint()));
		try {
			RawTransactionManager transactionManager = new RawTransactionManager(
					web3j,
					credentials,
					besuProperties.chainId());
			EthGetTransactionCount nonceResponse = web3j.ethGetTransactionCount(
					credentials.getAddress(),
					DefaultBlockParameterName.PENDING).send();
			if (nonceResponse.hasError()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, nonceResponse.getError().getMessage());
			}
			RawTransaction deployTransaction = RawTransaction.createContractTransaction(
					nonceResponse.getTransactionCount(),
					PRIVATE_NETWORK_GAS_PRICE,
					DEPLOY_GAS_LIMIT,
					BigInteger.ZERO,
					tokenArtifactLoader.tokenArtifact().bytecode());
			EthSendTransaction sendResponse = transactionManager.signAndSend(deployTransaction);

			if (sendResponse.hasError()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
			}

			TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());
			if (!receipt.isStatusOK()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "Contract deployment reverted: " + receipt.getTransactionHash());
			}
			if (receipt.getContractAddress() == null || receipt.getContractAddress().isBlank()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "Deployment receipt did not include a contract address");
			}

			DeployedContract deployedContract = deployedContractRepository.save(
					new DeployedContract(institutionId, contractName, receipt.getContractAddress()));

			return new DeployContractResponse(
					institution.getId(),
					institution.getName(),
					institution.getType(),
					deployedContract.getName(),
					deployedContract.getAddress(),
					receipt.getTransactionHash(),
					besuNode.getRpcEndpoint(),
					credentials.getAddress());
		}
		catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Besu RPC deployment failed: " + e.getMessage());
		}
		finally {
			web3j.shutdown();
		}
	}

	private TransactionReceipt waitForReceipt(Web3j web3j, String transactionHash) {
		try {
			PollingTransactionReceiptProcessor processor = new PollingTransactionReceiptProcessor(
					web3j,
					RECEIPT_POLLING_INTERVAL_MS,
					RECEIPT_POLLING_ATTEMPTS);
			return processor.waitForTransactionReceipt(transactionHash);
		}
		catch (Exception e) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Timed out waiting for deployment receipt: " + e.getMessage());
		}
	}

	private static ContractName resolveContractName(Institution institution, DeployContractRequest request) {
		if (request != null && request.name() != null) {
			return request.name();
		}
		if (institution.getType() == InstitutionType.CENTRAL_BANK) {
			return ContractName.CBDC;
		}
		return ContractName.DEPOSIT_TOKEN;
	}

	private static void validateSignerAddress(InstitutionWallet wallet, Credentials credentials) {
		if (!wallet.getAddress().equalsIgnoreCase(credentials.getAddress())) {
			throw new ApiException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Encrypted key does not match institution wallet address: " + wallet.getAddress());
		}
	}
}
