package edu.iitp.landstack.mapping;

import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.geospatial.CoordinateValidator;
import edu.iitp.landstack.geospatial.GeometryValidator;
import edu.iitp.landstack.quality.Severity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single, deterministic normalization engine (norm-1.0). Same input + same mapping → same output.
 * It never fabricates values: missing stays null, and every transformation is recorded.
 * React only displays the results of this class.
 */
@Component
public class Normalizer {
    private static final Pattern NUMBER = Pattern.compile("^[-+]?(\\d{1,3}(,\\d{2,3})+|\\d+)?(\\.\\d+)?$");
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile("^([-+]?[\\d,]*\\.?\\d+)\\s*([^\\d].*)$");
    private static final Pattern HEMISPHERE = Pattern.compile("^([-+]?\\d+(?:\\.\\d+)?)\\s*°?\\s*([NSEWnsew])$");

    private final NormalizationDictionaries dict;
    private final BigDecimal areaMin;
    private final BigDecimal areaMax;

    public Normalizer(NormalizationDictionaries dict, LandStackProperties props) {
        this.dict = dict;
        this.areaMin = props.quality().areaMinSqm();
        this.areaMax = props.quality().areaMaxSqm();
    }

    /** One canonical field's lineage: where it came from, the raw text, the normalized value and how. */
    public record FieldTrace(String sourceColumn, String raw, String normalized, String transformation) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("column", sourceColumn);
            m.put("raw", raw);
            m.put("value", normalized);
            m.put("transformation", transformation);
            return m;
        }
    }

    public record Result(NormalizedRecord record, Map<CanonicalField, FieldTrace> traces, List<RowIssue> issues) {
        public boolean blocked() { return issues.stream().anyMatch(RowIssue::blocking); }
        public boolean hasWarnings() { return issues.stream().anyMatch(i -> i.severity() != Severity.INFO); }

        public Map<String, Object> tracesMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            traces.forEach((f, t) -> m.put(f.key(), t.toMap()));
            return m;
        }
    }

    public Result normalize(Map<String, String> values, MappingConfig cfg) {
        Map<CanonicalField, FieldTrace> traces = new EnumMap<>(CanonicalField.class);
        List<RowIssue> issues = new ArrayList<>();

        String parcel = text(CanonicalField.PARCEL_ID, values, cfg, traces);
        String khasra = text(CanonicalField.SURVEY_KHASRA_NO, values, cfg, traces);
        String owner = text(CanonicalField.OWNER_NAME, values, cfg, traces);
        String regRef = text(CanonicalField.REGISTRATION_REF, values, cfg, traces);
        String village = text(CanonicalField.VILLAGE_LOCALITY, values, cfg, traces);
        String tehsil = text(CanonicalField.TEHSIL, values, cfg, traces);
        String district = text(CanonicalField.DISTRICT, values, cfg, traces);
        String state = text(CanonicalField.STATE, values, cfg, traces);

        // ---- area value and unit ----
        String areaRaw = raw(CanonicalField.LAND_AREA_VALUE, values, cfg);
        String unitRaw = raw(CanonicalField.LAND_AREA_UNIT, values, cfg);
        String areaClean = PlaceholderPolicy.clean(areaRaw);
        String unitClean = PlaceholderPolicy.clean(unitRaw);
        String unitSource = "column";
        if (PlaceholderPolicy.isPlaceholder(unitClean)) unitClean = null;
        BigDecimal area = null;
        String areaTransform = "none";
        if (areaClean != null && !PlaceholderPolicy.isPlaceholder(areaClean)) {
            String numberPart = areaClean;
            Matcher mu = NUMBER_WITH_UNIT.matcher(areaClean);
            if (!NUMBER.matcher(areaClean).matches() && mu.matches()) {
                numberPart = mu.group(1);
                if (unitClean == null) {
                    unitClean = mu.group(2).strip();
                    unitSource = "area value";
                    areaTransform = "unit text split from value";
                }
            }
            if (NUMBER.matcher(numberPart).matches() && numberPart.chars().anyMatch(Character::isDigit)) {
                BigDecimal v = new BigDecimal(numberPart.replace(",", "").replace("+", ""));
                if (numberPart.contains(",")) areaTransform = join(areaTransform, "thousands separators removed");
                if (v.signum() < 0) {
                    issues.add(new RowIssue("AREA_NEGATIVE", "land_area_value", Severity.HIGH, true,
                            "Land area is negative. Correct the value or exclude the row.", areaClean));
                } else {
                    if (v.scale() > 4) {
                        v = v.setScale(4, RoundingMode.HALF_UP);
                        areaTransform = join(areaTransform, "rounded to 4 decimal places");
                    }
                    area = v;
                }
            } else {
                issues.add(new RowIssue("AREA_NOT_NUMERIC", "land_area_value", Severity.HIGH, true,
                        "Land area is not a number. Correct the value or exclude the row.", areaClean));
            }
        }
        if (areaRaw != null || cfg.columns().containsKey(CanonicalField.LAND_AREA_VALUE)) {
            traces.put(CanonicalField.LAND_AREA_VALUE, new FieldTrace(cfg.columns().get(CanonicalField.LAND_AREA_VALUE),
                    areaRaw, area == null ? null : area.toPlainString(), areaClean == null || PlaceholderPolicy.isPlaceholder(areaClean)
                    ? (areaRaw == null ? "not mapped" : "missing/placeholder → null") : areaTransform));
        }
        if (unitClean == null && cfg.declaredAreaUnit() != null && area != null) {
            unitClean = cfg.declaredAreaUnit();
            unitSource = "declared by operator";
        }
        String canonicalState = state == null ? (cfg.declaredState() == null ? null : dict.canonicalState(cfg.declaredState()).orElse(null))
                : dict.canonicalState(state).orElse(null);
        String unitCode = null;
        BigDecimal sqm = null;
        String unitTransform = "unit from " + unitSource;
        if (unitClean != null) {
            var res = dict.resolveUnit(unitClean, canonicalState);
            if (res.known()) unitCode = res.unit().code();
            if (res.convertible() && area != null) {
                sqm = area.multiply(res.factor()).setScale(4, RoundingMode.HALF_UP);
                unitTransform = join(unitTransform, "converted ×" + res.factor().stripTrailingZeros().toPlainString() + " to m²"
                        + (res.unit().regional() ? " (" + canonicalState + " customary factor)" : ""));
            }
            if (!res.known()) {
                issues.add(new RowIssue("UNIT_UNKNOWN", "land_area_unit", Severity.WARNING, false,
                        "Area unit '" + unitClean + "' is not in the conversion table; the area is kept as supplied without conversion.", unitClean));
            } else if (!res.convertible()) {
                String why = "REGIONAL_UNIT_NO_STATE".equals(res.problem())
                        ? "it is a regional unit whose size varies by state, and no recognised state is available for this row"
                        : "it is a regional unit with no configured factor for " + canonicalState
                        + (res.unit().code().equals("BIGHA") && "Rajasthan".equals(canonicalState) ? " (specify pucca or kaccha bigha)" : "");
                issues.add(new RowIssue("UNIT_NOT_CONVERTIBLE", "land_area_unit", Severity.WARNING, false,
                        "Area in '" + unitClean + "' was not converted to m² because " + why + ".", unitClean));
            }
        } else if (area != null) {
            issues.add(new RowIssue("UNIT_MISSING", "land_area_unit", Severity.WARNING, false,
                    "Area has no unit; it cannot be compared or converted.", null));
        }
        if (unitRaw != null || unitClean != null || cfg.columns().containsKey(CanonicalField.LAND_AREA_UNIT)) {
            traces.put(CanonicalField.LAND_AREA_UNIT, new FieldTrace(cfg.columns().get(CanonicalField.LAND_AREA_UNIT), unitRaw,
                    unitCode == null ? unitClean : unitCode, unitClean == null ? "missing → null" : unitTransform));
        }
        if (sqm != null && (sqm.compareTo(areaMin) < 0 || sqm.compareTo(areaMax) > 0)) {
            issues.add(new RowIssue("AREA_EXTREME", "land_area_value", Severity.WARNING, false,
                    "Area of " + sqm.stripTrailingZeros().toPlainString() + " m² is outside the configured plausible range ("
                            + areaMin.toPlainString() + "–" + areaMax.toPlainString() + " m²). This is a prompt to check, not a finding.",
                    area == null ? null : area.toPlainString()));
        }

        // ---- land type ----
        String landTypeRaw = text(CanonicalField.LAND_TYPE, values, cfg, traces);
        String category = null;
        if (landTypeRaw != null) {
            category = dict.landTypeCategory(landTypeRaw).orElse(null);
            FieldTrace t = traces.get(CanonicalField.LAND_TYPE);
            traces.put(CanonicalField.LAND_TYPE, new FieldTrace(t.sourceColumn(), t.raw(), landTypeRaw,
                    category == null ? join(t.transformation(), "no category match") : join(t.transformation(), "category " + category)));
            if (category == null) {
                issues.add(new RowIssue("LAND_TYPE_UNMAPPED", "land_type", Severity.INFO, false,
                        "Land type '" + landTypeRaw + "' is not in the category dictionary; the original label is kept.", landTypeRaw));
            }
        }

        // ---- date ----
        LocalDate regDate = null;
        String dateRaw = raw(CanonicalField.REGISTRATION_DATE, values, cfg);
        String dateClean = PlaceholderPolicy.clean(dateRaw);
        if (dateClean != null && !PlaceholderPolicy.isPlaceholder(dateClean)) {
            DateParser.Result r = DateParser.parse(dateClean, cfg.dayFirst());
            regDate = r.date();
            switch (r.problem()) {
                case NONE -> { }
                case AMBIGUOUS -> issues.add(new RowIssue("DATE_AMBIGUOUS", "registration_date", Severity.WARNING, false,
                        "Date '" + dateClean + "' could be day-first or month-first. Declare the file day-first or correct it; left empty.", dateClean));
                case MONTH_FIRST -> issues.add(new RowIssue("DATE_INVALID", "registration_date", Severity.WARNING, false,
                        "Date '" + dateClean + "' looks month-first, which is not a supported format; left empty.", dateClean));
                case TWO_DIGIT_YEAR -> issues.add(new RowIssue("DATE_INVALID", "registration_date", Severity.WARNING, false,
                        "Date '" + dateClean + "' has a two-digit year; use a four-digit year. Left empty.", dateClean));
                case INVALID -> issues.add(new RowIssue("DATE_INVALID", "registration_date", Severity.WARNING, false,
                        "Date '" + dateClean + "' is not a valid date in a supported format (yyyy-MM-dd, dd-MM-yyyy, dd/MM/yyyy, dd.MM.yyyy); left empty.", dateClean));
            }
        }
        if (dateRaw != null || cfg.columns().containsKey(CanonicalField.REGISTRATION_DATE)) {
            traces.put(CanonicalField.REGISTRATION_DATE, new FieldTrace(cfg.columns().get(CanonicalField.REGISTRATION_DATE), dateRaw,
                    regDate == null ? null : regDate.toString(), regDate == null ? "missing/invalid → null" : "parsed to ISO date"));
        }

        // ---- coordinates ----
        BigDecimal lat = coordinate(CanonicalField.LATITUDE, values, cfg, traces, issues);
        BigDecimal lon = coordinate(CanonicalField.LONGITUDE, values, cfg, traces, issues);
        if (lat != null && !CoordinateValidator.validLatitude(lat)) {
            issues.add(new RowIssue("COORD_OUT_OF_RANGE", "latitude", Severity.WARNING, false,
                    "Latitude " + lat.toPlainString() + " is outside −90..90; the record will not be mapped.", lat.toPlainString()));
            lat = null;
        }
        if (lon != null && !CoordinateValidator.validLongitude(lon)) {
            issues.add(new RowIssue("COORD_OUT_OF_RANGE", "longitude", Severity.WARNING, false,
                    "Longitude " + lon.toPlainString() + " is outside −180..180; the record will not be mapped.", lon.toPlainString()));
            lon = null;
        }
        if (lat != null && lon != null && lat.signum() == 0 && lon.signum() == 0) {
            issues.add(new RowIssue("COORD_ZERO", "latitude", Severity.WARNING, false,
                    "Coordinates are 0,0, which is almost always a placeholder; the record will not be mapped.", "0,0"));
            lat = null;
            lon = null;
        }
        if ((lat == null) != (lon == null)) {
            issues.add(new RowIssue("COORD_PARTIAL", lat == null ? "latitude" : "longitude", Severity.WARNING, false,
                    "Only one coordinate component is valid; the record will not be mapped.", null));
            lat = null;
            lon = null;
        }
        updateCoordTrace(traces, CanonicalField.LATITUDE, lat);
        updateCoordTrace(traces, CanonicalField.LONGITUDE, lon);

        // ---- geometry ----
        Map<String, Object> geometry = null;
        String geomRaw = raw(CanonicalField.GEOMETRY, values, cfg);
        String geomClean = geomRaw == null ? null : geomRaw.strip();
        if (geomClean != null && !PlaceholderPolicy.isPlaceholder(geomClean)) {
            GeometryValidator.Result g = GeometryValidator.parse(geomClean);
            geometry = g.geometry();
            if (g.problem() != null) {
                issues.add(new RowIssue("GEOMETRY_INVALID", "geometry", Severity.WARNING, false, g.problem() + " Geometry ignored.", null));
            }
        }
        if (geomRaw != null) {
            traces.put(CanonicalField.GEOMETRY, new FieldTrace(cfg.columns().get(CanonicalField.GEOMETRY),
                    geomRaw.length() > 200 ? geomRaw.substring(0, 200) + "…" : geomRaw,
                    geometry == null ? null : (String) geometry.get("type"), geometry == null ? "invalid/missing → null" : "validated GeoJSON"));
        }

        // ---- dataset profile ----
        if (cfg.requireIdentifier() && parcel == null && khasra == null) {
            issues.add(new RowIssue("ID_MISSING", "parcel_id", Severity.HIGH, true,
                    "Neither a parcel ID/ULPIN nor a survey/khasra number is present; this dataset profile requires one.", null));
        }
        if (district == null && village == null) {
            issues.add(new RowIssue("LOCALITY_MISSING", "district", cfg.requireDistrict() ? Severity.HIGH : Severity.WARNING,
                    cfg.requireDistrict(), "No district or village/locality is present; duplicates cannot be scoped to a locality.", null));
        } else if (district == null && cfg.requireDistrict()) {
            issues.add(new RowIssue("LOCALITY_MISSING", "district", Severity.HIGH, true, "District is required by this dataset profile.", null));
        }

        NormalizedRecord rec = new NormalizedRecord(parcel, khasra, owner, area, unitClean, unitCode, sqm, landTypeRaw, category,
                regRef, regDate, village, tehsil, district, state, lat, lon, geometry);
        return new Result(rec, traces, issues);
    }

    private static String raw(CanonicalField f, Map<String, String> values, MappingConfig cfg) {
        String col = cfg.columns().get(f);
        return col == null ? null : values.get(col);
    }

    /** Whitespace-normalized text; placeholders become null. Case and characters are preserved. */
    private static String text(CanonicalField f, Map<String, String> values, MappingConfig cfg, Map<CanonicalField, FieldTrace> traces) {
        String col = cfg.columns().get(f);
        if (col == null) return null;
        String raw = values.get(col);
        String clean = PlaceholderPolicy.clean(raw);
        String out = PlaceholderPolicy.isPlaceholder(clean) ? null : clean;
        String transformation;
        if (raw == null || raw.isEmpty()) transformation = "empty → null";
        else if (out == null) transformation = "placeholder '" + clean + "' → null";
        else transformation = out.equals(raw) ? "none" : "whitespace normalized";
        traces.put(f, new FieldTrace(col, raw, out, transformation));
        return out;
    }

    private static BigDecimal coordinate(CanonicalField f, Map<String, String> values, MappingConfig cfg,
                                         Map<CanonicalField, FieldTrace> traces, List<RowIssue> issues) {
        String col = cfg.columns().get(f);
        if (col == null) return null;
        String raw = values.get(col);
        String clean = PlaceholderPolicy.clean(raw);
        if (PlaceholderPolicy.isPlaceholder(clean)) {
            traces.put(f, new FieldTrace(col, raw, null, "missing → null"));
            return null;
        }
        String transformation = "parsed decimal degrees";
        String num = clean;
        Matcher h = HEMISPHERE.matcher(clean);
        if (h.matches()) {
            char hemi = Character.toUpperCase(h.group(2).charAt(0));
            boolean latHemi = hemi == 'N' || hemi == 'S';
            if ((f == CanonicalField.LATITUDE) != latHemi) {
                issues.add(new RowIssue("COORD_INVALID", f.key(), Severity.WARNING, false,
                        f.label() + " '" + clean + "' has the wrong hemisphere letter; the record will not be mapped.", clean));
                traces.put(f, new FieldTrace(col, raw, null, "invalid → null"));
                return null;
            }
            num = (hemi == 'S' || hemi == 'W' ? "-" : "") + h.group(1).replace("+", "").replace("-", "");
            transformation = "hemisphere letter " + hemi + " applied";
        }
        BigDecimal v;
        try {
            v = new BigDecimal(num);
        } catch (NumberFormatException e) {
            issues.add(new RowIssue("COORD_INVALID", f.key(), Severity.WARNING, false,
                    f.label() + " '" + clean + "' is not a decimal-degree number; the record will not be mapped.", clean));
            traces.put(f, new FieldTrace(col, raw, null, "invalid → null"));
            return null;
        }
        if (v.scale() > 6) {
            v = v.setScale(6, RoundingMode.HALF_UP);
            transformation = join(transformation, "rounded to 6 decimal places");
        }
        traces.put(f, new FieldTrace(col, raw, v.toPlainString(), transformation));
        return v;
    }

    private static void updateCoordTrace(Map<CanonicalField, FieldTrace> traces, CanonicalField f, BigDecimal finalValue) {
        FieldTrace t = traces.get(f);
        if (t != null && t.normalized() != null && finalValue == null) {
            traces.put(f, new FieldTrace(t.sourceColumn(), t.raw(), null, join(t.transformation(), "rejected → null")));
        }
    }

    private static String join(String a, String b) {
        return a == null || a.equals("none") ? b : a + "; " + b;
    }
}
