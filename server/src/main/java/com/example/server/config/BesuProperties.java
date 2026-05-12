package com.example.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "besu")
public record BesuProperties(
        long chainId
) {
}