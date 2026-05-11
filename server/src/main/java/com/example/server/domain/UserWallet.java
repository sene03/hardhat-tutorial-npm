package com.example.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_wallet")
public class UserWallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 42)
	private String address;

	@Column(name = "encrypted_key", nullable = false, columnDefinition = "TEXT")
	private String encryptedKey;

	protected UserWallet() {
	}

	public UserWallet(String address, String encryptedKey) {
		this.address = address;
		this.encryptedKey = encryptedKey;
	}

	public Long getId() {
		return id;
	}

	public String getAddress() {
		return address;
	}

	public String getEncryptedKey() {
		return encryptedKey;
	}
}
