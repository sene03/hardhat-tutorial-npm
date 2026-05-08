package com.example.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "institution")
public class Institution {

	@Id
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private InstitutionType type;

	protected Institution() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public InstitutionType getType() {
		return type;
	}
}
