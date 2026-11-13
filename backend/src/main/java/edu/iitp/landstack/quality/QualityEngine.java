package edu.iitp.landstack.quality;

import edu.iitp.landstack.common.Hashing;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.landrecords.LandRecord;
import edu.iitp.landstack.landrecords.LandRecordRepository;
import edu.iitp.landstack.mapping.RowIssue;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Deterministic, explainable rule engine for persisted records. Each issue carries rule ID + version,
 * field, severity, reason and a stable dedup key so re-evaluation never creates duplicate flags.
 * Duplicate candidates are flagged on both records and are never merged.
 */
@Component
public class QualityEngine {
    private static final int MAX_CANDIDATES = 20;

    private final QualityIssueRepository issues;
    private final LandRecordRepository records;
    private final String rulesVersion;
    private final BigDecimal areaTolerance;

    public QualityEngine(QualityIssueRepository issues, LandRecordRepository records, LandStackProperties props) {
        this.issues = issues;
        this.records = records;
        this.rulesVersion = props.quality().rulesVersion();
        this.areaTolerance = props.quality().conflictingAreaTolerance();
    }

    public String rulesVersion() {
        return rulesVersion;
    }

    /** Persists non-blocking normalization findings for a record (blocking ones never reach a record). */
    public int persistRowIssues(LandRecord rec, Collection<RowIssue> rowIssues) {
        int created = 0;
        for (RowIssue i : rowIssues) {
            String observed = "owner_name".equals(i.field()) ? null : i.observed();
            created += create(rec.getId(), rec.getImportJobId(), i.ruleId(), QualityIssue.Source.RULE, category(i.ruleId()),
                    i.field(), i.severity(), observed, i.message(), List.of()) ? 1 : 0;
        }
        return created;
    }

    /** Duplicate-candidate rules against confirmed records from other imports, scoped by village + district. */
    public int evaluateDuplicates(Collection<LandRecord> batch) {
        int created = 0;
        for (LandRecord rec : batch) {
            if (rec.getRecordStatus() != LandRecord.Status.CONFIRMED) continue;
            String village = lower(rec.getVillageLocality());
            String district = lower(rec.getDistrict());
            if (rec.getParcelId() != null) {
                List<LandRecord> dups = records.parcelDuplicates(rec.getParcelId().toLowerCase(), village, district,
                        rec.getId(), rec.getImportJobId(), PageRequest.of(0, MAX_CANDIDATES));
                created += flagDuplicates(rec, dups, "DUP_PARCEL_IN_SCOPE", "parcel_id", rec.getParcelId(), "parcel ID/ULPIN");
            }
            if (rec.getSurveyKhasraNo() != null) {
                List<LandRecord> dups = records.khasraDuplicates(rec.getSurveyKhasraNo().toLowerCase(), village, district,
                        rec.getId(), rec.getImportJobId(), PageRequest.of(0, MAX_CANDIDATES));
                created += flagDuplicates(rec, dups, "DUP_KHASRA_IN_SCOPE", "survey_khasra_no", rec.getSurveyKhasraNo(), "survey/khasra number");
            }
        }
        return created;
    }

    private int flagDuplicates(LandRecord rec, List<LandRecord> dups, String ruleId, String field, String observed, String what) {
        if (dups.isEmpty()) return 0;
        int created = 0;
        String scope = scope(rec);
        List<UUID> ids = dups.stream().map(LandRecord::getId).toList();
        created += create(rec.getId(), rec.getImportJobId(), ruleId, QualityIssue.Source.RULE, "DUPLICATE", field, Severity.WARNING,
                observed, "The same " + what + " appears in " + dups.size() + " other confirmed record(s) in " + scope
                        + ". Possible duplicate for review; records are not merged.", ids) ? 1 : 0;
        for (LandRecord other : dups) {
            created += create(other.getId(), other.getImportJobId(), ruleId, QualityIssue.Source.RULE, "DUPLICATE", field, Severity.WARNING,
                    observed, "The same " + what + " appears in a later import (record " + rec.getId() + ") in " + scope
                            + ". Possible duplicate for review; records are not merged.", List.of(rec.getId())) ? 1 : 0;
            created += compareConflicts(rec, other);
            created += compareConflicts(other, rec); // symmetric, so a later re-run creates nothing new
        }
        return created;
    }

