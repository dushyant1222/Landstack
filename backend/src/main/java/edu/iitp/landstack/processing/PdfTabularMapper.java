package edu.iitp.landstack.processing;

import edu.iitp.landstack.parsing.Diagnostic;
import edu.iitp.landstack.parsing.ParseFailure;
import edu.iitp.landstack.parsing.TabularBuilder;
import edu.iitp.landstack.parsing.TabularResult;

import java.math.BigDecimal;
import java.util.*;

/**
 * Converts the extraction response into staged rows. Nothing is inferred beyond what the service
 * returned: empty output is a failure, scanned PDFs are reported as OCR-required.
 */
public final class PdfTabularMapper {
    private static final BigDecimal TABLE_CONFIDENCE = new BigDecimal("0.800");
    private static final int PAGE_TEXT_LIMIT = 5000;

    private PdfTabularMapper() {}

    public static TabularResult toTabular(PdfExtraction x, TabularBuilder.Limits limits) {
        switch (x.status() == null ? "" : x.status()) {
            case "ENCRYPTED" -> throw new ParseFailure("PDF_ENCRYPTED", "The PDF is password-protected. Upload an unprotected copy.", false);
            case "CORRUPT" -> throw new ParseFailure("PDF_UNREADABLE", "The PDF is corrupt or could not be opened.", false);
            case "TOO_MANY_PAGES" -> throw new ParseFailure("PDF_TOO_MANY_PAGES", "The PDF has more pages than the extraction limit allows. Split the document.", false);
            case "LIKELY_SCANNED" -> throw new ParseFailure("PDF_OCR_REQUIRED", "This PDF appears to be scanned (no extractable text). "
                    + "OCR is not supported in this version; provide a text-based PDF, CSV or Excel file.", false);
            case "OK" -> { }
            default -> throw new ParseFailure("PROCESSING_CONTRACT_MISMATCH", "The processing service returned an unknown status.", true);
        }
        List<Diagnostic> diags = new ArrayList<>();
        diags.add(Diagnostic.info("PDF_PAGES", x.pageCount() + " page(s) processed by " + x.processor() + "."));
        diags.add(Diagnostic.warn("PDF_REVIEW_REQUIRED", "Values extracted from PDF are machine-extracted and unverified. "
                + "Review every value before confirming."));
        if (x.pages() != null) {
            long scanned = x.pages().stream().filter(PdfExtraction.Page::likelyScanned).count();
            if (scanned > 0) diags.add(Diagnostic.warn("PDF_PARTLY_SCANNED", scanned + " page(s) had no extractable text and were skipped (OCR not supported)."));
        }
        if (x.warnings() != null) x.warnings().forEach(w -> diags.add(Diagnostic.warn("PDF_EXTRACTOR", w)));

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("pageCount", x.pageCount());
        extra.put("processor", x.processor());
        List<Map<String, Object>> pages = new ArrayList<>();
        if (x.pages() != null) for (PdfExtraction.Page p : x.pages()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("page", p.page());
            m.put("charCount", p.charCount());
            m.put("likelyScanned", p.likelyScanned());
            String t = p.text() == null ? "" : p.text();
            m.put("text", t.length() > PAGE_TEXT_LIMIT ? t.substring(0, PAGE_TEXT_LIMIT) : t);
            m.put("truncated", t.length() > PAGE_TEXT_LIMIT);
            pages.add(m);
        }
        extra.put("pages", pages);

        if (x.tables() != null && !x.tables().isEmpty()) return fromTables(x.tables(), limits, diags, extra);
        if (x.candidates() != null && !x.candidates().isEmpty()) return fromCandidates(x.candidates(), limits, diags, extra);
        throw new ParseFailure("PDF_NO_STRUCTURED_DATA", "Text was extracted, but no table or 'Label: value' fields were recognised. "
                + "This layout is not supported; enter the data in CSV/Excel instead.", false);
    }

