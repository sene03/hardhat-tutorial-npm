package com.example.server.repository;

import java.util.Optional;

import com.example.server.domain.InstitutionWallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionWalletRepository extends JpaRepository<InstitutionWallet, Long> {

	Optional<InstitutionWallet> findByInstitutionId(Long institutionId);
}
