package edu.iitp.landstack.mapping;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiError;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.common.Text;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.imports.ImportJob;
import edu.iitp.landstack.imports.ImportJobRepository;
import edu.iitp.landstack.imports.ImportStatus;
import edu.iitp.landstack.mapping.MappingDtos.*;
import edu.iitp.landstack.parsing.ImportRow;
import edu.iitp.landstack.parsing.ImportRowRepository;
import edu.iitp.landstack.parsing.ParsingService;
import edu.iitp.landstack.parsing.ProcessingAttempt;
import edu.iitp.landstack.parsing.ProcessingAttemptRepository;
import edu.iitp.landstack.quality.Severity;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import java.util.stream.Collectors;

/** Field mapping, normalization preview and operator corrections for staged rows. */
@Service
public class MappingService {
    private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("0.900");

    private final ImportJobRepository jobs;
    private final ImportRowRepository rows;
    private final ProcessingAttemptRepository attempts;
    private final ImportMappingRepository mappings;
    private final FieldMappingTemplateRepository templates;
    private final MappingSuggestionService suggestions;
    private final Normalizer normalizer;
    private final NormalizationDictionaries dict;
    private final AuditService audit;
    private final int maxCell;

    public MappingService(ImportJobRepository jobs, ImportRowRepository rows, ProcessingAttemptRepository attempts,
                          ImportMappingRepository mappings, FieldMappingTemplateRepository templates,
                          MappingSuggestionService suggestions, Normalizer normalizer, NormalizationDictionaries dict,
                          AuditService audit, LandStackProperties props) {
        this.jobs = jobs;
        this.rows = rows;
        this.attempts = attempts;
        this.mappings = mappings;
        this.templates = templates;
        this.suggestions = suggestions;
        this.normalizer = normalizer;
        this.dict = dict;
        this.audit = audit;
        this.maxCell = props.parsing().maxCellLength();
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public SuggestionsDto suggestions(UUID jobId) {
        ImportJob job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Import"));
        List<String> cols = job.getSourceColumns() == null ? List.of() : job.getSourceColumns();
        Map<String, List<String>> samples = new LinkedHashMap<>();
        cols.forEach(c -> samples.put(c, new ArrayList<>()));
        currentAttempt(jobId).ifPresent(a -> {
            for (ImportRow r : rows.findByImportJobIdAndAttemptId(jobId, a.getId(),
                    org.springframework.data.domain.PageRequest.of(0, 50, org.springframework.data.domain.Sort.by("rowNumber")))) {
                for (String c : cols) {
                    String v = r.getRawValues().get(c);
                    List<String> s = samples.get(c);
                    if (v != null && !v.isBlank() && s.size() < 5 && !s.contains(v)) s.add(Text.truncate(v, 80));
                }
            }
        });
        CurrentMappingDto current = mappings.findById(jobId).map(m -> new CurrentMappingDto(m.getMapping(), m.getOptions(),
                m.getNormalizationVersion(), m.getTemplateId(), m.getTemplateVersion(), m.getUpdatedAt())).orElse(null);
        return new SuggestionsDto(cols, samples, suggestions.suggest(cols), current);
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public PreviewSummary applyMapping(UUID jobId, MappingRequest req) {
        ImportJob job = jobs.findByIdForUpdate(jobId).orElseThrow(() -> ApiException.notFound("Import"));
        if (job.getStatus() != ImportStatus.AWAITING_MAPPING && job.getStatus() != ImportStatus.PREVIEW_READY) {
            throw new ApiException(ErrorCode.IMPORT_STATE_CONFLICT, "Mapping can only be changed before the import is confirmed.");
        }
        MappingConfig cfg = toConfig(job, req);
        Integer templateVersion = null;
        if (req.templateId() != null) {
            templateVersion = templates.findById(req.templateId()).map(FieldMappingTemplate::getVersion)
                    .orElseThrow(() -> ApiException.field("templateId", "template not found"));
        }
        ImportMapping m = mappings.findById(jobId).orElseGet(() -> new ImportMapping(jobId));
        m.update(cfg, req.templateId(), templateVersion, CurrentUser.id());
        mappings.save(m);
        PreviewSummary summary = normalizeAll(job, cfg);
        job.transitionTo(ImportStatus.PREVIEW_READY);
        audit.record("MAPPING_SAVED", "IMPORT_JOB", jobId, Map.of("fields", String.join(",", cfg.columnsByKey().keySet()),
                "normalizationVersion", MappingConfig.NORMALIZATION_VERSION, "template", req.templateId() != null));
        return summary;
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public PreviewSummary correctRow(UUID jobId, UUID rowId, CorrectionRequest req) {
        ImportJob job = jobs.findByIdForUpdate(jobId).orElseThrow(() -> ApiException.notFound("Import"));
        if (job.getStatus() != ImportStatus.PREVIEW_READY) {
            throw new ApiException(ErrorCode.IMPORT_STATE_CONFLICT, "Rows can only be corrected while the preview is open.");
        }
        ImportRow row = rows.findById(rowId).filter(r -> r.getImportJobId().equals(jobId))
                .orElseThrow(() -> ApiException.notFound("Row"));
        List<String> changed = new ArrayList<>();
        if (req.values() != null && !req.values().isEmpty()) {
            Map<String, String> corrections = row.getCorrectedValues() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(row.getCorrectedValues());
            for (var e : req.values().entrySet()) {
                if (!job.getSourceColumns().contains(e.getKey())) throw ApiException.field("values", "unknown column '" + e.getKey() + "'");
                String v = e.getValue() == null ? "" : e.getValue();
                if (v.length() > maxCell) throw ApiException.field("values", "value too long for column '" + e.getKey() + "'");
                if (Objects.equals(v, row.getRawValues().get(e.getKey()))) corrections.remove(e.getKey());
                else corrections.put(e.getKey(), v);
                changed.add(e.getKey());
            }
            row.setCorrectedValues(corrections.isEmpty() ? null : corrections);
        }
        if (req.excluded() != null) row.setExcluded(req.excluded());
        if (req.reviewed() != null) row.setReviewed(req.reviewed());
        PreviewSummary summary = normalizeAll(job, mappings.findById(jobId).orElseThrow().toConfig());
        audit.record("ROW_CORRECTED", "IMPORT_JOB", jobId, Map.of("row", row.getRowNumber(), "columns", String.join(",", changed),
                "excluded", row.isExcluded(), "reviewed", row.isReviewed()));
        return summary;
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public PreviewSummary summary(UUID jobId) {
        ImportJob job = jobs.findById(jobId).orElseThrow(() -> ApiException.notFound("Import"));
        Optional<ProcessingAttempt> a = currentAttempt(jobId);
        if (a.isEmpty()) return new PreviewSummary(0, 0, 0, 0, 0, Map.of());
        return summarize(rows.findByImportJobIdAndAttemptIdOrderByRowNumberAsc(job.getId(), a.get().getId()));
    }

    /** Normalizes and validates every staged row of the current attempt (deterministic). */
    public PreviewSummary normalizeAll(ImportJob job, MappingConfig cfg) {
        ProcessingAttempt attempt = currentAttempt(job.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.IMPORT_STATE_CONFLICT, "There are no parsed rows to map."));
        List<ImportRow> all = rows.findByImportJobIdAndAttemptIdOrderByRowNumberAsc(job.getId(), attempt.getId());
        Map<UUID, Normalizer.Result> results = new HashMap<>();
        Map<UUID, List<RowIssue>> extra = new HashMap<>();
        for (ImportRow r : all) {
            Normalizer.Result res = normalizer.normalize(r.effectiveValues(), cfg);
            results.put(r.getId(), res);
            List<RowIssue> more = new ArrayList<>();
            if (r.getConfidence() != null && r.getConfidence().compareTo(LOW_CONFIDENCE) < 0 && !r.isReviewed()) {
                more.add(new RowIssue("EXTRACTION_LOW_CONFIDENCE", null, Severity.WARNING, false,
                        "Values in this row were machine-extracted (" + r.getExtractionMethod() + ", page " + r.getPageRef()
                                + ") and have not been reviewed. Check them and mark the row reviewed.", null));
            }
            extra.put(r.getId(), more);
        }
        flagInFileDuplicates(all, results, extra);
        for (ImportRow r : all) {
            Normalizer.Result res = results.get(r.getId());
            List<RowIssue> issues = new ArrayList<>(res.issues());
            issues.addAll(extra.get(r.getId()));
            ImportRow.State state = issues.stream().anyMatch(RowIssue::blocking) ? ImportRow.State.BLOCKED
                    : issues.stream().anyMatch(i -> i.severity() != Severity.INFO) ? ImportRow.State.WARNING : ImportRow.State.VALID;
            r.applyResult(res.tracesMap(), issues.stream().map(RowIssue::toMap).toList(), state);
        }
        PreviewSummary s = summarize(all);
        job.setCounts(s.totalRows(), s.validRows(), s.warningRows(), s.blockedRows() + s.excludedRows());
        return s;
    }

    private void flagInFileDuplicates(List<ImportRow> all, Map<UUID, Normalizer.Result> results, Map<UUID, List<RowIssue>> extra) {
        Map<String, List<ImportRow>> byKey = new LinkedHashMap<>();
        Map<Map<String, String>, List<ImportRow>> identical = new HashMap<>();
        for (ImportRow r : all) {
            if (r.isExcluded()) continue;
            NormalizedRecord n = results.get(r.getId()).record();
            String id = n.parcelId() != null ? "p:" + n.parcelId().toLowerCase()
                    : n.surveyKhasraNo() != null ? "k:" + n.surveyKhasraNo().toLowerCase() : null;
            if (id != null) {
                String key = id + "|" + lower(n.villageLocality()) + "|" + lower(n.tehsil()) + "|" + lower(n.district());
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
            identical.computeIfAbsent(r.effectiveValues(), k -> new ArrayList<>()).add(r);
        }
        for (List<ImportRow> group : byKey.values()) {
            if (group.size() < 2) continue;
            for (ImportRow r : group) {
                String others = group.stream().filter(o -> o != r).map(o -> String.valueOf(o.getRowNumber())).collect(Collectors.joining(", "));
                extra.get(r.getId()).add(new RowIssue("DUP_ROW_IN_FILE", "parcel_id", Severity.WARNING, false,
                        "The same identifier and locality also appear in row(s) " + others + " of this file. Rows are not merged; review them.", null));
            }
        }
        for (List<ImportRow> group : identical.values()) {
            if (group.size() < 2) continue;
            for (ImportRow r : group) {
                String others = group.stream().filter(o -> o != r).map(o -> String.valueOf(o.getRowNumber())).collect(Collectors.joining(", "));
                extra.get(r.getId()).add(new RowIssue("DUP_IDENTICAL_ROW", null, Severity.WARNING, false,
                        "This row is identical to row(s) " + others + ". Consider excluding the repeat.", null));
            }
        }
    }

    private static PreviewSummary summarize(List<ImportRow> all) {
        int valid = 0, warning = 0, blocked = 0, excluded = 0;
        Map<String, Long> counts = new TreeMap<>();
        for (ImportRow r : all) {
            switch (r.getRowState()) {
                case VALID -> valid++;
                case WARNING -> warning++;
                case BLOCKED -> blocked++;
                case EXCLUDED -> excluded++;
                case PENDING -> { }
            }
            if (r.getValidation() != null && !r.isExcluded()) {
                for (Map<String, Object> i : r.getValidation()) counts.merge((String) i.get("ruleId"), 1L, Long::sum);
            }
        }
        return new PreviewSummary(all.size(), valid, warning, blocked, excluded, counts);
    }

    private MappingConfig toConfig(ImportJob job, MappingRequest req) {
        List<String> cols = job.getSourceColumns() == null ? List.of() : job.getSourceColumns();
        Map<CanonicalField, String> map = new EnumMap<>(CanonicalField.class);
        List<ApiError.FieldError> errors = new ArrayList<>();
        Set<String> usedColumns = new HashSet<>();
        for (var e : req.mapping().entrySet()) {
            if (e.getValue() == null || e.getValue().isBlank()) continue;
            Optional<CanonicalField> f = CanonicalField.byKey(e.getKey());
            if (f.isEmpty()) { errors.add(new ApiError.FieldError("mapping." + e.getKey(), "unknown canonical field")); continue; }
            if (!cols.contains(e.getValue())) { errors.add(new ApiError.FieldError("mapping." + e.getKey(), "column '" + e.getValue() + "' is not in this file")); continue; }
            if (!usedColumns.add(e.getValue())) { errors.add(new ApiError.FieldError("mapping." + e.getKey(), "column '" + e.getValue() + "' is already mapped to another field")); continue; }
            map.put(f.get(), e.getValue());
        }
        boolean requireId = req.requireIdentifier() == null || req.requireIdentifier();
        if (requireId && !map.containsKey(CanonicalField.PARCEL_ID) && !map.containsKey(CanonicalField.SURVEY_KHASRA_NO)) {
            errors.add(new ApiError.FieldError("mapping.parcel_id", "map a Parcel ID/ULPIN or Survey/Khasra column (required by the dataset profile)"));
        }
        if (req.requireDistrict() && !map.containsKey(CanonicalField.DISTRICT)) {
            errors.add(new ApiError.FieldError("mapping.district", "map a District column (required by the dataset profile)"));
        }
        if ((map.containsKey(CanonicalField.LATITUDE)) != (map.containsKey(CanonicalField.LONGITUDE))) {
            errors.add(new ApiError.FieldError("mapping.latitude", "map both latitude and longitude, or neither"));
        }
        String declaredUnit = Text.trimToNull(req.declaredAreaUnit());
        if (declaredUnit != null && !dict.resolveUnit(declaredUnit, null).known()) {
            errors.add(new ApiError.FieldError("declaredAreaUnit", "unit '" + declaredUnit + "' is not in the conversion table"));
        }
        String declaredState = Text.trimToNull(req.declaredState());
        if (declaredState != null && dict.canonicalState(declaredState).isEmpty()) {
            errors.add(new ApiError.FieldError("declaredState", "'" + declaredState + "' is not a recognised Indian state/UT"));
        }
        if (!errors.isEmpty()) throw new ApiException(ErrorCode.VALIDATION_FAILED, "The mapping is incomplete or invalid.", errors);
        return new MappingConfig(map, req.dayFirst(), declaredUnit, declaredState, requireId, req.requireDistrict());
    }

    private Optional<ProcessingAttempt> currentAttempt(UUID jobId) {
        return attempts.findFirstByImportJobIdAndStageInAndOutcomeOrderByStartedAtDesc(
                jobId, ParsingService.PARSE_STAGES, ProcessingAttempt.Outcome.SUCCEEDED);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

}
