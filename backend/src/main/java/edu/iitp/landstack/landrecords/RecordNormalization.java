package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.mapping.CanonicalField;
import edu.iitp.landstack.mapping.ImportMappingRepository;
import edu.iitp.landstack.mapping.MappingConfig;
import edu.iitp.landstack.mapping.NormalizedRecord;
import edu.iitp.landstack.mapping.Normalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Re-runs the one normalization engine over a stored record (identity mapping: column = canonical key).
 * Used for operator corrections and quality re-evaluation, so rules are never duplicated.
 */
@Component
public class RecordNormalization {
    private static final CanonicalField[] EDITABLE = {
            CanonicalField.PARCEL_ID, CanonicalField.SURVEY_KHASRA_NO, CanonicalField.OWNER_NAME, CanonicalField.LAND_AREA_VALUE,
            CanonicalField.LAND_AREA_UNIT, CanonicalField.LAND_TYPE, CanonicalField.REGISTRATION_REF, CanonicalField.REGISTRATION_DATE,
            CanonicalField.VILLAGE_LOCALITY, CanonicalField.TEHSIL, CanonicalField.DISTRICT, CanonicalField.STATE,
            CanonicalField.LATITUDE, CanonicalField.LONGITUDE};
    private static final Map<CanonicalField, String> IDENTITY_COLUMNS = new EnumMap<>(CanonicalField.class);

    static {
        for (CanonicalField f : EDITABLE) IDENTITY_COLUMNS.put(f, f.key());
    }

    private final Normalizer normalizer;
    private final ImportMappingRepository mappings;

    public RecordNormalization(Normalizer normalizer, ImportMappingRepository mappings) {
        this.normalizer = normalizer;
        this.mappings = mappings;
    }

    public static Map<String, String> values(NormalizedRecord n) {
        Map<String, String> v = new HashMap<>();
        put(v, CanonicalField.PARCEL_ID, n.parcelId());
        put(v, CanonicalField.SURVEY_KHASRA_NO, n.surveyKhasraNo());
        put(v, CanonicalField.OWNER_NAME, n.ownerName());
        put(v, CanonicalField.LAND_AREA_VALUE, plain(n.landAreaValue()));
        put(v, CanonicalField.LAND_AREA_UNIT, n.landAreaUnitRaw());
        put(v, CanonicalField.LAND_TYPE, n.landTypeRaw());
        put(v, CanonicalField.REGISTRATION_REF, n.registrationRef());
        put(v, CanonicalField.REGISTRATION_DATE, n.registrationDate() == null ? null : n.registrationDate().toString());
        put(v, CanonicalField.VILLAGE_LOCALITY, n.villageLocality());
        put(v, CanonicalField.TEHSIL, n.tehsil());
        put(v, CanonicalField.DISTRICT, n.district());
        put(v, CanonicalField.STATE, n.state());
        put(v, CanonicalField.LATITUDE, plain(n.latitude()));
        put(v, CanonicalField.LONGITUDE, plain(n.longitude()));
        return v;
    }

    /** Normalizes the record's current values with optional overrides (canonical key → new raw text). */
    public Normalizer.Result run(LandRecord rec, Map<String, String> overrides) {
        Map<String, String> v = values(rec.toNormalized());
        if (overrides != null) v.putAll(overrides);
        // Keep the file-level state declared at import (used for regional unit factors) so corrections
        // and re-evaluation convert exactly as the original import did.
        String declaredState = mappings.findById(rec.getImportJobId())
                .map(m -> (String) m.getOptions().get("declaredState")).orElse(null);
        MappingConfig cfg = new MappingConfig(IDENTITY_COLUMNS, false, null, declaredState, true, false);
        Normalizer.Result r = normalizer.normalize(v, cfg);
        // geometry is not editable; keep the stored one
        NormalizedRecord n = r.record();
        NormalizedRecord withGeom = new NormalizedRecord(n.parcelId(), n.surveyKhasraNo(), n.ownerName(), n.landAreaValue(),
                n.landAreaUnitRaw(), n.landAreaUnit(), n.landAreaSqm(), n.landTypeRaw(), n.landTypeCategory(), n.registrationRef(),
                n.registrationDate(), n.villageLocality(), n.tehsil(), n.district(), n.state(), n.latitude(), n.longitude(), rec.getGeometry());
        return new Normalizer.Result(withGeom, r.traces(), r.issues());
    }

    private static void put(Map<String, String> v, CanonicalField f, String value) {
        v.put(f.key(), value == null ? "" : value);
    }

    private static String plain(BigDecimal d) {
        return d == null ? null : d.toPlainString();
    }
}
