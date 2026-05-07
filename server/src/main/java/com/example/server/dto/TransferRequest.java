package com.example.server.dto;

import java.math.BigInteger;

public record TransferRequest(
		String from,
		String to,
		BigInteger amount) {
}
