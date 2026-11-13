package edu.iitp.landstack.parsing;

import java.util.*;

/**
 * Turns physical rows (lists of cell strings) into a header + data rows deterministically.
 * Shared by the CSV and XLSX parsers so both behave identically.
 */
public final class TabularBuilder {
    private TabularBuilder() {}

    public record PhysicalRow(int number, List<String> cells) {}

    public record Limits(int maxRows, int maxColumns, int maxCellLength) {}

    public static TabularResult build(List<PhysicalRow> physical, Integer headerRowOverride, Limits limits,
                                      List<Diagnostic> diagnostics, Map<String, Object> extra) {
        List<PhysicalRow> nonBlank = physical.stream().filter(r -> !isBlank(r.cells())).toList();
        if (nonBlank.isEmpty()) {
            throw new ParseFailure("NO_DATA", "The file contains no data.", false);
        }

        PhysicalRow header;
        boolean certain;
        if (headerRowOverride != null) {
            header = physical.stream().filter(r -> r.number() == headerRowOverride).findFirst()
                    .orElseThrow(() -> new ParseFailure("HEADER_ROW_NOT_FOUND",
                            "Row " + headerRowOverride + " does not exist in this file/sheet.", false));
            if (isBlank(header.cells())) {
                throw new ParseFailure("HEADER_ROW_BLANK", "Row " + headerRowOverride + " is blank and cannot be the header.", false);
            }
            certain = true;
            diagnostics.add(Diagnostic.info("HEADER_ROW_SELECTED", "Header row " + headerRowOverride + " was chosen by the operator."));
        } else {
            header = nonBlank.getFirst();
            certain = looksLikeHeader(header.cells());
            if (!certain) {
                diagnostics.add(new Diagnostic(Diagnostic.Level.WARNING, "HEADER_UNCERTAIN",
                        "Row " + header.number() + " was used as the header, but it contains numbers or blanks. "
                                + "Check the column names and choose a different header row if needed.", header.number(), null));
            }
        }

        int width = header.cells().size();
        for (PhysicalRow r : physical) if (r.number() > header.number()) width = Math.max(width, r.cells().size());
        while (width > 0 && columnIsEmpty(physical, header.number(), width - 1)) width--;
        if (width > limits.maxColumns()) {
            throw new ParseFailure("TOO_MANY_COLUMNS", "The file has " + width + " columns; the maximum is "
                    + limits.maxColumns() + ".", false);
        }

        List<String> names = uniqueHeaders(header.cells(), width, diagnostics, header.number());

        List<TabularResult.Row> rows = new ArrayList<>();
        int blankSkipped = 0;
        int formulaLike = 0;
        for (PhysicalRow r : physical) {
            if (r.number() <= header.number()) continue;
            if (isBlank(r.cells())) { blankSkipped++; continue; }
            if (rows.size() >= limits.maxRows()) {
                throw new ParseFailure("ROW_LIMIT_EXCEEDED", "The file has more than " + limits.maxRows()
                        + " data rows. Split it into smaller files (no rows were imported).", false);
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int c = 0; c < width; c++) {
                String v = c < r.cells().size() ? r.cells().get(c) : "";
                if (v == null) v = "";
                if (v.length() > limits.maxCellLength()) {
                    v = v.substring(0, limits.maxCellLength());
                    diagnostics.add(Diagnostic.warn("CELL_TRUNCATED", "Cell longer than " + limits.maxCellLength()
                            + " characters was truncated.", r.number(), names.get(c)));
                }
                if (!v.isEmpty() && "=+@".indexOf(v.charAt(0)) >= 0) formulaLike++;
                values.put(names.get(c), v);
            }
            rows.add(new TabularResult.Row(r.number(), values, null, null, null));
        }
        if (rows.isEmpty()) {
            throw new ParseFailure("NO_DATA_ROWS", "No data rows were found below the header row "
                    + header.number() + ".", false);
        }
        if (blankSkipped > 0) diagnostics.add(Diagnostic.info("BLANK_ROWS_SKIPPED", blankSkipped + " blank row(s) were skipped."));
        if (formulaLike > 0) diagnostics.add(Diagnostic.info("FORMULA_LIKE_TEXT", formulaLike
                + " cell(s) begin with '=', '+' or '@'. They are kept as plain text and never evaluated."));
        diagnostics.add(Diagnostic.info("PARSED", rows.size() + " data row(s) and " + width + " column(s) read."));
        return new TabularResult(names, header.number(), certain, rows, diagnostics, extra);
    }

    static boolean looksLikeHeader(List<String> cells) {
        int nonBlank = 0, numeric = 0;
        Set<String> seen = new HashSet<>();
        boolean dup = false;
        for (String c : cells) {
            if (c == null || c.isBlank()) continue;
            nonBlank++;
            if (c.strip().matches("[-+]?[0-9.,]+")) numeric++;
            if (!seen.add(c.strip().toLowerCase())) dup = true;
        }
        return nonBlank > 0 && numeric == 0 && nonBlank * 2 >= cells.size() && !dup;
    }

    static List<String> uniqueHeaders(List<String> raw, int width, List<Diagnostic> diagnostics, int headerRow) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (int c = 0; c < width; c++) {
            String name = c < raw.size() && raw.get(c) != null ? raw.get(c).strip().replaceAll("\\s+", " ") : "";
            if (name.isEmpty()) {
                name = "Column " + (c + 1);
                diagnostics.add(Diagnostic.warn("HEADER_BLANK", "Column " + (c + 1) + " has no header; named '" + name + "'.", headerRow, name));
            }
            String key = name.toLowerCase();
            int n = counts.merge(key, 1, Integer::sum);
            if (n > 1) {
                String renamed = name + " (" + n + ")";
                diagnostics.add(Diagnostic.warn("HEADER_DUPLICATE", "Duplicate column name '" + name + "' renamed to '" + renamed + "'.", headerRow, renamed));
                name = renamed;
                counts.merge(renamed.toLowerCase(), 1, Integer::sum);
            }
            out.add(name);
        }
        return out;
    }

    private static boolean columnIsEmpty(List<PhysicalRow> rows, int headerNumber, int col) {
        for (PhysicalRow r : rows) {
            if (r.number() < headerNumber) continue;
            if (col < r.cells().size() && r.cells().get(col) != null && !r.cells().get(col).isBlank()) return false;
        }
        return true;
    }

    static boolean isBlank(List<String> cells) {
        for (String c : cells) if (c != null && !c.isBlank()) return false;
        return true;
    }
}
