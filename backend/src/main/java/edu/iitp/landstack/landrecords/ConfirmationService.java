package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.imports.ImportJob;
import edu.iitp.landstack.imports.ImportJobRepository;
import edu.iitp.landstack.imports.ImportStatus;
import edu.iitp.landstack.mapping.*;
import edu.iitp.landstack.parsing.ImportRow;
import edu.iitp.landstack.parsing.ImportRowRepository;
import edu.iitp.landstack.parsing.ParsingService;
import edu.iitp.landstack.parsing.ProcessingAttempt;
import edu.iitp.landstack.parsing.ProcessingAttemptRepository;
import edu.iitp.landstack.quality.QualityEngine;
import edu.iitp.landstack.quality.QualityIssueRepository;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Converts a reviewed preview into confirmed land records in ONE transaction (all-or-nothing for the
 * selected rows). Idempotent per confirmation key; a database unique constraint on
 * (import_job_id, source_row_number) is the second line of defence against duplicates.
 */
@Service
public class ConfirmationService {
    private static final int MAX_REJECTED_DIAGNOSTICS = 500;

    private final ImportJobRepository jobs;
    private final ImportRowRepository rows;
    private final ProcessingAttemptRepository attempts;
    private final ImportMappingRepository mappings;
    private final MappingService mappingService;
    private final Normalizer normalizer;
    private final LandRecordRepository records;
    private final LandRecordSourceValueRepository sourceValues;
    private final QualityEngine quality;
    private final QualityIssueRepository issues;
    private final AuditService audit;

    public ConfirmationService(ImportJobRepository jobs, ImportRowRepository rows, ProcessingAttemptRepository attempts,
                               ImportMappingRepository mappings, MappingService mappingService, Normalizer normalizer,
                               LandRecordRepository records, LandRecordSourceValueRepository sourceValues, QualityEngine quality,
                               QualityIssueRepository issues, AuditService audit) {
        this.jobs = jobs;
        this.rows = rows;
        this.attempts = attempts;
        this.mappings = mappings;
        this.mappingService = mappingService;
        this.normalizer = normalizer;
        this.records = records;
        this.sourceValues = sourceValues;
        this.quality = quality;
        this.issues = issues;
        this.audit = audit;
    }

    public record ConfirmRequest(UUID confirmationKey, Boolean includeWarningRows) {}

