package com.example.server.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.server.domain.BankWallet;

public interface UserWalletRepository extends JpaRepository<BankWallet, Long> {

	Optional<BankWallet> findByAddressIgnoreCase(String address);
}
