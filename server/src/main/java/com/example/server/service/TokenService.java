package com.example.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import com.example.server.config.BesuProperties;
import com.example.server.config.TokenProperties;
import com.example.server.dto.BalanceResponse;
import com.example.server.dto.TransferRequest;
import com.example.server.dto.TransferResponse;
import com.example.server.dto.WalletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
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
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

@Service
public class TokenService {

	private static final BigInteger TRANSFER_GAS_LIMIT = BigInteger.valueOf(100_000);
	private static final int RECEIPT_POLLING_ATTEMPTS = 60;
	private static final long RECEIPT_POLLING_INTERVAL_MS = 1_000L;

	private final Web3j web3j;
	private final BesuProperties besuProperties;
	private final TokenProperties tokenProperties;
	private final WalletRegistry walletRegistry;

	public TokenService(
			Web3j web3j,
			BesuProperties besuProperties,
			TokenProperties tokenProperties,
			WalletRegistry walletRegistry) {
		this.web3j = web3j;
		this.besuProperties = besuProperties;
		this.tokenProperties = tokenProperties;
		this.walletRegistry = walletRegistry;
	}

	public BalanceResponse balanceOf(String address) {
		validateAddress(address, "address");
		Function function = new Function(
				"balanceOf",
				List.of(new Address(address)),
				List.of(new TypeReference<Uint256>() {
				}));

		EthCall response = ethCall(function);
		if (response.hasError()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
		}

		List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
		if (decoded.isEmpty()) {
			throw new ApiException(
					HttpStatus.BAD_GATEWAY,
					"No ERC-20 response from TOKEN_CONTRACT_ADDRESS. Check that the address is deployed on this Besu network.");
		}

		BigInteger balance = (BigInteger) decoded.getFirst().getValue();
		return new BalanceResponse(address, balance);
	}

	public WalletResponse createWallet() {
		try {
			return walletRegistry.createWallet();
		} catch (Exception e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create wallet: " + e.getMessage());
		}
	}

	public TransferResponse transfer(TransferRequest request) {
		validateTransferRequest(request);

		BalanceResponse balance = balanceOf(request.from());
		if (balance.balance().compareTo(request.amount()) < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Insufficient token balance");
		}

		Credentials credentials = walletRegistry.findByAddress(request.from())
				.orElseThrow(() -> new ApiException(
						HttpStatus.BAD_REQUEST,
						"No private key registered for from address. Use the configured signer account or create a wallet first."));

		Function function = new Function(
				"transfer",
				List.of(new Address(request.to()), new Uint256(request.amount())),
				List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() {
				}));

		String transactionHash = sendSignedTransaction(credentials, FunctionEncoder.encode(function));
		TransactionReceipt receipt = waitForReceipt(transactionHash);
		if (!receipt.isStatusOK()) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Transaction reverted: " + transactionHash);
		}

		return new TransferResponse(transactionHash, request.from(), request.to(), receipt.getStatus());
	}

	private EthCall ethCall(Function function) {
		requireContractAddress();
		try {
			String data = FunctionEncoder.encode(function);
			Transaction transaction = Transaction.createEthCallTransaction(null, tokenProperties.contractAddress(),
					data);
			return web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Besu RPC call failed: " + e.getMessage());
		}
	}

	private String sendSignedTransaction(Credentials credentials, String data) {
		requireContractAddress();
		try {
			EthGasPrice gasPriceResponse = web3j.ethGasPrice().send();
			if (gasPriceResponse.hasError()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, gasPriceResponse.getError().getMessage());
			}

			RawTransactionManager transactionManager = new RawTransactionManager(
					web3j,
					credentials,
					besuProperties.chainId());
			EthSendTransaction response = transactionManager.sendTransaction(
					gasPriceResponse.getGasPrice(),
					TRANSFER_GAS_LIMIT,
					tokenProperties.contractAddress(),
					data,
					BigInteger.ZERO);

			if (response.hasError()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, response.getError().getMessage());
			}
			return response.getTransactionHash();
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "Besu RPC transaction failed: " + e.getMessage());
		}
	}

	private TransactionReceipt waitForReceipt(String transactionHash) {
		try {
			PollingTransactionReceiptProcessor processor = new PollingTransactionReceiptProcessor(
					web3j,
					RECEIPT_POLLING_INTERVAL_MS,
					RECEIPT_POLLING_ATTEMPTS);
			return processor.waitForTransactionReceipt(transactionHash);
		} catch (Exception e) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,
					"Timed out waiting for transaction receipt: " + e.getMessage());
		}
	}

	private void validateTransferRequest(TransferRequest request) {
		if (request == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		validateAddress(request.from(), "from");
		validateAddress(request.to(), "to");
		if (request.amount() == null || request.amount().compareTo(BigInteger.ZERO) <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "amount must be a positive integer in token base units");
		}
	}

	private static void validateAddress(String address, String fieldName) {
		if (!StringUtils.hasText(address) || !WalletUtils.isValidAddress(address)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be a valid Ethereum address");
		}
	}

	private void requireContractAddress() {
		if (!StringUtils.hasText(tokenProperties.contractAddress())
				|| !WalletUtils.isValidAddress(tokenProperties.contractAddress())) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"TOKEN_CONTRACT_ADDRESS must be configured with a valid address");
		}
	}
}
