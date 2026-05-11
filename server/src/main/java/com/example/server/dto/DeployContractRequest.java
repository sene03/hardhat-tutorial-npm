package com.example.server.dto;

import com.example.server.domain.ContractName;

public record DeployContractRequest(
		ContractName name) {
}
