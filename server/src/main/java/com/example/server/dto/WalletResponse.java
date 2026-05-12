package com.example.server.dto;

public record WalletResponse(
        String walletAddress,
        String encryptedPrivateKey
) {
}