package com.example.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wallet")
public record WalletEncryptionProperties(
		String aesKeyBase64) {
}
