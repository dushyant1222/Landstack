package edu.iitp.landstack.quality;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QualityIssueRepository extends JpaRepository<QualityIssue, UUID>, JpaSpecificationExecutor<QualityIssue> {
    boolean existsByDedupKey(String dedupKey);

    List<QualityIssue> findByLandRecordIdOrderByCreatedAtAsc(UUID landRecordId);

    long countByImportJobId(UUID importJobId);

    @Query("select q.landRecordId, count(q) from QualityIssue q where q.landRecordId in :ids " +
           "and q.status = edu.iitp.landstack.quality.IssueStatus.OPEN group by q.landRecordId")
    List<Object[]> openCountsByRecord(@Param("ids") java.util.Collection<UUID> ids);

    @Modifying
    @Query("delete from QualityIssue q where q.importJobId = :jobId or q.landRecordId in " +
           "(select r.id from LandRecord r where r.importJobId = :jobId)")
    int deleteForImport(@Param("jobId") UUID jobId);
}
