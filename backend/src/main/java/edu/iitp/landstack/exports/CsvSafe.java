package edu.iitp.landstack.exports;

import java.util.regex.Pattern;

/**
 * CSV/formula-injection mitigation (OWASP): any text cell starting with = + - @ TAB or CR is prefixed
 * with a single quote so spreadsheet software treats it as text. Plain numbers (e.g. -73.5) are left intact.
 * Quoting of commas, quotes and newlines is handled by the CSV printer (QuoteMode.ALL).
 */
public final class CsvSafe {
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private CsvSafe() {}

    public static String cell(String v) {
        if (v == null || v.isEmpty()) return v == null ? "" : v;
        char c = v.charAt(0);
        if ((c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') && !PLAIN_NUMBER.matcher(v).matches()) {
            return "'" + v;
        }
        return v;
    }
}
