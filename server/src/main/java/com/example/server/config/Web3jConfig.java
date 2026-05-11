package com.example.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@EnableConfigurationProperties({BesuProperties.class, WalletEncryptionProperties.class})
public class Web3jConfig {

	@Bean
	Web3j web3j(BesuProperties properties) {
		return Web3j.build(new HttpService(properties.rpcUrl()));
	}
}
