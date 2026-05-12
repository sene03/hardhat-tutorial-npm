package com.example.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        BesuProperties.class,
        WalletEncryptionProperties.class
})
public class Web3jConfig {
}