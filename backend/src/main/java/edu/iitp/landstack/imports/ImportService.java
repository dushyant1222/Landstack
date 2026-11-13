package edu.iitp.landstack.imports;

import edu.iitp.landstack.audit.AuditEvent;
import edu.iitp.landstack.audit.AuditEventRepository;
import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.common.Paging;
import edu.iitp.landstack.common.Text;
import edu.iitp.landstack.files.*;
import edu.iitp.landstack.imports.ImportDtos.ImportJobDto;
import edu.iitp.landstack.imports.ImportDtos.TimelineEntry;
import edu.iitp.landstack.parsing.ImportRowRepository;
import edu.iitp.landstack.parsing.ProcessingAttempt;
import edu.iitp.landstack.parsing.ProcessingAttemptRepository;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.AppUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Service
public class ImportService {
    private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "updatedAt", "updatedAt", "status", "status");

    private final ImportJobRepository jobs;
    private final SourceFileRepository files;
    private final FileStorageService storage;
    private final AppUserRepository users;
    private final AuditService audit;
    private final AuditEventRepository auditEvents;
    private final ProcessingAttemptRepository attempts;
    private final ImportRowRepository rows;
    private final ApplicationEventPublisher events;

    public ImportService(ImportJobRepository jobs, SourceFileRepository files, FileStorageService storage,
                         AppUserRepository users, AuditService audit, AuditEventRepository auditEvents,
                         ProcessingAttemptRepository attempts, ImportRowRepository rows, ApplicationEventPublisher events) {
        this.jobs = jobs;
        this.files = files;
        this.storage = storage;
        this.users = users;
        this.audit = audit;
        this.auditEvents = auditEvents;
        this.attempts = attempts;
        this.rows = rows;
        this.events = events;
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public ImportJobDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(ErrorCode.FILE_UNREADABLE, "Choose a non-empty file to upload.");
        String name = FilenameSanitizer.sanitize(file.getOriginalFilename());
        String ext = FilenameSanitizer.extension(name);
        FileStorageService.TempFile temp;
        try (InputStream in = file.getInputStream()) {
            temp = storage.writeTemp(in);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.FILE_UNREADABLE, "The upload could not be read. Please try again.");
        }
        String key;
        DetectedType type;
        try {
            type = FileTypeDetector.detect(temp.path(), ext);
            key = storage.commit(temp);
        } catch (IOException e) {
            storage.deleteTemp(temp);
            throw new ApiException(ErrorCode.FILE_UNREADABLE, "The file could not be stored. Please try again.");
        } catch (RuntimeException e) {
            storage.deleteTemp(temp);
            audit.recordIndependent("IMPORT_UPLOAD_REJECTED", "SOURCE_FILE", null,
                    Map.of("extension", ext, "sizeBytes", temp.size(),
                            "reason", e instanceof ApiException a ? a.code().name() : "ERROR"));
            throw e;
        }
        final String storedKey = key;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.delete(storedKey); // no orphan bytes on rollback
            }
        });

        UUID userId = CurrentUser.id();
        List<SourceFile> sameChecksum = files.findBySha256OrderByUploadedAtDesc(temp.sha256());
        SourceFile sf = files.save(new SourceFile(name, key, type, Text.truncate(file.getContentType(), 200),
                temp.size(), temp.sha256(), userId));
        ImportJob job = new ImportJob(sf.getId(), userId);
        if (!sameChecksum.isEmpty()) {
            jobs.findFirstBySourceFileIdInAndIdNotOrderByCreatedAtAsc(
                    sameChecksum.stream().map(SourceFile::getId).toList(), job.getId())
                    .ifPresent(prev -> job.setDuplicateOfJobId(prev.getId()));
        }
        job.transitionTo(ImportStatus.VALIDATING);
        job.transitionTo(ImportStatus.PARSING); // signature/size/readability checks passed above
        jobs.save(job);
        audit.record("IMPORT_UPLOADED", "IMPORT_JOB", job.getId(), Map.of("fileType", type.name(),
                "sizeBytes", temp.size(), "duplicateChecksum", job.getDuplicateOfJobId() != null));
        events.publishEvent(new ImportEvents.ParseRequested(job.getId()));
        return toDto(job, sf);
    }

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public PageResponse<ImportJobDto> list(ImportStatus status, UUID createdBy, String q, Instant from, Instant to,
                                           int page, int size, String sort) {
        Specification<ImportJob> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (createdBy != null) ps.add(cb.equal(root.get("createdBy"), createdBy));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) ps.add(cb.lessThan(root.get("createdAt"), to));
            if (!Text.isBlank(q)) {
                var sub = query.subquery(UUID.class);
                var sfRoot = sub.from(SourceFile.class);
                sub.select(sfRoot.get("id")).where(cb.like(cb.lower(sfRoot.get("originalFilename")), Text.likeContains(q.strip()), '\\'));
                ps.add(root.get("sourceFileId").in(sub));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var result = jobs.findAll(spec, Paging.of(page, size, sort, SORTS, "createdAt,desc"));
        Map<UUID, SourceFile> sfs = new HashMap<>();
        files.findAllById(result.getContent().stream().map(ImportJob::getSourceFileId).toList()).forEach(f -> sfs.put(f.getId(), f));
        Map<UUID, String> names = userNames(result.getContent().stream().map(ImportJob::getCreatedBy).toList());
        return PageResponse.of(result, j -> toDto(j, sfs.get(j.getSourceFileId()), names.get(j.getCreatedBy())));
    }

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public ImportJobDto get(UUID id) {
        ImportJob job = load(id);
        return toDto(job, files.findById(job.getSourceFileId()).orElseThrow());
    }

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(UUID id) {
        ImportJob job = load(id);
        List<TimelineEntry> out = new ArrayList<>();
        List<AuditEvent> evs = auditEvents.findByTargetIdOrderByOccurredAtAsc(job.getId());
        Map<UUID, String> names = userNames(evs.stream().map(AuditEvent::getActorId).filter(Objects::nonNull).toList());
        for (AuditEvent e : evs) {
            out.add(new TimelineEntry(e.getOccurredAt(), "AUDIT", e.getAction(), summaryText(e.getSummary()), names.get(e.getActorId())));
        }
        for (ProcessingAttempt a : attempts.findByImportJobIdOrderByStartedAtAsc(job.getId())) {
            out.add(new TimelineEntry(a.getStartedAt(), "PROCESSING", a.getStage() + " via " + a.getProcessor(),
                    "Outcome: " + a.getOutcome() + (a.getFinishedAt() != null
                            ? " (" + java.time.Duration.between(a.getStartedAt(), a.getFinishedAt()).toMillis() + " ms)" : ""), null));
        }
        out.sort(Comparator.comparing(TimelineEntry::at));
        return out;
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public ImportJobDto cancel(UUID id) {
        ImportJob job = jobs.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Import"));
        job.transitionTo(ImportStatus.CANCELLED);
        rows.deleteByImportJobId(job.getId());
        audit.record("IMPORT_CANCELLED", "IMPORT_JOB", job.getId(), Map.of());
        return get(id);
    }

    /** Re-runs parsing for a FAILED job marked retryable (e.g. processing service was unavailable). */
    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public ImportJobDto retry(UUID id) {
        ImportJob job = jobs.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Import"));
        if (job.getStatus() != ImportStatus.FAILED || !job.isRetryable()) {
            throw new ApiException(ErrorCode.IMPORT_STATE_CONFLICT, "Only failed imports marked as retryable can be retried.");
        }
        SourceFile sf = files.findById(job.getSourceFileId()).orElseThrow();
        if (sf.getPurgedAt() != null) throw new ApiException(ErrorCode.CONFLICT, "The source file has been removed; upload it again.");
        job.transitionTo(ImportStatus.PARSING);
        audit.record("IMPORT_RETRY_REQUESTED", "IMPORT_JOB", job.getId(), Map.of());
        events.publishEvent(new ImportEvents.ParseRequested(job.getId()));
        return toDto(job, sf);
    }

    /** Choose a worksheet and/or header row; re-parses. Resets mapping/corrections of earlier attempts. */
    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public ImportJobDto selectSheet(UUID id, ImportDtos.SheetSelectionRequest req) {
        ImportJob job = jobs.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Import"));
        if (!job.getStatus().isEditable()) {
            throw new ApiException(ErrorCode.IMPORT_STATE_CONFLICT, "The sheet or header row can no longer be changed for this import.");
        }
        SourceFile sf = files.findById(job.getSourceFileId()).orElseThrow();
        if (req.sheetName() != null) {
            if (sf.getDetectedType() != DetectedType.XLSX) throw ApiException.field("sheetName", "only applies to Excel files");
            if (job.getSheetNames() == null || !job.getSheetNames().contains(req.sheetName())) {
                throw ApiException.field("sheetName", "is not a worksheet in this workbook");
            }
            job.setSelectedSheet(req.sheetName());
        } else if (sf.getDetectedType() == DetectedType.XLSX && job.getSelectedSheet() == null) {
            throw ApiException.field("sheetName", "select a worksheet");
        }
        if (sf.getDetectedType() == DetectedType.PDF && req.headerRow() != null) {
            throw ApiException.field("headerRow", "does not apply to PDF extraction");
        }
        job.setHeaderRow(req.headerRow());
        job.transitionTo(ImportStatus.PARSING);
        audit.record("IMPORT_SHEET_SELECTED", "IMPORT_JOB", job.getId(),
                Map.of("sheetSelected", req.sheetName() != null, "headerRow", req.headerRow() == null ? "auto" : req.headerRow()));
        events.publishEvent(new ImportEvents.ParseRequested(job.getId()));
        return toDto(job, sf);
    }

    public ImportJob load(UUID id) {
        return jobs.findById(id).orElseThrow(() -> ApiException.notFound("Import"));
    }

    public ImportJobDto toDto(ImportJob j, SourceFile f) {
        return toDto(j, f, users.findById(j.getCreatedBy()).map(AppUser::getDisplayName).orElse(null));
    }

    private ImportJobDto toDto(ImportJob j, SourceFile f, String creatorName) {
        return new ImportJobDto(j.getId(), j.getStatus(), j.getStatusCode(), j.getStatusMessage(), j.isRetryable(),
                j.getSourceFileId(), f == null ? null : f.getOriginalFilename(), f == null ? null : f.getDetectedType(),
                f == null ? 0 : f.getSizeBytes(), f == null ? null : f.getSha256(), j.getCreatedBy(), creatorName,
                j.getCreatedAt(), j.getUpdatedAt(), j.getDuplicateOfJobId(), j.getSheetNames(), j.getSelectedSheet(),
                j.getHeaderRow(), j.getSourceColumns(), j.getTotalRows(), j.getValidRows(), j.getWarningRows(),
                j.getRejectedRows(), j.getImportedRows(), j.getConfirmedAt(), j.getResultSummary(), j.getVersion());
    }

    private Map<UUID, String> userNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(new HashSet<>(ids)).forEach(u -> names.put(u.getId(), u.getDisplayName()));
        return names;
    }

    private static String summaryText(Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) return null;
        StringJoiner sj = new StringJoiner(", ");
        summary.forEach((k, v) -> sj.add(k + ": " + v));
        return sj.toString();
    }
}
