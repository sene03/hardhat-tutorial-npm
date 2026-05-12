package com.example.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.server.domain.ContractName;
import com.example.server.domain.DeployedContract;

public interface DeployedContractRepository
        extends JpaRepository<DeployedContract, Long> {

    boolean existsByInstitutionIdAndName(
            Long institutionId,
            ContractName name
    );

    Optional<DeployedContract> findByInstitutionIdAndName(
            Long institutionId,
            ContractName name
    );

    List<DeployedContract> findAllByInstitutionId(Long institutionId);
}