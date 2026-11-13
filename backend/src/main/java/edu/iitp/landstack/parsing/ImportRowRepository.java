package edu.iitp.landstack.parsing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ImportRowRepository extends JpaRepository<ImportRow, UUID> {
    List<ImportRow> findByImportJobIdAndAttemptIdOrderByRowNumberAsc(UUID jobId, UUID attemptId);

    Page<ImportRow> findByImportJobIdAndAttemptId(UUID jobId, UUID attemptId, Pageable pageable);

    Page<ImportRow> findByImportJobIdAndAttemptIdAndRowStateIn(UUID jobId, UUID attemptId,
                                                              Collection<ImportRow.State> states, Pageable pageable);

    long countByImportJobIdAndAttemptIdAndRowState(UUID jobId, UUID attemptId, ImportRow.State state);

    @Modifying
    @Query("delete from ImportRow r where r.importJobId = :jobId")
    int deleteByImportJobId(@Param("jobId") UUID jobId);

    @Modifying
    @Query("delete from ImportRow r where r.importJobId = :jobId and r.attemptId <> :keep")
    int deleteSuperseded(@Param("jobId") UUID jobId, @Param("keep") UUID keepAttemptId);
}
