package com.example.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "institution_wallet")
public class InstitutionWallet {

	@Id
	private Long id;

	@Column(name = "institution_id", nullable = false)
	private Long institutionId;

	@Column(nullable = false, length = 42)
	private String address;

	@Column(name = "encrypted_key", nullable = false, columnDefinition = "TEXT")
	private String encryptedKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private WalletRole role;

	protected InstitutionWallet() {
	}

	public Long getId() {
		return id;
	}

	public Long getInstitutionId() {
		return institutionId;
	}

	public String getAddress() {
		return address;
	}

	public String getEncryptedKey() {
		return encryptedKey;
	}

	public WalletRole getRole() {
		return role;
	}
}
