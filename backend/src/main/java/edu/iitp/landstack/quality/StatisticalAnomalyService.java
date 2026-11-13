package edu.iitp.landstack.quality;

import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.landrecords.LandRecord;
import edu.iitp.landstack.landrecords.LandRecordRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Optional, advisory statistical check (Module 13): flags land areas that are extreme relative to
 * comparable records (same land-type category and district), using Tukey fences on log10(area m²).
 * Deterministic and dependency-free; the Python ML comparison is an offline evaluation only.
 */
@Component
public class StatisticalAnomalyService {
    public static final String RULE_ID = "STAT_AREA_IQR";

    private final LandRecordRepository records;
    private final QualityEngine engine;
    private final int minGroup;
    private final double k;

    public StatisticalAnomalyService(LandRecordRepository records, QualityEngine engine, LandStackProperties props) {
        this.records = records;
        this.engine = engine;
        this.minGroup = props.quality().statisticalMinGroupSize();
        this.k = props.quality().statisticalIqrMultiplier().doubleValue();
    }

    public String version() {
        return "stat-iqr-1.0(k=" + k + ",log10,min=" + minGroup + ")";
    }

    public record RunResult(int recordsConsidered, int groupsEvaluated, int groupsTooSmall, int issuesCreated, String version) {}

    public RunResult run() {
        Map<String, List<LandRecord>> groups = new HashMap<>();
        int considered = 0;
        for (LandRecord r : records.findAll()) {
            if (r.getRecordStatus() != LandRecord.Status.CONFIRMED || r.getLandAreaSqm() == null || r.getLandAreaSqm().signum() <= 0) continue;
            considered++;
            String key = (r.getLandTypeCategory() == null ? "UNCATEGORISED" : r.getLandTypeCategory()) + "|"
                    + (r.getDistrict() == null ? "" : r.getDistrict().strip().toLowerCase());
            groups.computeIfAbsent(key, x -> new ArrayList<>()).add(r);
        }
        int evaluated = 0, small = 0, created = 0;
        for (var e : groups.entrySet()) {
            List<LandRecord> g = e.getValue();
            if (g.size() < minGroup) { small++; continue; }
            evaluated++;
            double[] logs = g.stream().mapToDouble(r -> Math.log10(r.getLandAreaSqm().doubleValue())).sorted().toArray();
            double q1 = quantile(logs, 0.25), q3 = quantile(logs, 0.75), iqr = q3 - q1;
            if (iqr <= 0) continue;
            double lo = q1 - k * iqr, hi = q3 + k * iqr;
            String[] parts = e.getKey().split("\\|", -1);
            for (LandRecord r : g) {
                double v = Math.log10(r.getLandAreaSqm().doubleValue());
                if (v >= lo && v <= hi) continue;
                String reason = "Advisory statistical flag: area " + fmt(r.getLandAreaSqm().doubleValue()) + " m² is unusually "
                        + (v > hi ? "large" : "small") + " compared with " + g.size() + " records of land type " + parts[0]
                        + (parts[1].isEmpty() ? "" : " in the same district") + " (typical range " + fmt(Math.pow(10, lo)) + "–"
                        + fmt(Math.pow(10, hi)) + " m²). Unusual is not wrong: check the value and unit.";
                created += engine.createVersioned(r.getId(), r.getImportJobId(), RULE_ID, version(), QualityIssue.Source.STATISTICAL,
                        "STATISTICAL", "land_area_value", Severity.INFO, fmt(r.getLandAreaSqm().doubleValue()) + " m²", reason, List.of()) ? 1 : 0;
            }
        }
        return new RunResult(considered, evaluated, small, created, version());
    }

    /** Linear-interpolation quantile (type 7) on a sorted array. */
    static double quantile(double[] sorted, double p) {
        double h = (sorted.length - 1) * p;
        int lo = (int) Math.floor(h);
        int hi = (int) Math.ceil(h);
        return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]);
    }

    private static String fmt(double v) {
        return BigDecimal.valueOf(v).setScale(v >= 100 ? 0 : 2, RoundingMode.HALF_UP).toPlainString();
    }
}