    private static TabularResult fromTables(List<PdfExtraction.Table> tables, TabularBuilder.Limits limits,
                                            List<Diagnostic> diags, Map<String, Object> extra) {
        Map<String, List<PdfExtraction.Table>> groups = new LinkedHashMap<>();
        for (PdfExtraction.Table t : tables) {
            if (t.header() == null || t.header().isEmpty()) continue;
            String key = String.join("|", t.header().stream().map(h -> h == null ? "" : h.strip().toLowerCase()).toList());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        if (groups.isEmpty()) throw new ParseFailure("PDF_NO_STRUCTURED_DATA", "Tables were found but none had a header row.", false);
        List<PdfExtraction.Table> best = groups.values().stream()
                .max(Comparator.comparingInt(g -> g.stream().mapToInt(t -> t.rows() == null ? 0 : t.rows().size()).sum())).orElseThrow();
        if (groups.size() > 1) {
            diags.add(Diagnostic.warn("PDF_OTHER_TABLES", (groups.size() - 1) + " table layout(s) with different columns were not staged; "
                    + "only the largest table layout (" + best.size() + " table part(s)) is used."));
        }
        List<String> rawHeader = best.getFirst().header();
        int width = rawHeader.size();
        List<String> headers = uniqueHeaders(rawHeader, width, diags);
        List<TabularResult.Row> rows = new ArrayList<>();
        int seq = 0;
        for (PdfExtraction.Table t : best) {
            if (t.rows() == null) continue;
            for (List<String> cells : t.rows()) {
                if (cells == null || cells.stream().allMatch(c -> c == null || c.isBlank())) continue;
                if (rows.size() >= limits.maxRows()) {
                    throw new ParseFailure("ROW_LIMIT_EXCEEDED", "The PDF tables have more than " + limits.maxRows() + " rows.", false);
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < width; c++) {
                    String v = c < cells.size() && cells.get(c) != null ? cells.get(c) : "";
                    if (v.length() > limits.maxCellLength()) v = v.substring(0, limits.maxCellLength());
                    values.put(headers.get(c), v);
                }
                rows.add(new TabularResult.Row(++seq, values, t.page(), "pdf-table", TABLE_CONFIDENCE));
            }
        }
        if (rows.isEmpty()) throw new ParseFailure("PDF_NO_STRUCTURED_DATA", "Tables were found but contained no data rows.", false);
        diags.add(Diagnostic.info("PARSED", rows.size() + " table row(s) staged from PDF. Row numbers are sequence numbers; see the page reference."));
        return new TabularResult(headers, 1, true, rows, diags, extra);
    }

    /** Sequential 'Label: value' candidates; a repeated label starts a new record. */
    private static TabularResult fromCandidates(List<PdfExtraction.Candidate> cands, TabularBuilder.Limits limits,
                                                List<Diagnostic> diags, Map<String, Object> extra) {
        List<String> labelOrder = new ArrayList<>();
        List<Map<String, PdfExtraction.Candidate>> records = new ArrayList<>();
        Map<String, PdfExtraction.Candidate> current = new LinkedHashMap<>();
        for (PdfExtraction.Candidate c : cands) {
            if (c.label() == null || c.label().isBlank()) continue;
            String label = c.label().strip().replaceAll("\\s+", " ");
            if (current.containsKey(label)) {
                records.add(current);
                current = new LinkedHashMap<>();
            }
            current.put(label, c);
            if (!labelOrder.contains(label)) labelOrder.add(label);
        }
        if (!current.isEmpty()) records.add(current);
        if (labelOrder.size() > limits.maxColumns()) {
            throw new ParseFailure("TOO_MANY_COLUMNS", "Too many distinct field labels were extracted from the PDF.", false);
        }
        if (records.size() > limits.maxRows()) {
            throw new ParseFailure("ROW_LIMIT_EXCEEDED", "Too many records were extracted from the PDF.", false);
        }
        List<TabularResult.Row> rows = new ArrayList<>();
        int seq = 0;
        for (Map<String, PdfExtraction.Candidate> rec : records) {
            Map<String, String> values = new LinkedHashMap<>();
            BigDecimal minConf = BigDecimal.ONE;
            Integer page = null;
            String method = null;
            for (String l : labelOrder) {
                PdfExtraction.Candidate c = rec.get(l);
                values.put(l, c == null || c.value() == null ? "" : truncate(c.value(), limits.maxCellLength()));
                if (c != null) {
                    if (c.confidence() != null && c.confidence().compareTo(minConf) < 0) minConf = c.confidence();
                    if (page == null) page = c.page();
                    if (method == null) method = c.method();
                }
            }
            rows.add(new TabularResult.Row(++seq, values, page, method == null ? "pdf-label-value" : method,
                    minConf.setScale(3, java.math.RoundingMode.HALF_UP)));
        }
        diags.add(Diagnostic.warn("PDF_LABEL_VALUE", rows.size() + " record(s) were assembled from 'Label: value' text. "
                + "Field boundaries are heuristic; check every value."));
        return new TabularResult(labelOrder, 1, false, rows, diags, extra);
    }

    private static List<String> uniqueHeaders(List<String> raw, int width, List<Diagnostic> diags) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < width; i++) {
            String n = raw.get(i) == null ? "" : raw.get(i).strip().replaceAll("\\s+", " ");
            if (n.isEmpty()) n = "Column " + (i + 1);
            String base = n;
            int k = 2;
            while (!seen.add(n.toLowerCase())) n = base + " (" + k++ + ")";
            if (!n.equals(base)) diags.add(Diagnostic.warn("HEADER_DUPLICATE", "Duplicate column '" + base + "' renamed to '" + n + "'."));
            out.add(n);
        }
        return out;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
