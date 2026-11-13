package edu.iitp.landstack.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * Loads the versioned dictionaries from classpath:normalization/*.json (area units, land types, states).
 * Matching is exact on a normalized label; there is no fuzzy matching.
 */
@Component
public class NormalizationDictionaries {

    public record Unit(String code, String label, BigDecimal sqmFactor, boolean regional, Map<String, BigDecimal> byState) {}

    /** Resolution of a raw unit label (+ canonical state) to a conversion factor. */
    public record UnitResolution(Unit unit, BigDecimal factor, String problem) {
        public boolean known() { return unit != null; }
        public boolean convertible() { return factor != null; }
    }

    private final Map<String, Unit> unitsByAlias = new HashMap<>();
    private final Map<String, String> landTypeByLabel = new HashMap<>();
    private final Map<String, String> stateByAlias = new HashMap<>();
    private final String unitsVersion;

    public NormalizationDictionaries(ObjectMapper mapper) throws IOException {
        JsonNode units = read(mapper, "normalization/area-units.json");
        unitsVersion = units.path("version").asText();
        for (JsonNode u : units.path("universal")) {
            Unit unit = new Unit(u.get("code").asText(), u.get("label").asText(), new BigDecimal(u.get("sqm").asText()), false, Map.of());
            u.get("aliases").forEach(a -> unitsByAlias.put(key(a.asText()), unit));
            unitsByAlias.put(key(unit.code()), unit);
        }
        for (JsonNode u : units.path("regional")) {
            Map<String, BigDecimal> byState = new HashMap<>();
            u.get("byState").fields().forEachRemaining(e -> byState.put(e.getKey(), new BigDecimal(e.getValue().asText())));
            Unit unit = new Unit(u.get("code").asText(), u.get("code").asText().toLowerCase().replace('_', ' '), null, true, Map.copyOf(byState));
            u.get("aliases").forEach(a -> unitsByAlias.put(key(a.asText()), unit));
            unitsByAlias.put(key(unit.code()), unit);
        }
        JsonNode types = read(mapper, "normalization/land-types.json");
        types.path("categories").fields().forEachRemaining(e ->
                e.getValue().forEach(label -> landTypeByLabel.put(key(label.asText()), e.getKey())));
        JsonNode states = read(mapper, "normalization/states.json");
        states.path("states").fields().forEachRemaining(e -> {
            stateByAlias.put(key(e.getKey()), e.getKey());
            e.getValue().forEach(a -> stateByAlias.put(key(a.asText()), e.getKey()));
        });
    }

    private static JsonNode read(ObjectMapper mapper, String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readTree(in);
        }
    }

    static String key(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[\\s.]+", " ").replaceAll("\\s+", " ").strip();
    }

    public String unitsVersion() {
        return unitsVersion;
    }

    public UnitResolution resolveUnit(String rawUnit, String canonicalState) {
        Unit u = unitsByAlias.get(key(rawUnit));
        if (u == null) {
            // tolerate trailing 's'/punctuation differences such as "Acres." or "Hectares"
            String k = key(rawUnit).replaceAll("[^a-z0-9 ^]", "");
            u = unitsByAlias.get(k);
            if (u == null && k.endsWith("s")) u = unitsByAlias.get(k.substring(0, k.length() - 1));
        }
        if (u == null) return new UnitResolution(null, null, "UNIT_UNKNOWN");
        if (!u.regional()) return new UnitResolution(u, u.sqmFactor(), null);
        if (canonicalState == null) return new UnitResolution(u, null, "REGIONAL_UNIT_NO_STATE");
        BigDecimal f = u.byState().get(canonicalState);
        return f == null ? new UnitResolution(u, null, "REGIONAL_UNIT_NO_FACTOR") : new UnitResolution(u, f, null);
    }

    public Optional<String> landTypeCategory(String raw) {
        return Optional.ofNullable(landTypeByLabel.get(key(raw)));
    }

    public Optional<String> canonicalState(String raw) {
        return Optional.ofNullable(stateByAlias.get(key(raw)));
    }
}
