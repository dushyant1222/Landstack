package edu.iitp.landstack.parsing;

import java.util.LinkedHashMap;
import java.util.Map;

/** A parse/extraction message with an optional row/column reference. */
public record Diagnostic(Level level, String code, String message, Integer row, String column) {
    public enum Level { INFO, WARNING, ERROR }

    public static Diagnostic info(String code, String message) { return new Diagnostic(Level.INFO, code, message, null, null); }
    public static Diagnostic warn(String code, String message) { return new Diagnostic(Level.WARNING, code, message, null, null); }
    public static Diagnostic warn(String code, String message, Integer row, String column) {
        return new Diagnostic(Level.WARNING, code, message, row, column);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level.name());
        m.put("code", code);
        m.put("message", message);
        if (row != null) m.put("row", row);
        if (column != null) m.put("column", column);
        return m;
    }
}
