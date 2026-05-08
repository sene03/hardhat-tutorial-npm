package com.example.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contract")
public class DeployedContract {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "institution_id", nullable = false)
	private Long institutionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ContractName name;

	@Column(nullable = false, length = 42)
	private String address;

	protected DeployedContract() {
	}

	public DeployedContract(Long institutionId, ContractName name, String address) {
		this.institutionId = institutionId;
		this.name = name;
		this.address = address;
	}

	public Long getId() {
		return id;
	}

	public Long getInstitutionId() {
		return institutionId;
	}

	public ContractName getName() {
		return name;
	}

	public String getAddress() {
		return address;
	}
}
