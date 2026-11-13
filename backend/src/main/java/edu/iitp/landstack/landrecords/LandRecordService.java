package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiError;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.common.Paging;
import edu.iitp.landstack.files.SourceFileRepository;
import edu.iitp.landstack.imports.ImportJobRepository;
import edu.iitp.landstack.landrecords.LandRecordDtos.*;
import edu.iitp.landstack.mapping.CanonicalField;
import edu.iitp.landstack.mapping.ImportMappingRepository;
import edu.iitp.landstack.mapping.Normalizer;
import edu.iitp.landstack.mapping.RowIssue;
import edu.iitp.landstack.quality.QualityEngine;
import edu.iitp.landstack.quality.QualityIssueRepository;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import edu.iitp.landstack.users.AppUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class LandRecordService {
    private static final Map<String, String> SORTS = Map.ofEntries(
            Map.entry("createdAt", "createdAt"), Map.entry("parcelId", "parcelId"), Map.entry("surveyKhasraNo", "surveyKhasraNo"),
            Map.entry("village", "villageLocality"), Map.entry("district", "district"), Map.entry("state", "state"),
            Map.entry("area", "landAreaSqm"), Map.entry("landType", "landTypeCategory"), Map.entry("updatedAt", "updatedAt"));
    private static final Set<String> EDITABLE = Set.of("parcel_id", "survey_khasra_no", "owner_name", "land_area_value",
            "land_area_unit", "land_type", "registration_ref", "registration_date", "village_locality", "tehsil", "district",
            "state", "latitude", "longitude");

    private final LandRecordRepository records;
    private final LandRecordSourceValueRepository sourceValues;
    private final SourceFileRepository files;
    private final ImportJobRepository jobs;
    private final ImportMappingRepository mappings;
    private final QualityIssueRepository issues;
    private final QualityEngine quality;
    private final RecordNormalization normalization;
    private final AppUserRepository users;
    private final OwnerMasking masking;
    private final AuditService audit;

    public LandRecordService(LandRecordRepository records, LandRecordSourceValueRepository sourceValues, SourceFileRepository files,
                             ImportJobRepository jobs, ImportMappingRepository mappings, QualityIssueRepository issues,
                             QualityEngine quality, RecordNormalization normalization, AppUserRepository users,
                             OwnerMasking masking, AuditService audit) {
        this.records = records;
        this.sourceValues = sourceValues;
        this.files = files;
        this.jobs = jobs;
        this.mappings = mappings;
        this.issues = issues;
        this.quality = quality;
        this.normalization = normalization;
        this.users = users;
        this.masking = masking;
        this.audit = audit;
    }

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public PageResponse<LandRecordListItem> search(LandRecordFilter filter, int page, int size, String sort) {
        Page<LandRecord> result = records.findAll(filter.toSpec(!masking.active()), Paging.of(page, size, sort, SORTS, "createdAt,desc"));
        Map<UUID, Long> open = openCounts(result.getContent().stream().map(LandRecord::getId).toList());
        return PageResponse.of(result, r -> item(r, open.getOrDefault(r.getId(), 0L)));
    }

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public LandRecordDetail get(UUID id) {
        LandRecord r = records.findById(id).orElseThrow(() -> ApiException.notFound("Land record"));
        var sf = files.findById(r.getSourceFileId()).orElseThrow();
        var job = jobs.findById(r.getImportJobId()).orElseThrow();
        var mapping = mappings.findById(r.getImportJobId()).orElse(null);
        List<LandRecordSourceValue> values = sourceValues.findByLandRecordIdOrderByCreatedAtAsc(id);
        Set<UUID> userIds = new HashSet<>();
        values.stream().map(LandRecordSourceValue::getChangedBy).filter(Objects::nonNull).forEach(userIds::add);
        if (job.getConfirmedBy() != null) userIds.add(job.getConfirmedBy());
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getDisplayName()));
        boolean mask = masking.active();
        List<SourceValueDto> sv = values.stream().map(v -> {
            boolean owner = "owner_name".equals(v.getCanonicalField());
            return new SourceValueDto(v.getCanonicalField(), v.getSourceColumn(),
                    owner && mask ? OwnerMasking.mask(v.getRawValue()) : v.getRawValue(),
                    owner && mask ? OwnerMasking.mask(v.getNormalizedValue()) : v.getNormalizedValue(),
                    owner && mask && LandRecordSourceValue.MANUAL_CORRECTION.equals(firstWord(v.getTransformation()))
                            ? "MANUAL_CORRECTION" : v.getTransformation(),
                    names.get(v.getChangedBy()), v.getCreatedAt());
        }).toList();
        List<IssueRef> issueRefs = issues.findByLandRecordIdOrderByCreatedAtAsc(id).stream()
                .map(i -> new IssueRef(i.getId(), i.getRuleId(), i.getSeverity().name(), i.getStatus().name(), i.getField(), i.getReason()))
                .toList();
        long open = issueRefs.stream().filter(i -> "OPEN".equals(i.status())).count();
        Provenance prov = new Provenance(mapping == null ? null : mapping.getNormalizationVersion(),
                mapping == null ? null : mapping.getTemplateId(), mapping == null ? null : mapping.getTemplateVersion(),
                mapping == null ? Map.of() : mapping.getMapping(), mapping == null ? Map.of() : mapping.getOptions(),
                r.getSourceRowNumber(), r.getSourcePage());
        return new LandRecordDetail(item(r, open), r.getGeometry(), r.getArchivedReason(), r.getVersion(),
                new SourceFileRef(sf.getId(), sf.getOriginalFilename(), sf.getDetectedType(), sf.getUploadedAt(), sf.getSha256()),
                new ImportJobRef(job.getId(), job.getStatus(), job.getConfirmedAt(), names.get(job.getConfirmedBy())),
                prov, sv, issueRefs, mask);
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public LandRecordDetail update(UUID id, UpdateRequest req) {
        LandRecord r = records.findById(id).orElseThrow(() -> ApiException.notFound("Land record"));
        if (r.getRecordStatus() == LandRecord.Status.ARCHIVED) throw new ApiException(ErrorCode.CONFLICT, "Restore the record before correcting it.");
        if (req.version() != r.getVersion()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "This record was changed by someone else. Reload and try again.");
        }
        List<ApiError.FieldError> bad = new ArrayList<>();
        for (String k : req.changes().keySet()) {
            if (!EDITABLE.contains(k)) bad.add(new ApiError.FieldError("changes." + k, "is not an editable field"));
        }
        if (!bad.isEmpty()) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Only normalized land fields can be corrected; lineage cannot be changed.", bad);

        Map<String, String> before = RecordNormalization.values(r.toNormalized());
        Normalizer.Result res = normalization.run(r, req.changes());
        List<ApiError.FieldError> blocking = res.issues().stream().filter(RowIssue::blocking)
                .map(i -> new ApiError.FieldError(i.field() == null ? "changes" : "changes." + i.field(), i.message())).toList();
        if (!blocking.isEmpty()) throw new ApiException(ErrorCode.VALIDATION_FAILED, "The correction is not valid.", blocking);

        Map<String, String> after = RecordNormalization.values(res.record());
        List<String> changed = new ArrayList<>();
        UUID actor = CurrentUser.id();
        for (String k : EDITABLE) {
            if (!Objects.equals(before.get(k), after.get(k))) {
                changed.add(k);
                Normalizer.FieldTrace t = CanonicalField.byKey(k).map(res.traces()::get).orElse(null);
                sourceValues.save(new LandRecordSourceValue(r.getId(), k, null, emptyToNull(before.get(k)), emptyToNull(after.get(k)),
                        LandRecordSourceValue.MANUAL_CORRECTION + " — " + req.reason().strip()
                                + (t == null ? "" : " (" + t.transformation() + ")"), actor));
            }
        }
        if (changed.isEmpty()) throw ApiException.validation("No values were changed.");
        r.apply(res.record());
        records.saveAndFlush(r);
        quality.persistRowIssues(r, res.issues());
        quality.evaluateDuplicates(List.of(r));
        audit.record("RECORD_CORRECTED", "LAND_RECORD", r.getId(), Map.of("fields", String.join(",", changed), "reasonProvided", true));
        return get(id);
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public LandRecordDetail archive(UUID id, String reason) {
        LandRecord r = records.findById(id).orElseThrow(() -> ApiException.notFound("Land record"));
        if (r.getRecordStatus() == LandRecord.Status.ARCHIVED) throw new ApiException(ErrorCode.CONFLICT, "The record is already archived.");
        r.archive(reason.strip());
        audit.record("RECORD_ARCHIVED", "LAND_RECORD", r.getId(), Map.of());
        return get(id);
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public LandRecordDetail restore(UUID id) {
        LandRecord r = records.findById(id).orElseThrow(() -> ApiException.notFound("Land record"));
        if (r.getRecordStatus() != LandRecord.Status.ARCHIVED) throw new ApiException(ErrorCode.CONFLICT, "The record is not archived.");
        r.restore();
        audit.record("RECORD_RESTORED", "LAND_RECORD", r.getId(), Map.of());
        return get(id);
    }

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public Facets facets() {
        PageRequest limit = PageRequest.of(0, 300);
        return new Facets(records.distinctDistricts(limit), records.distinctStates(limit), records.distinctCategories(limit));
    }

    LandRecordListItem item(LandRecord r, long openIssues) {
        return new LandRecordListItem(r.getId(), r.getParcelId(), r.getSurveyKhasraNo(), masking.apply(r.getOwnerName()),
                r.getVillageLocality(), r.getTehsil(), r.getDistrict(), r.getState(), r.getLandAreaValue(), r.getLandAreaUnitRaw(),
                r.getLandAreaUnit(), r.getLandAreaSqm(), r.getLandTypeRaw(), r.getLandTypeCategory(), r.getRegistrationRef(),
                r.getRegistrationDate(), r.getLatitude(), r.getLongitude(), r.isMappable(), r.getRecordStatus().name(),
                r.getImportJobId(), r.getSourceFileId(), openIssues, r.getCreatedAt());
    }

    Map<UUID, Long> openCounts(Collection<UUID> ids) {
        Map<UUID, Long> m = new HashMap<>();
        if (ids.isEmpty()) return m;
        for (Object[] row : issues.openCountsByRecord(ids)) m.put((UUID) row[0], ((Number) row[1]).longValue());
        return m;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String firstWord(String s) {
        if (s == null) return "";
        int i = s.indexOf(' ');
        return i < 0 ? s : s.substring(0, i);
    }
}