    public record ConfirmResult(UUID importJobId, ImportStatus status, int importedRows, int rejectedRows, int warningRows,
                                long qualityIssuesCreated, boolean replayed) {}

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public ConfirmResult confirm(UUID jobId, ConfirmRequest req) {
        if (req == null || req.confirmationKey() == null) throw ApiException.field("confirmationKey", "is required");
        UUID key = req.confirmationKey();
        Optional<ImportJob> byKey = jobs.findByConfirmationKey(key);
        if (byKey.isPresent() && !byKey.get().getId().equals(jobId)) {
            throw new ApiException(ErrorCode.CONFLICT, "This confirmation key was already used for a different import.");
        }
        ImportJob job = jobs.findByIdForUpdate(jobId).orElseThrow(() -> ApiException.notFound("Import"));
        if (job.getStatus().isConfirmed()) {
            if (key.equals(job.getConfirmationKey())) return replay(job);
            throw new ApiException(ErrorCode.IMPORT_ALREADY_CONFIRMED, "This import has already been confirmed.");
        }
        if (job.getStatus() != ImportStatus.PREVIEW_READY) {
            throw new ApiException(ErrorCode.IMPORT_STATE_CONFLICT, "Only an import with a ready preview can be confirmed.");
        }
        ImportMapping mapping = mappings.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.IMPORT_STATE_CONFLICT, "Save a field mapping before confirming."));
        MappingConfig cfg = mapping.toConfig();
        // Re-validate everything with the saved mapping so the persisted result equals what the operator reviewed.
        mappingService.normalizeAll(job, cfg);

        ProcessingAttempt attempt = attempts.findFirstByImportJobIdAndStageInAndOutcomeOrderByStartedAtDesc(
                jobId, ParsingService.PARSE_STAGES, ProcessingAttempt.Outcome.SUCCEEDED).orElseThrow();
        List<ImportRow> all = rows.findByImportJobIdAndAttemptIdOrderByRowNumberAsc(jobId, attempt.getId());
        boolean includeWarnings = req.includeWarningRows() == null || req.includeWarningRows();
        List<ImportRow> selected = new ArrayList<>();
        List<Map<String, Object>> rejected = new ArrayList<>();
        int rejectedCount = 0;
        int warningRows = 0;
        for (ImportRow r : all) {
            boolean take = r.getRowState() == ImportRow.State.VALID || (includeWarnings && r.getRowState() == ImportRow.State.WARNING);
            if (take) {
                selected.add(r);
                if (r.getRowState() == ImportRow.State.WARNING) warningRows++;
            } else {
                rejectedCount++;
                if (rejected.size() < MAX_REJECTED_DIAGNOSTICS) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("row", r.getRowNumber());
                    d.put("state", r.getRowState().name());
                    d.put("rules", r.getValidation() == null ? List.of()
                            : r.getValidation().stream().filter(v -> Boolean.TRUE.equals(v.get("blocking")) || r.getRowState() != ImportRow.State.BLOCKED)
                            .map(v -> v.get("ruleId")).distinct().toList());
                    rejected.add(d);
                }
            }
        }
        if (selected.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "No rows are eligible for confirmation. Correct blocking errors or include rows with warnings.");
        }

        job.transitionTo(ImportStatus.CONFIRMING);
        UUID actor = CurrentUser.id();
        List<LandRecord> created = new ArrayList<>(selected.size());
        Map<LandRecord, List<RowIssue>> rowIssues = new IdentityHashMap<>();
        List<LandRecordSourceValue> lineage = new ArrayList<>();
        for (ImportRow r : selected) {
            Normalizer.Result res = normalizer.normalize(r.effectiveValues(), cfg);
            LandRecord rec = new LandRecord(res.record(), job.getSourceFileId(), jobId, r.getRowNumber(), r.getPageRef());
            created.add(rec);
            Set<String> corrected = r.getCorrectedValues() == null ? Set.of() : r.getCorrectedValues().keySet();
            for (var e : res.traces().entrySet()) {
                Normalizer.FieldTrace t = e.getValue();
                boolean wasCorrected = t.sourceColumn() != null && corrected.contains(t.sourceColumn());
                String raw = wasCorrected ? r.getRawValues().get(t.sourceColumn()) : t.raw();
                String transformation = wasCorrected
                        ? "operator correction before import (corrected to '" + r.getCorrectedValues().get(t.sourceColumn()) + "'); " + t.transformation()
                        : t.transformation();
                if (r.getExtractionMethod() != null) transformation = "extracted via " + r.getExtractionMethod()
                        + (r.getPageRef() != null ? " (page " + r.getPageRef() + ")" : "") + "; " + transformation;
                lineage.add(new LandRecordSourceValue(rec.getId(), e.getKey().key(), t.sourceColumn(), raw, t.normalized(),
                        transformation, wasCorrected ? actor : null));
            }
            List<RowIssue> carried = new ArrayList<>(res.issues());
            if (r.getValidation() != null) {
                for (Map<String, Object> v : r.getValidation()) {
                    RowIssue ri = RowIssue.fromMap(v);
                    if (ri.ruleId().startsWith("DUP_") || ri.ruleId().startsWith("EXTRACTION_")) carried.add(ri);
                }
            }
            rowIssues.put(rec, carried);
        }
        records.saveAll(created);
        records.flush();
        sourceValues.saveAll(lineage);

        long issuesBefore = issues.countByImportJobId(jobId);
        for (LandRecord rec : created) quality.persistRowIssues(rec, rowIssues.get(rec));
        quality.evaluateDuplicates(created);
        issues.flush();
        long issuesCreated = issues.countByImportJobId(jobId) - issuesBefore;

        int persisted = (int) records.countByImportJobId(jobId); // counts from the database, not from the plan
        if (persisted != created.size()) {
            throw new IllegalStateException("Persisted row count mismatch");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rejectedRows", rejected);
        summary.put("rejectedRowsTruncated", rejectedCount > rejected.size());
        summary.put("includeWarningRows", includeWarnings);
        summary.put("normalizationVersion", mapping.getNormalizationVersion());
        summary.put("rulesVersion", quality.rulesVersion());
        summary.put("qualityIssuesCreated", issuesCreated);
        job.markConfirmed(key, actor, persisted, summary);
        job.setCounts(all.size(), persisted - warningRows, warningRows, rejectedCount);
        ImportStatus finalStatus = rejectedCount > 0 || warningRows > 0 || issuesCreated > 0
                ? ImportStatus.COMPLETED_WITH_WARNINGS : ImportStatus.COMPLETED;
        job.transitionTo(finalStatus);
        audit.record("IMPORT_CONFIRMED", "IMPORT_JOB", jobId, Map.of("importedRows", persisted, "rejectedRows", rejectedCount,
                "warningRows", warningRows, "qualityIssuesCreated", issuesCreated));
        return new ConfirmResult(jobId, finalStatus, persisted, rejectedCount, warningRows, issuesCreated, false);
    }

    private ConfirmResult replay(ImportJob job) {
        Map<String, Object> s = job.getResultSummary() == null ? Map.of() : job.getResultSummary();
        Object qi = s.getOrDefault("qualityIssuesCreated", 0);
        return new ConfirmResult(job.getId(), job.getStatus(), job.getImportedRows() == null ? 0 : job.getImportedRows(),
                job.getRejectedRows() == null ? 0 : job.getRejectedRows(), job.getWarningRows() == null ? 0 : job.getWarningRows(),
                qi instanceof Number n ? n.longValue() : 0, true);
    }
}
