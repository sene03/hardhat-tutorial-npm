package com.example.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "besu_node")
public class BesuNode {

	@Id
	private Long id;

	@Column(name = "institution_id", nullable = false)
	private Long institutionId;

	@Column(name = "enode_url", nullable = false, length = 512)
	private String enodeUrl;

	@Column(name = "rpc_endpoint", nullable = false)
	private String rpcEndpoint;

	@Column(name = "is_validator", nullable = false)
	private boolean validator;

	protected BesuNode() {
	}

	public Long getId() {
		return id;
	}

	public Long getInstitutionId() {
		return institutionId;
	}

	public String getEnodeUrl() {
		return enodeUrl;
	}

	public String getRpcEndpoint() {
		return rpcEndpoint;
	}

	public boolean isValidator() {
		return validator;
	}
}
