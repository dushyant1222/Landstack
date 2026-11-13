package edu.iitp.landstack.quality;

import java.util.List;

/** Human-readable catalogue of the rules (served to the UI so explanations stay in one place). */
public final class QualityRuleCatalogue {
    private QualityRuleCatalogue() {}

    public record Rule(String ruleId, String category, String severity, String stage, String description) {}

    public static final List<Rule> RULES = List.of(
            new Rule("ID_MISSING", "COMPLETENESS", "HIGH (blocking)", "preview", "No parcel ID/ULPIN and no survey/khasra number."),
            new Rule("LOCALITY_MISSING", "COMPLETENESS", "WARNING (HIGH if profile requires district)", "preview", "No district or village/locality."),
            new Rule("AREA_NEGATIVE", "AREA", "HIGH (blocking)", "preview", "Land area below zero."),
            new Rule("AREA_NOT_NUMERIC", "AREA", "HIGH (blocking)", "preview", "Land area is not a number."),
            new Rule("AREA_EXTREME", "AREA", "WARNING", "preview + re-run", "Area in m² outside the configured plausible range."),
            new Rule("UNIT_MISSING", "AREA_UNIT", "WARNING", "preview", "Area supplied without a unit."),
            new Rule("UNIT_UNKNOWN", "AREA_UNIT", "WARNING", "preview", "Unit not in the conversion table."),
            new Rule("UNIT_NOT_CONVERTIBLE", "AREA_UNIT", "WARNING", "preview", "Regional unit (bigha, katha, kanal…) with no factor for the record's state."),
            new Rule("DATE_INVALID", "DATE", "WARNING", "preview", "Unsupported or impossible date."),
            new Rule("DATE_AMBIGUOUS", "DATE", "WARNING", "preview", "Day/month order cannot be determined."),
            new Rule("COORD_OUT_OF_RANGE", "SPATIAL", "WARNING", "preview", "Latitude outside −90..90 or longitude outside −180..180."),
            new Rule("COORD_PARTIAL", "SPATIAL", "WARNING", "preview", "Only one coordinate component is valid."),
            new Rule("COORD_INVALID", "SPATIAL", "WARNING", "preview", "Coordinate is not a decimal-degree number."),
            new Rule("COORD_ZERO", "SPATIAL", "WARNING", "preview", "0,0 placeholder coordinates."),
            new Rule("GEOMETRY_INVALID", "SPATIAL", "WARNING", "preview", "Supplied GeoJSON failed validation."),
            new Rule("DUP_ROW_IN_FILE", "DUPLICATE", "WARNING", "preview", "Same identifier + locality repeated within one file."),
            new Rule("DUP_IDENTICAL_ROW", "DUPLICATE", "WARNING", "preview", "Row identical to another row in the file."),
            new Rule("DUP_PARCEL_IN_SCOPE", "DUPLICATE", "WARNING", "post-confirm", "Same parcel ID in another import, same village + district."),
            new Rule("DUP_KHASRA_IN_SCOPE", "DUPLICATE", "WARNING", "post-confirm", "Same survey/khasra no. in another import, same village + district."),
            new Rule("DUP_CONFLICTING_AREA", "CONSISTENCY", "WARNING", "post-confirm", "Likely duplicates disagree on area beyond tolerance."),
            new Rule("DUP_CONFLICTING_FIELDS", "CONSISTENCY", "INFO", "post-confirm", "Likely duplicates disagree on owner, land type or tehsil."),
            new Rule("EXTRACTION_LOW_CONFIDENCE", "EXTRACTION", "WARNING", "preview", "PDF-extracted row not yet reviewed by an operator."),
            new Rule("LAND_TYPE_UNMAPPED", "CLASSIFICATION", "INFO", "preview", "Land-type label not in the category dictionary."),
            new Rule("STAT_AREA_IQR", "STATISTICAL", "INFO (advisory)", "on demand", "Area is a statistical outlier among comparable records (log-IQR)."));
}
