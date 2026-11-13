package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.Text;
import edu.iitp.landstack.quality.IssueStatus;
import edu.iitp.landstack.quality.QualityIssue;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared filter for search, map and export so that all three return exactly the same set of records.
 * Built with the JPA Criteria API (parameterized; no string-concatenated SQL).
 *
 * @param status CONFIRMED (default), ARCHIVED or ALL
 */
public record LandRecordFilter(String q, String parcelId, String surveyKhasraNo, String village, String tehsil,
                               String district, String state, String landType, UUID importJobId, UUID sourceFileId,
                               String status, Boolean mappable, Boolean hasOpenIssues,
                               BigDecimal minLat, BigDecimal minLon, BigDecimal maxLat, BigDecimal maxLon) {

    public LandRecordFilter withoutBbox() {
        return new LandRecordFilter(q, parcelId, surveyKhasraNo, village, tehsil, district, state, landType, importJobId,
                sourceFileId, status, mappable, hasOpenIssues, null, null, null, null);
    }

    public LandRecordFilter withMappable(Boolean m) {
        return new LandRecordFilter(q, parcelId, surveyKhasraNo, village, tehsil, district, state, landType, importJobId,
                sourceFileId, status, m, hasOpenIssues, minLat, minLon, maxLat, maxLon);
    }

    /** @param includeOwner whether free-text search may match owner names (not for reviewers) */
    public Specification<LandRecord> toSpec(boolean includeOwner) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            String st = status == null || status.isBlank() ? "CONFIRMED" : status.strip().toUpperCase();
            switch (st) {
                case "CONFIRMED" -> ps.add(cb.equal(root.get("recordStatus"), LandRecord.Status.CONFIRMED));
                case "ARCHIVED" -> ps.add(cb.equal(root.get("recordStatus"), LandRecord.Status.ARCHIVED));
                case "ALL" -> { }
                default -> throw ApiException.field("status", "must be CONFIRMED, ARCHIVED or ALL");
            }
            if (!Text.isBlank(q)) {
                String like = Text.likeContains(q.strip());
                List<Predicate> any = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("parcelId")), like, '\\'),
                        cb.like(cb.lower(root.get("surveyKhasraNo")), like, '\\'),
                        cb.like(cb.lower(root.get("villageLocality")), like, '\\'),
                        cb.like(cb.lower(root.get("tehsil")), like, '\\'),
                        cb.like(cb.lower(root.get("district")), like, '\\'),
                        cb.like(cb.lower(root.get("registrationRef")), like, '\\')));
                if (includeOwner) any.add(cb.like(cb.lower(root.get("ownerName")), like, '\\'));
                ps.add(cb.or(any.toArray(Predicate[]::new)));
            }
            contains(ps, cb, root.get("parcelId"), parcelId);
            contains(ps, cb, root.get("surveyKhasraNo"), surveyKhasraNo);
            contains(ps, cb, root.get("villageLocality"), village);
            contains(ps, cb, root.get("tehsil"), tehsil);
            if (!Text.isBlank(district)) ps.add(cb.equal(cb.lower(root.get("district")), district.strip().toLowerCase()));
            if (!Text.isBlank(state)) ps.add(cb.equal(cb.lower(root.get("state")), state.strip().toLowerCase()));
            if (!Text.isBlank(landType)) {
                if ("UNCATEGORISED".equalsIgnoreCase(landType.strip())) ps.add(cb.isNull(root.get("landTypeCategory")));
                else ps.add(cb.equal(root.get("landTypeCategory"), landType.strip().toUpperCase()));
            }
            if (importJobId != null) ps.add(cb.equal(root.get("importJobId"), importJobId));
            if (sourceFileId != null) ps.add(cb.equal(root.get("sourceFileId"), sourceFileId));
            if (mappable != null) ps.add(cb.equal(root.get("mappable"), mappable));
            if (hasOpenIssues != null) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                var qi = sub.from(QualityIssue.class);
                sub.select(qi.get("landRecordId")).where(cb.equal(qi.get("status"), IssueStatus.OPEN), cb.isNotNull(qi.get("landRecordId")));
                ps.add(hasOpenIssues ? root.get("id").in(sub) : cb.not(root.get("id").in(sub)));
            }
            if (minLat != null || maxLat != null || minLon != null || maxLon != null) {
                if (minLat == null || maxLat == null || minLon == null || maxLon == null) {
                    throw ApiException.field("bbox", "provide minLat, minLon, maxLat and maxLon together");
                }
                ps.add(cb.isTrue(root.get("mappable")));
                ps.add(cb.between(root.get("latitude"), minLat, maxLat));
                if (minLon.compareTo(maxLon) <= 0) ps.add(cb.between(root.get("longitude"), minLon, maxLon));
                else ps.add(cb.or(cb.greaterThanOrEqualTo(root.get("longitude"), minLon), cb.lessThanOrEqualTo(root.get("longitude"), maxLon)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private static void contains(List<Predicate> ps, jakarta.persistence.criteria.CriteriaBuilder cb,
                                 jakarta.persistence.criteria.Path<String> path, String value) {
        if (!Text.isBlank(value)) ps.add(cb.like(cb.lower(path), Text.likeContains(value.strip()), '\\'));
    }
}
