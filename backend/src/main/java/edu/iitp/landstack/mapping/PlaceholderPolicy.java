package edu.iitp.landstack.mapping;

import java.util.Set;

/** Values treated as "missing" (norm-1.0). The raw value is still kept in lineage. */
public final class PlaceholderPolicy {
    private static final Set<String> PLACEHOLDERS = Set.of("", "-", "--", "---", "n/a", "na", "n.a.", "nil", "null",
            "none", "not available", "unknown", "?", "#n/a", "nan");

    private PlaceholderPolicy() {}

    public static boolean isPlaceholder(String cleaned) {
        return cleaned == null || PLACEHOLDERS.contains(cleaned.toLowerCase());
    }

    /** Collapses runs of whitespace and trims. Does not change case or characters. */
    public static String clean(String raw) {
        if (raw == null) return null;
        return raw.replace(' ', ' ').replaceAll("\\s+", " ").strip();
    }
}
