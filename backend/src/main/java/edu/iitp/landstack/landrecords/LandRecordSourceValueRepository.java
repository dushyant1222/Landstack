package edu.iitp.landstack.landrecords;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LandRecordSourceValueRepository extends JpaRepository<LandRecordSourceValue, UUID> {
    List<LandRecordSourceValue> findByLandRecordIdOrderByCreatedAtAsc(UUID landRecordId);
}
