package edu.iitp.landstack.parsing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Parser output: ordered source columns, staged rows and diagnostics. */
public record TabularResult(List<String> headers, int headerRow, boolean headerCertain, List<Row> rows,
                            List<Diagnostic> diagnostics, Map<String, Object> extra) {

    /** @param rowNumber 1-based physical row/record number in the source (or synthetic sequence for PDF) */
    public record Row(int rowNumber, Map<String, String> values, Integer page, String method, BigDecimal confidence) {}
}