    private int compareConflicts(LandRecord a, LandRecord b) {
        int created = 0;
        if (a.getLandAreaSqm() != null && b.getLandAreaSqm() != null) {
            BigDecimal max = a.getLandAreaSqm().max(b.getLandAreaSqm());
            if (max.signum() > 0) {
                BigDecimal rel = a.getLandAreaSqm().subtract(b.getLandAreaSqm()).abs().divide(max, 6, RoundingMode.HALF_UP);
                if (rel.compareTo(areaTolerance) > 0) {
                    created += create(a.getId(), a.getImportJobId(), "DUP_CONFLICTING_AREA", QualityIssue.Source.RULE, "CONSISTENCY",
                            "land_area_value", Severity.WARNING, a.getLandAreaSqm().stripTrailingZeros().toPlainString() + " m² vs "
                                    + b.getLandAreaSqm().stripTrailingZeros().toPlainString() + " m²",
                            "A likely duplicate record has a different area (difference " + rel.movePointRight(2).setScale(1, RoundingMode.HALF_UP)
                                    + "%, tolerance " + areaTolerance.movePointRight(2).stripTrailingZeros().toPlainString() + "%).",
                            List.of(b.getId())) ? 1 : 0;
                }
            }
        }
        List<String> differing = new ArrayList<>();
        if (differs(a.getOwnerName(), b.getOwnerName())) differing.add("owner name");
        if (differs(a.getLandTypeCategory(), b.getLandTypeCategory())) differing.add("land type");
        if (differs(a.getTehsil(), b.getTehsil())) differing.add("tehsil");
        if (!differing.isEmpty()) {
            created += create(a.getId(), a.getImportJobId(), "DUP_CONFLICTING_FIELDS", QualityIssue.Source.RULE, "CONSISTENCY",
                    String.join(",", differing), Severity.INFO, null,
                    "A likely duplicate record differs in: " + String.join(", ", differing) + ". This may reflect a transfer, "
                            + "a typo or different source vintages; it is not a finding about ownership.", List.of(b.getId())) ? 1 : 0;
        }
        return created;
    }

    /** Creates an issue unless one with the same dedup key exists. Returns true if created. */
    public boolean create(UUID recordId, UUID jobId, String ruleId, QualityIssue.Source source, String category, String field,
                          Severity severity, String observed, String reason, List<UUID> related) {
        return createVersioned(recordId, jobId, ruleId, rulesVersion, source, category, field, severity, observed, reason, related);
    }

    public boolean createVersioned(UUID recordId, UUID jobId, String ruleId, String version, QualityIssue.Source source, String category,
                                   String field, Severity severity, String observed, String reason, List<UUID> related) {
        String key = Hashing.sha256Hex(ruleId + "|" + version + "|" + recordId + "|" + field + "|" + observed
                + "|" + (related == null ? "" : related.stream().map(UUID::toString).sorted().reduce("", String::concat)));
        if (issues.existsByDedupKey(key)) return false;
        String obs = observed == null ? null : observed.length() > 300 ? observed.substring(0, 300) : observed;
        issues.save(new QualityIssue(recordId, jobId, ruleId, version, source, category, field, severity, obs, reason, related, key));
        return true;
    }

    public static String category(String ruleId) {
        if (ruleId.startsWith("ID_") || ruleId.startsWith("LOCALITY_") || ruleId.startsWith("FIELD_")) return "COMPLETENESS";
        if (ruleId.startsWith("AREA_")) return "AREA";
        if (ruleId.startsWith("UNIT_")) return "AREA_UNIT";
        if (ruleId.startsWith("DATE_")) return "DATE";
        if (ruleId.startsWith("COORD_") || ruleId.startsWith("GEOMETRY_")) return "SPATIAL";
        if (ruleId.startsWith("DUP_")) return "DUPLICATE";
        if (ruleId.startsWith("EXTRACTION_")) return "EXTRACTION";
        if (ruleId.startsWith("LAND_TYPE_")) return "CLASSIFICATION";
        if (ruleId.startsWith("STAT_")) return "STATISTICAL";
        return "OTHER";
    }

    private static String scope(LandRecord r) {
        String v = r.getVillageLocality() == null ? "(no village)" : r.getVillageLocality();
        String d = r.getDistrict() == null ? "(no district)" : r.getDistrict();
        return "village/locality '" + v + "', district '" + d + "'";
    }

    private static boolean differs(String a, String b) {
        return a != null && b != null && !a.strip().equalsIgnoreCase(b.strip());
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
