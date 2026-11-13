package edu.iitp.landstack.parsing;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Bounded CSV reader with deterministic encoding and delimiter detection. Values are kept as strings. */
@Component
public class CsvParser {
    public static final String VERSION = "java-csv-1.0 (commons-csv 1.12)";
    private static final char[] CANDIDATES = {',', ';', '\t', '|'};

    public TabularResult parse(Path file, Integer headerRow, TabularBuilder.Limits limits) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        List<Diagnostic> diags = new ArrayList<>();
        String text = decode(bytes, diags);
        char delimiter = detectDelimiter(text, diags);

        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(false)
                .setTrim(false).setAllowMissingColumnNames(true).setIgnoreSurroundingSpaces(false).build();
        List<TabularBuilder.PhysicalRow> physical = new ArrayList<>();
        int limit = limits.maxRows() + 1000; // headroom for header/blank rows; builder enforces the exact limit
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            int n = 0;
            for (CSVRecord rec : parser) {
                n++;
                if (n > limit) {
                    throw new ParseFailure("ROW_LIMIT_EXCEEDED", "The file has more than " + limits.maxRows()
                            + " data rows. Split it into smaller files (no rows were imported).", false);
                }
                if (rec.size() > limits.maxColumns() * 2) {
                    throw new ParseFailure("TOO_MANY_COLUMNS", "Record " + n + " has " + rec.size()
                            + " fields; the maximum is " + limits.maxColumns() + ".", false);
                }
                List<String> cells = new ArrayList<>(rec.size());
                rec.forEach(cells::add);
                physical.add(new TabularBuilder.PhysicalRow(n, cells));
            }
        } catch (UncheckedIOException | IllegalStateException e) {
            throw new ParseFailure("CSV_MALFORMED", "The CSV is malformed (for example an unclosed quote) near record "
                    + (physical.size() + 1) + ".", false);
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("delimiter", delimiter == '\t' ? "TAB" : String.valueOf(delimiter));
        return TabularBuilder.build(physical, headerRow, limits, diags, extra);
    }

    static String decode(byte[] bytes, List<Diagnostic> diags) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            diags.add(Diagnostic.info("ENCODING", "Encoding: UTF-8 (with byte-order mark)."));
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE
                || (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF)) {
            throw new ParseFailure("ENCODING_UNSUPPORTED", "UTF-16 CSV files are not supported. Save the file as UTF-8 CSV.", false);
        }
        try {
            String s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            diags.add(Diagnostic.info("ENCODING", "Encoding: UTF-8."));
            return s;
        } catch (CharacterCodingException e) {
            diags.add(Diagnostic.warn("ENCODING_FALLBACK", "The file is not valid UTF-8; it was read as Windows-1252. "
                    + "Check that accented or non-Latin characters look correct, or re-save the file as UTF-8."));
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    static char detectDelimiter(String text, List<Diagnostic> diags) {
        List<String> lines = new ArrayList<>();
        for (String l : text.split("\r\n|\n|\r", 60)) {
            if (!l.isBlank()) lines.add(l);
            if (lines.size() >= 30) break;
        }
        if (lines.isEmpty()) return ',';
        List<Character> consistent = new ArrayList<>();
        Map<Character, Integer> typical = new HashMap<>();
        for (char c : CANDIDATES) {
            Map<Integer, Integer> freq = new HashMap<>();
            for (String l : lines) freq.merge(countOutsideQuotes(l, c), 1, Integer::sum);
            var mode = freq.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            if (mode.getKey() > 0 && mode.getValue() * 10 >= lines.size() * 8) {
                consistent.add(c);
                typical.put(c, mode.getKey());
            }
        }
        if (consistent.isEmpty()) {
            diags.add(Diagnostic.warn("DELIMITER_UNDETECTED", "No consistent delimiter was found; the file was read as a single-column comma-separated file."));
            return ',';
        }
        consistent.sort(Comparator.comparing((Character c) -> -typical.get(c)));
        char chosen = consistent.contains(',') && typical.get(',').equals(typical.get(consistent.getFirst())) ? ',' : consistent.getFirst();
        if (consistent.size() > 1) {
            diags.add(Diagnostic.warn("DELIMITER_AMBIGUOUS", "More than one delimiter looked plausible; '"
                    + (chosen == '\t' ? "TAB" : String.valueOf(chosen)) + "' was used. Check that columns split correctly."));
        } else {
            diags.add(Diagnostic.info("DELIMITER", "Delimiter: '" + (chosen == '\t' ? "TAB" : String.valueOf(chosen)) + "'."));
        }
        return chosen;
    }

    private static int countOutsideQuotes(String line, char c) {
        boolean inQuotes = false;
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') inQuotes = !inQuotes;
            else if (ch == c && !inQuotes) n++;
        }
        return n;
    }
}
