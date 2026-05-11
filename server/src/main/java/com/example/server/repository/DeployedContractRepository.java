package com.example.server.repository;

import java.util.Optional;

import com.example.server.domain.DeployedContract;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeployedContractRepository extends JpaRepository<DeployedContract, Long> {

	boolean existsByInstitutionId(Long institutionId);

	Optional<DeployedContract> findByInstitutionId(Long institutionId);
}
