package com.example.server.dto;

public record WalletResponse(
		String address,
		String privateKey) {
}
