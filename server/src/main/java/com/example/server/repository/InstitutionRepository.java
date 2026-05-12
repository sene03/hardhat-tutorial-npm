package com.example.server.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.server.domain.Institution;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {
    Optional<Institution> findByWalletAddressIgnoreCase(String walletAddress);
}
