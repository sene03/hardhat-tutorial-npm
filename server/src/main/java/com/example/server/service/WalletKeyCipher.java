package com.example.server.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
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
	private static final int AES_256_KEY_BYTES = 32;

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
