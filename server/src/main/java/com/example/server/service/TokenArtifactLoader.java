package com.example.server.service;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TokenArtifactLoader {

	private static final String TOKEN_ARTIFACT_PATH = "contracts/Token.json";

	private final TokenArtifact tokenArtifact;

	public TokenArtifactLoader() throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode artifact = objectMapper.readTree(new ClassPathResource(TOKEN_ARTIFACT_PATH).getInputStream());
		String bytecode = artifact.path("bytecode").asText();
		if (!StringUtils.hasText(bytecode) || "0x".equals(bytecode)) {
			throw new IllegalStateException(TOKEN_ARTIFACT_PATH + " must contain deployable bytecode");
		}
		this.tokenArtifact = new TokenArtifact(bytecode);
	}

	public TokenArtifact tokenArtifact() {
		return tokenArtifact;
	}
}
