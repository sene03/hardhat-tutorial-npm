package com.example.server.repository;

import java.util.Optional;

import com.example.server.domain.UserWallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {

	Optional<UserWallet> findByAddressIgnoreCase(String address);
}
