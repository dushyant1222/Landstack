package edu.iitp.landstack.imports;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.files.FileStorageService;
import edu.iitp.landstack.files.SourceFile;
import edu.iitp.landstack.files.SourceFileRepository;
import edu.iitp.landstack.landrecords.LandRecordRepository;
import edu.iitp.landstack.parsing.ImportRowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Retention and recovery:
 * <ul>
 *   <li>jobs left in PARSING (e.g. interrupted by a restart) become FAILED + retryable;</li>
 *   <li>unconfirmed jobs idle for N days become EXPIRED and their staging rows are purged;</li>
 *   <li>staging rows of finished jobs are purged after N days (lineage on records is kept);</li>
 *   <li>stored bytes of files whose jobs all ended without records are deleted after N days;</li>
 *   <li>stale temp uploads are deleted.</li>
 * </ul>
 */
@Service
public class RetentionService {
    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
    private static final EnumSet<ImportStatus> OPEN = EnumSet.of(ImportStatus.AWAITING_SHEET_SELECTION,
            ImportStatus.AWAITING_MAPPING, ImportStatus.PREVIEW_READY);
    private static final EnumSet<ImportStatus> FINISHED = EnumSet.of(ImportStatus.COMPLETED, ImportStatus.COMPLETED_WITH_WARNINGS,
            ImportStatus.FAILED, ImportStatus.EXPIRED, ImportStatus.CANCELLED);
    private static final EnumSet<ImportStatus> NO_RECORDS = EnumSet.of(ImportStatus.EXPIRED, ImportStatus.CANCELLED, ImportStatus.FAILED);

    private final ImportJobRepository jobs;
    private final ImportRowRepository rows;
    private final SourceFileRepository files;
    private final LandRecordRepository records;
    private final FileStorageService storage;
    private final AuditService audit;
    private final LandStackProperties.Retention cfg;

    public RetentionService(ImportJobRepository jobs, ImportRowRepository rows, SourceFileRepository files,
                            LandRecordRepository records, FileStorageService storage, AuditService audit, LandStackProperties props) {
        this.jobs = jobs;
        this.rows = rows;
        this.files = files;
        this.records = records;
        this.storage = storage;
        this.audit = audit;
        this.cfg = props.retention();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterrupted() {
        for (ImportJob j : jobs.findByStatusInAndUpdatedAtBefore(EnumSet.of(ImportStatus.PARSING), Instant.now())) {
            j.fail("PROCESSING_INTERRUPTED", "Processing was interrupted (for example by a server restart). Retry the import.", true);
            audit.recordAs(null, "SYSTEM", "IMPORT_PARSE_FAILED", "IMPORT_JOB", j.getId(), Map.of("code", "PROCESSING_INTERRUPTED"));
        }
    }

    @Scheduled(cron = "${landstack.retention.cron}")
    @Transactional
    public void runDaily() {
        Instant now = Instant.now();
        int failedStuck = 0, expired = 0, purgedRows = 0, purgedFiles = 0;
        for (ImportJob j : jobs.findByStatusInAndUpdatedAtBefore(EnumSet.of(ImportStatus.PARSING), now.minus(Duration.ofHours(1)))) {
            j.fail("PROCESSING_STUCK", "Processing did not finish within an hour. Retry the import.", true);
            failedStuck++;
        }
        for (ImportJob j : jobs.findByStatusInAndUpdatedAtBefore(OPEN, now.minus(Duration.ofDays(cfg.abandonedJobDays())))) {
            j.transitionTo(ImportStatus.EXPIRED);
            purgedRows += rows.deleteByImportJobId(j.getId());
            expired++;
        }
        for (ImportJob j : jobs.findByStatusInAndUpdatedAtBefore(FINISHED, now.minus(Duration.ofDays(cfg.stagingDays())))) {
            purgedRows += rows.deleteByImportJobId(j.getId());
        }
        List<ImportJob> ended = jobs.findByStatusInAndUpdatedAtBefore(NO_RECORDS, now.minus(Duration.ofDays(cfg.abandonedJobDays())));
        for (ImportJob j : ended) {
            SourceFile sf = files.findById(j.getSourceFileId()).orElse(null);
            if (sf == null || sf.getPurgedAt() != null) continue;
            if (j.getStatus() == ImportStatus.FAILED && j.isRetryable()) continue;
            boolean hasRecords = records.exists((root, q, cb) -> cb.equal(root.get("sourceFileId"), sf.getId()));
            boolean otherActive = jobs.countBySourceFileId(sf.getId()) > 1;
            if (!hasRecords && !otherActive) {
                storage.delete(sf.getStorageKey());
                sf.markPurged();
                purgedFiles++;
            }
        }
        cleanTemp();
        if (failedStuck + expired + purgedRows + purgedFiles > 0) {
            audit.recordAs(null, "SYSTEM", "RETENTION_RUN", "SYSTEM", null, Map.of("stuckFailed", failedStuck, "expired", expired,
                    "stagingRowsPurged", purgedRows, "filesPurged", purgedFiles));
        }
        log.info("Retention run: stuck={} expired={} stagingRows={} files={}", failedStuck, expired, purgedRows, purgedFiles);
    }

    private void cleanTemp() {
        Path tmp = storage.root().resolve(".tmp");
        if (!Files.isDirectory(tmp)) return;
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        try (Stream<Path> s = Files.list(tmp)) {
            s.filter(p -> {
                try {
                    return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff);
                } catch (IOException e) {
                    return false;
                }
            }).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
