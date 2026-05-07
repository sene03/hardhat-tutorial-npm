package com.example.server.service;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.example.server.config.TokenProperties;
import com.example.server.dto.WalletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.WalletUtils;
import org.web3j.utils.Numeric;

@Component
public class WalletRegistry {

	private final Map<String, Credentials> wallets = new ConcurrentHashMap<>();

	public WalletRegistry(TokenProperties properties) {
		if (StringUtils.hasText(properties.signerPrivateKey())) {
			register(Credentials.create(normalizePrivateKey(properties.signerPrivateKey())));
		}
	}

	public WalletResponse createWallet() throws Exception {
		Credentials credentials = Credentials.create(Keys.createEcKeyPair());
		register(credentials);
		return new WalletResponse(credentials.getAddress(), withHexPrefix(credentials.getEcKeyPair().getPrivateKey()));
	}

	public Optional<Credentials> findByAddress(String address) {
		if (!WalletUtils.isValidAddress(address)) {
			return Optional.empty();
		}
		return Optional.ofNullable(wallets.get(normalizeAddress(address)));
	}

	private void register(Credentials credentials) {
		wallets.put(normalizeAddress(credentials.getAddress()), credentials);
	}

	private static String normalizeAddress(String address) {
		return address.toLowerCase();
	}

	private static String normalizePrivateKey(String privateKey) {
		return privateKey.startsWith("0x") || privateKey.startsWith("0X")
				? privateKey.substring(2)
				: privateKey;
	}

	private static String withHexPrefix(BigInteger value) {
		return Numeric.toHexStringWithPrefixZeroPadded(value, 64);
	}
}
