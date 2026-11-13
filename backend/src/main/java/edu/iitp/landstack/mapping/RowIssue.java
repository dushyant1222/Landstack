package edu.iitp.landstack.mapping;

import edu.iitp.landstack.quality.Severity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A preview-stage validation result. {@code blocking} issues prevent the row from being confirmed.
 * {@code observed} is omitted for sensitive fields (owner name).
 */
public record RowIssue(String ruleId, String field, Severity severity, boolean blocking, String message, String observed) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleId", ruleId);
        if (field != null) m.put("field", field);
        m.put("severity", severity.name());
        m.put("blocking", blocking);
        m.put("message", message);
        if (observed != null) m.put("observed", observed);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static RowIssue fromMap(Map<String, Object> m) {
        return new RowIssue((String) m.get("ruleId"), (String) m.get("field"), Severity.valueOf((String) m.get("severity")),
                Boolean.TRUE.equals(m.get("blocking")), (String) m.get("message"), (String) m.get("observed"));
    }
}
