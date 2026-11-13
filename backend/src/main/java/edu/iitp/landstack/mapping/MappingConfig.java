package edu.iitp.landstack.mapping;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapping canonical field → source column, plus operator-declared options.
 *
 * @param dayFirst           operator declares ambiguous dd/MM vs MM/dd dates are day-first
 * @param declaredAreaUnit   unit declared by the operator for the whole file (used only when a row has none)
 * @param declaredState      state declared for the whole file (used only to look up regional unit factors)
 * @param requireIdentifier  profile: a parcel ID or survey/khasra number is required (blocking)
 * @param requireDistrict    profile: district is required (blocking)
 */
public record MappingConfig(Map<CanonicalField, String> columns, boolean dayFirst, String declaredAreaUnit,
                            String declaredState, boolean requireIdentifier, boolean requireDistrict) {

    public static final String NORMALIZATION_VERSION = "norm-1.0";

    public MappingConfig {
        EnumMap<CanonicalField, String> copy = new EnumMap<>(CanonicalField.class);
        copy.putAll(columns);
        columns = Collections.unmodifiableMap(copy);
    }

    public Map<String, String> columnsByKey() {
        Map<String, String> m = new LinkedHashMap<>();
        columns.forEach((f, c) -> m.put(f.key(), c));
        return m;
    }

    public Map<String, Object> optionsMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dayFirst", dayFirst);
        if (declaredAreaUnit != null) m.put("declaredAreaUnit", declaredAreaUnit);
        if (declaredState != null) m.put("declaredState", declaredState);
        m.put("requireIdentifier", requireIdentifier);
        m.put("requireDistrict", requireDistrict);
        return m;
    }

    public static MappingConfig fromStored(Map<String, String> byKey, Map<String, Object> options) {
        Map<CanonicalField, String> cols = new EnumMap<>(CanonicalField.class);
        byKey.forEach((k, v) -> CanonicalField.byKey(k).ifPresent(f -> cols.put(f, v)));
        return new MappingConfig(cols,
                Boolean.TRUE.equals(options.get("dayFirst")),
                (String) options.get("declaredAreaUnit"),
                (String) options.get("declaredState"),
                !Boolean.FALSE.equals(options.get("requireIdentifier")),
                Boolean.TRUE.equals(options.get("requireDistrict")));
    }
}
