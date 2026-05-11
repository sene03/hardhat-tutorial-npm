package com.example.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import com.example.server.config.BesuProperties;
import com.example.server.domain.BesuNode;
import com.example.server.domain.DeployedContract;
import com.example.server.domain.InstitutionWallet;
import com.example.server.domain.UserWallet;
import com.example.server.dto.BalanceResponse;
import com.example.server.dto.TransferRequest;
import com.example.server.dto.TransferResponse;
import com.example.server.dto.WalletResponse;
import com.example.server.repository.BesuNodeRepository;
import com.example.server.repository.DeployedContractRepository;
import com.example.server.repository.InstitutionWalletRepository;
import com.example.server.repository.UserWalletRepository;
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
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

@Service
public class TokenService {

	private static final BigInteger TRANSFER_GAS_LIMIT = BigInteger.valueOf(100_000);
	private static final BigInteger PRIVATE_NETWORK_GAS_PRICE = BigInteger.ZERO;
	private static final int RECEIPT_POLLING_ATTEMPTS = 60;
	private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;
	private static final Long CBDC_INSTITUTION_ID = 1L;

	private final BesuProperties besuProperties;
	private final DeployedContractRepository deployedContractRepository;
	private final InstitutionWalletRepository walletRepository;
	private final UserWalletRepository userWalletRepository;
	private final WalletKeyCipher walletKeyCipher;
	private final BesuNodeRepository besuNodeRepository;

	public TokenService(
			BesuProperties besuProperties,
			DeployedContractRepository deployedContractRepository,
			InstitutionWalletRepository walletRepository,
			UserWalletRepository userWalletRepository,
			WalletKeyCipher walletKeyCipher,
			BesuNodeRepository besuNodeRepository) {
		this.besuProperties = besuProperties;
		this.deployedContractRepository = deployedContractRepository;
		this.walletRepository = walletRepository;
		this.userWalletRepository = userWalletRepository;
		this.walletKeyCipher = walletKeyCipher;
		this.besuNodeRepository = besuNodeRepository;
	}

	public BalanceResponse balanceOf(String address) {
		validateAddress(address, "address");
		Long contractInstitutionId = walletRepository.findByAddressIgnoreCase(address)
				.map(InstitutionWallet::getInstitutionId)
				.orElseGet(() -> resolveUserWalletContractInstitutionId(address));
		String contractAddress = resolveContractAddress(contractInstitutionId);
		BesuNode besuNode = resolveBesuNode(contractInstitutionId);

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
			Function function = new Function(
					"transfer",
					List.of(new Address(request.to()), new Uint256(request.amount())),
					List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {}));

			RawTransactionManager transactionManager = new RawTransactionManager(
					web3j, credentials, besuProperties.chainId());
			EthSendTransaction sendResponse = transactionManager.sendTransaction(
					PRIVATE_NETWORK_GAS_PRICE,
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
			Credentials credentials = Credentials.create(Keys.createEcKeyPair());
			String privateKey = Numeric.toHexStringWithPrefixZeroPadded(
					credentials.getEcKeyPair().getPrivateKey(),
					64);
			userWalletRepository.save(new UserWallet(
					credentials.getAddress(),
					walletKeyCipher.encryptPrivateKey(privateKey)));
			return new WalletResponse(credentials.getAddress(), privateKey);
		} catch (Exception e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create wallet: " + e.getMessage());
		}
	}

	private Long resolveUserWalletContractInstitutionId(String address) {
		return userWalletRepository.findByAddressIgnoreCase(address)
				.map(wallet -> CBDC_INSTITUTION_ID)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
						"No wallet found for address: " + address));
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
