package edu.iitp.landstack.geospatial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Validates source-supplied GeoJSON. Accepts a geometry object or a Feature wrapping one; allowed
 * types are Point, Polygon and MultiPolygon (WGS84 lon/lat order). Never derives or invents geometry.
 */
public final class GeometryValidator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_POSITIONS = 5000;

    public record Result(Map<String, Object> geometry, String problem) {}

    private GeometryValidator() {}

    @SuppressWarnings("unchecked")
    public static Result parse(String raw) {
        JsonNode node;
        try {
            if (raw.length() > 500_000) return new Result(null, "Geometry is too large.");
            node = MAPPER.readTree(raw);
        } catch (Exception e) {
            return new Result(null, "Geometry is not valid JSON.");
        }
        if (node == null || !node.isObject()) return new Result(null, "Geometry must be a GeoJSON object.");
        if ("Feature".equals(node.path("type").asText())) node = node.path("geometry");
        String type = node.path("type").asText();
        JsonNode coords = node.path("coordinates");
        Optional<String> problem = switch (type) {
            case "Point" -> checkPosition(coords);
            case "Polygon" -> checkPolygon(coords, new int[]{0});
            case "MultiPolygon" -> {
                if (!coords.isArray() || coords.isEmpty()) yield Optional.of("MultiPolygon needs at least one polygon.");
                int[] count = {0};
                for (JsonNode poly : coords) {
                    Optional<String> p = checkPolygon(poly, count);
                    if (p.isPresent()) yield p;
                }
                yield Optional.empty();
            }
            default -> Optional.of("Unsupported geometry type '" + type + "' (allowed: Point, Polygon, MultiPolygon).");
        };
        if (problem.isPresent()) return new Result(null, problem.get());
        Map<String, Object> clean = new LinkedHashMap<>();
        clean.put("type", type);
        clean.put("coordinates", MAPPER.convertValue(coords, Object.class));
        return new Result(clean, null);
    }

    private static Optional<String> checkPolygon(JsonNode rings, int[] count) {
        if (!rings.isArray() || rings.isEmpty()) return Optional.of("Polygon needs at least one linear ring.");
        for (JsonNode ring : rings) {
            if (!ring.isArray() || ring.size() < 4) return Optional.of("Each polygon ring needs at least 4 positions.");
            for (JsonNode pos : ring) {
                if (++count[0] > MAX_POSITIONS) return Optional.of("Geometry has more than " + MAX_POSITIONS + " positions.");
                Optional<String> p = checkPosition(pos);
                if (p.isPresent()) return p;
            }
            JsonNode first = ring.get(0), last = ring.get(ring.size() - 1);
            if (first.get(0).asDouble() != last.get(0).asDouble() || first.get(1).asDouble() != last.get(1).asDouble()) {
                return Optional.of("Polygon ring is not closed (first and last positions differ).");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> checkPosition(JsonNode pos) {
        if (!pos.isArray() || pos.size() < 2 || !pos.get(0).isNumber() || !pos.get(1).isNumber()) {
            return Optional.of("Each position must be [longitude, latitude].");
        }
        double lon = pos.get(0).asDouble(), lat = pos.get(1).asDouble();
        if (!CoordinateValidator.validLongitude(lon) || !CoordinateValidator.validLatitude(lat)) {
            return Optional.of("Geometry position out of range (longitude −180..180, latitude −90..90).");
        }
        return Optional.empty();
    }
}
