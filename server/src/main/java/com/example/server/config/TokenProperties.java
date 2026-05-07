package com.example.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "token")
public record TokenProperties(
		String contractAddress,
		String signerPrivateKey) {
}
