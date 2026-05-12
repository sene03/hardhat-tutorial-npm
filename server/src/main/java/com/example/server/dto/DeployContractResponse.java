package com.example.server.dto;

import com.example.server.domain.ContractName;

public record DeployContractResponse(
        Long institutionId,
        String institutionName,
        ContractName contractName,
        String contractAddress,
        String transactionHash,
        String rpcEndpoint,
        String signerAddress
) {
}