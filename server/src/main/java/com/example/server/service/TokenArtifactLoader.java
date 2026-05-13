package com.example.server.service;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TokenArtifactLoader {

    private static final String CBDC_ARTIFACT_PATH =
            "contracts/CBDCToken.json";

private static final String DEPOSIT_TOKEN_ARTIFACT_PATH =
        "contracts/DepositToken.json";

    private static final String SETTLEMENT_ARTIFACT_PATH =
            "contracts/Settlement.json";

    private final TokenArtifact cbdcArtifact;
    private final TokenArtifact depositTokenArtifact;
    private final TokenArtifact settlementArtifact;

    public TokenArtifactLoader() throws IOException {
        this.cbdcArtifact = loadArtifact(CBDC_ARTIFACT_PATH);
        this.depositTokenArtifact = loadArtifact(DEPOSIT_TOKEN_ARTIFACT_PATH);
        this.settlementArtifact = loadArtifact(SETTLEMENT_ARTIFACT_PATH);
    }

    public TokenArtifact cbdcArtifact() {
        return cbdcArtifact;
    }

    public TokenArtifact depositTokenArtifact() {
        return depositTokenArtifact;
    }

    public TokenArtifact settlementArtifact() {
        return settlementArtifact;
    }

    private TokenArtifact loadArtifact(String path) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode artifact = objectMapper.readTree(
                new ClassPathResource(path).getInputStream()
        );

        String bytecode = artifact.path("bytecode").asText();

        if (!StringUtils.hasText(bytecode) || "0x".equals(bytecode)) {
            throw new IllegalStateException(
                    path + " must contain deployable bytecode"
            );
        }

        return new TokenArtifact(bytecode);
    }
}