package com.example.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.server.domain.BankWallet;

public interface BankWalletRepository
        extends JpaRepository<BankWallet, Long> {

    Optional<BankWallet> findByWalletAddressIgnoreCase(
            String walletAddress
    );

    List<BankWallet> findAllByInstitutionId(
            Long institutionId
    );

    boolean existsByWalletAddressIgnoreCase(
            String walletAddress
    );
}