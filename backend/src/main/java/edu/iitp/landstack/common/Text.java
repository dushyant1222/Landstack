package edu.iitp.landstack.common;

/** Small text helpers shared by services. */
public final class Text {
    private Text() {}

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    /** Escapes LIKE wildcards so user search input is matched literally. */
    public static String likeContains(String term) {
        String escaped = term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }
}
