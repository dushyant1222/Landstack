package edu.iitp.landstack.imports;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.files.FileStorageService;
import edu.iitp.landstack.files.SourceFile;
import edu.iitp.landstack.files.SourceFileRepository;
import edu.iitp.landstack.landrecords.LandRecordRepository;
import edu.iitp.landstack.mapping.ImportMappingRepository;
import edu.iitp.landstack.parsing.ImportRowRepository;
import edu.iitp.landstack.quality.QualityIssueRepository;
import edu.iitp.landstack.security.Perms;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

/**
 * Administrator removal of a demonstration import (retention policy): deletes its records, their
 * lineage and quality issues, its staging rows and — if no other job uses it — the stored file bytes.
 * The import job row is kept with status REMOVED and the audit trail is never deleted.
 */
@Service
public class ImportRemovalService {
    private final ImportJobRepository jobs;
    private final LandRecordRepository records;
    private final QualityIssueRepository issues;
    private final ImportRowRepository rows;
    private final ImportMappingRepository mappings;
    private final SourceFileRepository files;
    private final FileStorageService storage;
    private final AuditService audit;

    public ImportRemovalService(ImportJobRepository jobs, LandRecordRepository records, QualityIssueRepository issues,
                                ImportRowRepository rows, ImportMappingRepository mappings, SourceFileRepository files,
                                FileStorageService storage, AuditService audit) {
        this.jobs = jobs;
        this.records = records;
        this.issues = issues;
        this.rows = rows;
        this.mappings = mappings;
        this.files = files;
        this.storage = storage;
        this.audit = audit;
    }

    public record RemovalResult(UUID importJobId, int recordsDeleted, int issuesDeleted, boolean sourceFileDeleted) {}

    @PreAuthorize(Perms.ADMIN)
    @Transactional
    public RemovalResult remove(UUID jobId, boolean confirm) {
        if (!confirm) throw ApiException.field("confirm", "must be true to remove an import and its records");
        ImportJob job = jobs.findByIdForUpdate(jobId).orElseThrow(() -> ApiException.notFound("Import"));
        if (!job.getStatus().isTerminal()) {
            throw new ApiException(ErrorCode.IMPORT_STATE_CONFLICT, "Cancel the import before removing it.");
        }
        if (job.getStatus() == ImportStatus.REMOVED) throw new ApiException(ErrorCode.CONFLICT, "The import was already removed.");
        int issuesDeleted = issues.deleteForImport(jobId);
        int recordsDeleted = records.deleteByImportJobId(jobId); // source values cascade in the database
        rows.deleteByImportJobId(jobId);
        mappings.deleteById(jobId);
        job.transitionTo(ImportStatus.REMOVED);
        boolean fileDeleted = false;
        SourceFile sf = files.findById(job.getSourceFileId()).orElseThrow();
        if (jobs.countBySourceFileId(sf.getId()) == 1 && sf.getPurgedAt() == null) {
            sf.markPurged();
            fileDeleted = true;
            String key = sf.getStorageKey();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(key);
                }
            });
        }
        audit.record("IMPORT_REMOVED", "IMPORT_JOB", jobId, Map.of("recordsDeleted", recordsDeleted,
                "issuesDeleted", issuesDeleted, "sourceFileDeleted", fileDeleted));
        return new RemovalResult(jobId, recordsDeleted, issuesDeleted, fileDeleted);
    }
}
