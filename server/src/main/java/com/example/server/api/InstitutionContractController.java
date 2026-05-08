package com.example.server.api;

import com.example.server.dto.DeployContractRequest;
import com.example.server.dto.DeployContractResponse;
import com.example.server.service.InstitutionContractDeploymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/institutions")
public class InstitutionContractController {

	private final InstitutionContractDeploymentService deploymentService;

	public InstitutionContractController(InstitutionContractDeploymentService deploymentService) {
		this.deploymentService = deploymentService;
	}

	@PostMapping("/{institutionId}/contracts/deploy")
	public DeployContractResponse deploy(
			@PathVariable Long institutionId,
			@RequestBody(required = false) DeployContractRequest request) {
		return deploymentService.deploy(institutionId, request);
	}
}
