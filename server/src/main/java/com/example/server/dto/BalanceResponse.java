package com.example.server.dto;

import java.math.BigInteger;

public record BalanceResponse(
        String walletAddress,
        BigInteger balance
) {
}