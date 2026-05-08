package com.example.server.repository;

import java.util.Optional;

import com.example.server.domain.BesuNode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BesuNodeRepository extends JpaRepository<BesuNode, Long> {

	Optional<BesuNode> findByInstitutionId(Long institutionId);
}
