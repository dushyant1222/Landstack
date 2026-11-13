package edu.iitp.landstack.quality;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.common.Paging;
import edu.iitp.landstack.common.Text;
import edu.iitp.landstack.landrecords.LandRecord;
import edu.iitp.landstack.landrecords.LandRecordRepository;
import edu.iitp.landstack.landrecords.OwnerMasking;
import edu.iitp.landstack.landrecords.RecordNormalization;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import edu.iitp.landstack.users.AppUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class QualityService {
    private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "severity", "severity",
            "ruleId", "ruleId", "status", "status");

    private final QualityIssueRepository issues;
    private final LandRecordRepository records;
    private final QualityEngine engine;
    private final StatisticalAnomalyService statistical;
    private final RecordNormalization normalization;
    private final AppUserRepository users;
    private final OwnerMasking masking;
    private final AuditService audit;

    public QualityService(QualityIssueRepository issues, LandRecordRepository records, QualityEngine engine,
                          StatisticalAnomalyService statistical, RecordNormalization normalization, AppUserRepository users,
                          OwnerMasking masking, AuditService audit) {
        this.issues = issues;
        this.records = records;
        this.engine = engine;
        this.statistical = statistical;
        this.normalization = normalization;
        this.users = users;
        this.masking = masking;
        this.audit = audit;
    }

    public record RecordSummary(UUID id, String parcelId, String surveyKhasraNo, String ownerName, String villageLocality,
                                String district, String state, BigDecimal landAreaValue, String landAreaUnitRaw,
                                BigDecimal landAreaSqm, String landTypeRaw, boolean mappable, String recordStatus, UUID importJobId) {}

    public record IssueDto(UUID id, UUID landRecordId, UUID importJobId, String ruleId, String ruleVersion, String source,
                           String category, String field, Severity severity, String observedValue, String reason,
                           List<UUID> relatedRecordIds, IssueStatus status, String reviewerName, String reviewerNote,
                           Instant reviewedAt, Instant createdAt, RecordSummary record) {}

    public record IssueDetailDto(IssueDto issue, List<RecordSummary> relatedRecords) {}

    public record ReviewRequest(IssueStatus status, String note) {}

    public record RunResult(int recordsEvaluated, int issuesCreated, String rulesVersion) {}

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public PageResponse<IssueDto> list(IssueStatus status, Severity severity, String ruleId, String category, String district,
                                       UUID importJobId, UUID landRecordId, QualityIssue.Source source,
                                       int page, int size, String sort) {
        Specification<QualityIssue> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (severity != null) ps.add(cb.equal(root.get("severity"), severity));
            if (!Text.isBlank(ruleId)) ps.add(cb.equal(root.get("ruleId"), ruleId.strip().toUpperCase()));
            if (!Text.isBlank(category)) ps.add(cb.equal(root.get("category"), category.strip().toUpperCase()));
            if (importJobId != null) ps.add(cb.equal(root.get("importJobId"), importJobId));
            if (landRecordId != null) ps.add(cb.equal(root.get("landRecordId"), landRecordId));
            if (source != null) ps.add(cb.equal(root.get("source"), source));
            if (!Text.isBlank(district)) {
                var sub = query.subquery(UUID.class);
                var r = sub.from(LandRecord.class);
                sub.select(r.get("id")).where(cb.equal(cb.lower(r.get("district")), district.strip().toLowerCase()));
                ps.add(root.get("landRecordId").in(sub));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<QualityIssue> result = issues.findAll(spec, Paging.of(page, size, sort, SORTS, "createdAt,desc"));
        Map<UUID, LandRecord> recs = new HashMap<>();
        records.findAllById(result.getContent().stream().map(QualityIssue::getLandRecordId).filter(Objects::nonNull).toList())
                .forEach(r -> recs.put(r.getId(), r));
        Map<UUID, String> reviewers = reviewerNames(result.getContent());
        return PageResponse.of(result, i -> toDto(i, recs.get(i.getLandRecordId()), reviewers));
    }

    @PreAuthorize(Perms.READ)
    @Transactional(readOnly = true)
    public IssueDetailDto get(UUID id) {
        QualityIssue i = issues.findById(id).orElseThrow(() -> ApiException.notFound("Quality issue"));
        LandRecord rec = i.getLandRecordId() == null ? null : records.findById(i.getLandRecordId()).orElse(null);
        List<RecordSummary> related = records.findAllById(i.getRelatedRecordIds()).stream().map(this::summary).toList();
        return new IssueDetailDto(toDto(i, rec, reviewerNames(List.of(i))), related);
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public IssueDto review(UUID id, ReviewRequest req) {
        if (req == null || req.status() == null) throw ApiException.field("status", "is required");
        String note = Text.trimToNull(req.note());
        if (note != null && note.length() > 1000) throw ApiException.field("note", "must be at most 1000 characters");
        if ((req.status() == IssueStatus.RESOLVED || req.status() == IssueStatus.DISMISSED) && note == null) {
            throw ApiException.field("note", "explain why the issue is " + req.status().name().toLowerCase());
        }
        QualityIssue i = issues.findById(id).orElseThrow(() -> ApiException.notFound("Quality issue"));
        IssueStatus previous = i.getStatus();
        if (previous == req.status() && Objects.equals(note, i.getReviewerNote())) {
            throw new ApiException(ErrorCode.CONFLICT, "The issue already has this status.");
        }
        i.review(req.status(), note, CurrentUser.id());
        audit.record("ISSUE_" + req.status().name(), "QUALITY_ISSUE", i.getId(),
                Map.of("from", previous.name(), "to", req.status().name(), "ruleId", i.getRuleId(), "noteProvided", note != null));
        LandRecord rec = i.getLandRecordId() == null ? null : records.findById(i.getLandRecordId()).orElse(null);
        return toDto(i, rec, reviewerNames(List.of(i)));
    }

    /** Re-evaluates rules for all confirmed records (or one import). Dedup keys prevent repeated flags. */
    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public RunResult rerun(UUID importJobId) {
        int evaluated = 0, created = 0, page = 0;
        while (true) {
            Page<LandRecord> batch = importJobId == null
                    ? records.findAll((root, q, cb) -> cb.equal(root.get("recordStatus"), LandRecord.Status.CONFIRMED),
                    PageRequest.of(page++, 500, Sort.by("id")))
                    : records.findAll((root, q, cb) -> cb.and(cb.equal(root.get("recordStatus"), LandRecord.Status.CONFIRMED),
                    cb.equal(root.get("importJobId"), importJobId)), PageRequest.of(page++, 500, Sort.by("id")));
            for (LandRecord r : batch) {
                created += engine.persistRowIssues(r, normalization.run(r, null).issues());
            }
            created += engine.evaluateDuplicates(batch.getContent());
            evaluated += batch.getNumberOfElements();
            if (!batch.hasNext()) break;
        }
        audit.record("QUALITY_RUN", "QUALITY", importJobId, Map.of("records", evaluated, "issuesCreated", created,
                "rulesVersion", engine.rulesVersion()));
        return new RunResult(evaluated, created, engine.rulesVersion());
    }

    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public StatisticalAnomalyService.RunResult statisticalRun() {
        StatisticalAnomalyService.RunResult r = statistical.run();
        audit.record("STATISTICAL_RUN", "QUALITY", null, Map.of("records", r.recordsConsidered(), "groups", r.groupsEvaluated(),
                "issuesCreated", r.issuesCreated(), "version", r.version()));
        return r;
    }

    private IssueDto toDto(QualityIssue i, LandRecord rec, Map<UUID, String> reviewers) {
        return new IssueDto(i.getId(), i.getLandRecordId(), i.getImportJobId(), i.getRuleId(), i.getRuleVersion(), i.getSource().name(),
                i.getCategory(), i.getField(), i.getSeverity(), i.getObservedValue(), i.getReason(), i.getRelatedRecordIds(),
                i.getStatus(), reviewers.get(i.getReviewerId()), i.getReviewerNote(), i.getReviewedAt(), i.getCreatedAt(),
                rec == null ? null : summary(rec));
    }

    private RecordSummary summary(LandRecord r) {
        return new RecordSummary(r.getId(), r.getParcelId(), r.getSurveyKhasraNo(), masking.apply(r.getOwnerName()),
                r.getVillageLocality(), r.getDistrict(), r.getState(), r.getLandAreaValue(), r.getLandAreaUnitRaw(),
                r.getLandAreaSqm(), r.getLandTypeRaw(), r.isMappable(), r.getRecordStatus().name(), r.getImportJobId());
    }

    private Map<UUID, String> reviewerNames(List<QualityIssue> list) {
        Map<UUID, String> m = new HashMap<>();
        users.findAllById(list.stream().map(QualityIssue::getReviewerId).filter(Objects::nonNull).toList())
                .forEach(u -> m.put(u.getId(), u.getDisplayName()));
        return m;
    }
}
