package com.example.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import com.example.server.config.BesuProperties;
import com.example.server.domain.BesuNode;
import com.example.server.domain.DeployedContract;
import com.example.server.domain.InstitutionWallet;
import com.example.server.dto.BalanceResponse;
import com.example.server.dto.TransferRequest;
import com.example.server.dto.TransferResponse;
import com.example.server.dto.WalletResponse;
import com.example.server.repository.BesuNodeRepository;
import com.example.server.repository.DeployedContractRepository;
import com.example.server.repository.InstitutionWalletRepository;
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
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

@Service
public class TokenService {

	private static final BigInteger TRANSFER_GAS_LIMIT = BigInteger.valueOf(100_000);
	private static final int RECEIPT_POLLING_ATTEMPTS = 60;
	private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;

	private final BesuProperties besuProperties;
	private final DeployedContractRepository deployedContractRepository;
	private final InstitutionWalletRepository walletRepository;
	private final WalletKeyCipher walletKeyCipher;
	private final BesuNodeRepository besuNodeRepository;
	private final WalletRegistry walletRegistry;

	public TokenService(
			BesuProperties besuProperties,
			DeployedContractRepository deployedContractRepository,
			InstitutionWalletRepository walletRepository,
			WalletKeyCipher walletKeyCipher,
			BesuNodeRepository besuNodeRepository,
			WalletRegistry walletRegistry) {
		this.besuProperties = besuProperties;
		this.deployedContractRepository = deployedContractRepository;
		this.walletRepository = walletRepository;
		this.walletKeyCipher = walletKeyCipher;
		this.besuNodeRepository = besuNodeRepository;
		this.walletRegistry = walletRegistry;
	}

	public BalanceResponse balanceOf(String address) {
		validateAddress(address, "address");
		InstitutionWallet wallet = walletRepository.findByAddressIgnoreCase(address)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"No institution wallet found for address: " + address));
		String contractAddress = resolveContractAddress(wallet.getInstitutionId());
		BesuNode besuNode = resolveBesuNode(wallet.getInstitutionId());

		Web3j web3j = Web3j.build(new HttpService(besuNode.getRpcEndpoint()));
		try {
			Function function = new Function(
					"balanceOf",
					List.of(new Address(address)),
					List.of(new TypeReference<Uint256>() {}));

			String data = FunctionEncoder.encode(function);
			Transaction transaction = Transaction.createEthCallTransaction(null, contractAddress, data);
			EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();

			if (response.hasError()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
			}

			var decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
			if (decoded.isEmpty()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY,
						"No ERC-20 response from contract. Check the contract is deployed on this Besu network.");
			}

			BigInteger balance = (BigInteger) decoded.getFirst().getValue();
			return new BalanceResponse(address, balance);
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Besu RPC call failed: " + e.getMessage());
		} finally {
			web3j.shutdown();
		}
	}

	public TransferResponse transfer(TransferRequest request) {
		if (request == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		validateAddress(request.from(), "from");
		validateAddress(request.to(), "to");
		if (request.amount() == null || request.amount().compareTo(BigInteger.ZERO) <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount must be a positive integer in token base units");
		}

		InstitutionWallet wallet = walletRepository.findByAddressIgnoreCase(request.from())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"No institution wallet found for address: " + request.from()));
		Long institutionId = wallet.getInstitutionId();
		Credentials credentials = walletKeyCipher.decryptCredentials(wallet.getEncryptedKey());
		String contractAddress = resolveContractAddress(institutionId);
		BesuNode besuNode = resolveBesuNode(institutionId);

		BalanceResponse balance = balanceOf(request.from());
		if (balance.balance().compareTo(request.amount()) < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient token balance");
		}

		Web3j web3j = Web3j.build(new HttpService(besuNode.getRpcEndpoint()));
		try {
			EthGasPrice gasPriceResponse = web3j.ethGasPrice().send();
			if (gasPriceResponse.hasError()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, gasPriceResponse.getError().getMessage());
			}

			Function function = new Function(
					"transfer",
					List.of(new Address(request.to()), new Uint256(request.amount())),
					List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {}));

			RawTransactionManager transactionManager = new RawTransactionManager(
					web3j, credentials, besuProperties.chainId());
			EthSendTransaction sendResponse = transactionManager.sendTransaction(
					gasPriceResponse.getGasPrice(),
					TRANSFER_GAS_LIMIT,
					contractAddress,
					FunctionEncoder.encode(function),
					BigInteger.ZERO);

			if (sendResponse.hasError()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, sendResponse.getError().getMessage());
			}

			TransactionReceipt receipt = waitForReceipt(web3j, sendResponse.getTransactionHash());
			if (!receipt.isStatusOK()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY,
						"Transaction reverted: " + sendResponse.getTransactionHash());
			}

			return new TransferResponse(
					sendResponse.getTransactionHash(),
					credentials.getAddress(),
					request.to(),
					receipt.getStatus());
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Besu RPC transaction failed: " + e.getMessage());
		} finally {
			web3j.shutdown();
		}
	}

	public WalletResponse createWallet() {
		try {
			return walletRegistry.createWallet();
		} catch (Exception e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create wallet: " + e.getMessage());
		}
	}

	private String resolveContractAddress(Long institutionId) {
		return deployedContractRepository.findByInstitutionId(institutionId)
				.map(DeployedContract::getAddress)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
						"No contract deployed for institution " + institutionId + ". Call deploy first."));
	}

	private BesuNode resolveBesuNode(Long institutionId) {
		return besuNodeRepository.findByInstitutionId(institutionId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
						"Besu node not found for institution " + institutionId));
	}

	private TransactionReceipt waitForReceipt(Web3j web3j, String transactionHash) {
		try {
			PollingTransactionReceiptProcessor processor = new PollingTransactionReceiptProcessor(
					web3j, RECEIPT_POLLING_INTERVAL_MS, RECEIPT_POLLING_ATTEMPTS);
			return processor.waitForTransactionReceipt(transactionHash);
		} catch (Exception e) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,
					"Timed out waiting for transaction receipt: " + e.getMessage());
		}
	}

	private static void validateAddress(String address, String fieldName) {
		if (!StringUtils.hasText(address) || !WalletUtils.isValidAddress(address)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid Ethereum address");
		}
	}
}
