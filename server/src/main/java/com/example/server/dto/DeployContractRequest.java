package com.example.server.dto;

import com.example.server.domain.ContractName;
import com.fasterxml.jackson.annotation.JsonAlias;

public record DeployContractRequest(
		@JsonAlias("contractName")
		ContractName name) {
}
