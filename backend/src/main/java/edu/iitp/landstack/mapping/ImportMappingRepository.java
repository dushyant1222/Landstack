package edu.iitp.landstack.mapping;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportMappingRepository extends JpaRepository<ImportMapping, UUID> {}
