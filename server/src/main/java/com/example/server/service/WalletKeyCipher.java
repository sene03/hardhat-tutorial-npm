package com.example.server.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.example.server.config.WalletEncryptionProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.crypto.Credentials;

@Component
public class WalletKeyCipher {

	private static final String FORMAT_PREFIX = "v1";
	private static final int GCM_TAG_BITS = 128;
	private static final int GCM_NONCE_BYTES = 12;
	private static final int AES_256_KEY_BYTES = 32;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final SecretKeySpec keySpec;

	public WalletKeyCipher(WalletEncryptionProperties properties) {
		if (!StringUtils.hasText(properties.aesKeyBase64())) {
			throw new IllegalStateException("WALLET_AES_KEY_BASE64 must be configured");
		}
		byte[] key = Base64.getDecoder().decode(properties.aesKeyBase64());
		if (key.length != AES_256_KEY_BYTES) {
			throw new IllegalStateException("WALLET_AES_KEY_BASE64 must decode to 32 bytes");
		}
		this.keySpec = new SecretKeySpec(key, "AES");
	}

	public Credentials decryptCredentials(String encryptedKey) {
		return Credentials.create(decryptPrivateKey(encryptedKey));
	}

	public String encryptPrivateKey(String privateKey) {
		try {
			byte[] nonce = new byte[GCM_NONCE_BYTES];
			SECURE_RANDOM.nextBytes(nonce);

			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, nonce));
			byte[] ciphertextWithTag = cipher.doFinal(privateKey.getBytes(StandardCharsets.UTF_8));

			return FORMAT_PREFIX + ":"
					+ Base64.getEncoder().encodeToString(nonce) + ":"
					+ Base64.getEncoder().encodeToString(ciphertextWithTag);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to encrypt wallet private key", e);
		}
	}

	public String decryptPrivateKey(String encryptedKey) {
		String[] parts = encryptedKey.split(":", 3);
		if (parts.length != 3 || !FORMAT_PREFIX.equals(parts[0])) {
			throw new IllegalArgumentException("encrypted_key must use v1:<nonce>:<ciphertextWithTag> format");
		}

		try {
			byte[] nonce = Base64.getDecoder().decode(parts[1]);
			byte[] ciphertextWithTag = Base64.getDecoder().decode(parts[2]);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, nonce));
			return new String(cipher.doFinal(ciphertextWithTag), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalArgumentException("Failed to decrypt wallet private key", e);
		}
	}
}
