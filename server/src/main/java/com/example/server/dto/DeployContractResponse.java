package com.example.server.dto;

import com.example.server.domain.ContractName;
import com.example.server.domain.InstitutionType;

public record DeployContractResponse(
		Long institutionId,
		String institutionName,
		InstitutionType institutionType,
		ContractName contractName,
		String contractAddress,
		String transactionHash,
		String rpcEndpoint,
		String signerAddress) {
}
