package com.example.server.dto;

public record TransferResponse(
		String transactionHash,
		String from,
		String to,
		String status) {
}
