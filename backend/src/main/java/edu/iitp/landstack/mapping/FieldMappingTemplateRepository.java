package edu.iitp.landstack.mapping;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FieldMappingTemplateRepository extends JpaRepository<FieldMappingTemplate, UUID> {
    List<FieldMappingTemplate> findByActiveTrueOrderByNameAscVersionDesc();

    @Query("select coalesce(max(t.version), 0) from FieldMappingTemplate t where lower(t.name) = lower(?1)")
    int maxVersion(String name);
}
