package edu.iitp.landstack.imports;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID>, JpaSpecificationExecutor<ImportJob> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ImportJob j where j.id = :id")
    Optional<ImportJob> findByIdForUpdate(@Param("id") UUID id);

    Optional<ImportJob> findFirstBySourceFileIdInAndIdNotOrderByCreatedAtAsc(Collection<UUID> sourceFileIds, UUID excludeId);

    Optional<ImportJob> findByConfirmationKey(UUID key);

    List<ImportJob> findByStatusInAndUpdatedAtBefore(Collection<ImportStatus> statuses, Instant before);

    long countByStatusIn(Collection<ImportStatus> statuses);

    long countBySourceFileId(UUID sourceFileId);
}
