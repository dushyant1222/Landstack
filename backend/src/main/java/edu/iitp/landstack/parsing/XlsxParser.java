package edu.iitp.landstack.parsing;

import org.apache.poi.util.XMLHelper;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Streaming XLSX reader (POI event API). Uses cached cell values only: formulas are never evaluated
 * and macros are never executed. Zip-bomb limits are applied before opening the package.
 */
@Component
public class XlsxParser {
    public static final String VERSION = "java-xlsx-1.0 (poi 5.3 streaming)";

    static {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(50L * 1024 * 1024);
    }

    public List<String> sheetNames(Path file) {
        List<String> names = new ArrayList<>();
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (it.hasNext()) {
                try (InputStream ignored = it.next()) {
                    names.add(it.getSheetName());
                }
            }
        } catch (IOException | OpenXML4JException | RuntimeException e) {
            throw unreadable(e);
        }
        if (names.isEmpty()) throw new ParseFailure("NO_SHEETS", "The workbook contains no worksheets.", false);
        return names;
    }

    public TabularResult parse(Path file, String sheetName, Integer headerRow, TabularBuilder.Limits limits) {
        List<TabularBuilder.PhysicalRow> physical = new ArrayList<>();
        int limit = limits.maxRows() + 1000;
        try (OPCPackage pkg = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg, false);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) reader.getSheetsData();
            boolean found = false;
            while (it.hasNext()) {
                try (InputStream sheet = it.next()) {
                    if (!it.getSheetName().equals(sheetName)) continue;
                    found = true;
                    XMLReader xml = XMLHelper.newXMLReader();
                    Collector collector = new Collector(physical, limit, limits.maxColumns());
                    xml.setContentHandler(new XSSFSheetXMLHandler(styles, strings, collector, new SafeFormatter(), false));
                    try {
                        xml.parse(new InputSource(sheet));
                    } catch (StopReading stop) {
                        throw new ParseFailure("ROW_LIMIT_EXCEEDED", "The worksheet has more than " + limits.maxRows()
                                + " data rows. Split it into smaller files (no rows were imported).", false);
                    } catch (TooWide wide) {
                        throw new ParseFailure("TOO_MANY_COLUMNS", "The worksheet has more than "
                                + limits.maxColumns() + " columns.", false);
                    }
                }
            }
            if (!found) throw new ParseFailure("SHEET_NOT_FOUND", "Worksheet '" + sheetName + "' was not found.", false);
        } catch (ParseFailure pf) {
            throw pf;
        } catch (IOException | OpenXML4JException | SAXException | ParserConfigurationException | RuntimeException e) {
            throw unreadable(e);
        }
        List<Diagnostic> diags = new ArrayList<>();
        diags.add(Diagnostic.info("SHEET", "Worksheet: '" + sheetName + "'. Formula cells use their last saved (cached) values; formulas are not evaluated."));
        diags.add(Diagnostic.info("MERGED_CELLS", "In merged cell ranges only the first cell carries the value; other cells read as blank."));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("sheet", sheetName);
        return TabularBuilder.build(physical, headerRow, limits, diags, extra);
    }

    private static ParseFailure unreadable(Exception e) {
        if (e instanceof InvalidFormatException || e.getClass().getSimpleName().contains("Encrypted")) {
            return new ParseFailure("XLSX_UNREADABLE", "The workbook is corrupt, encrypted or not a valid .xlsx file.", false);
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("Zip bomb") || msg.contains("inflate ratio")) {
            return new ParseFailure("XLSX_TOO_COMPRESSED", "The workbook expands to an unsafe size and was rejected.", false);
        }
        return new ParseFailure("XLSX_UNREADABLE", "The workbook could not be read. Re-save it as .xlsx and try again.", false);
    }

    private static final class StopReading extends RuntimeException {
        StopReading() { super(null, null, false, false); }
    }

    private static final class TooWide extends RuntimeException {
        TooWide() { super(null, null, false, false); }
    }

    /** Collects rows as 1-based physical row numbers with sparse cells expanded to positional lists. */
    private static final class Collector implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final List<TabularBuilder.PhysicalRow> out;
        private final int limit;
        private final int maxColumns;
        private List<String> current;
        private int currentCol;

        Collector(List<TabularBuilder.PhysicalRow> out, int limit, int maxColumns) {
            this.out = out;
            this.limit = limit;
            this.maxColumns = maxColumns;
        }

        @Override
        public void startRow(int rowNum) {
            if (out.size() >= limit) throw new StopReading();
            current = new ArrayList<>();
            currentCol = -1;
        }

        @Override
        public void endRow(int rowNum) {
            out.add(new TabularBuilder.PhysicalRow(rowNum + 1, current));
        }

        @Override
        public void cell(String ref, String formattedValue, XSSFComment comment) {
            int col = ref == null ? currentCol + 1 : new CellReference(ref).getCol();
            if (col >= maxColumns * 2) throw new TooWide();
            while (current.size() < col) current.add("");
            current.add(formattedValue == null ? "" : formattedValue);
            currentCol = col;
        }
    }

    /**
     * Formats dates as ISO-8601 (unambiguous) and large integral numbers in full (Excel's General format
     * would otherwise render 14-digit identifiers in scientific notation).
     */
    static final class SafeFormatter extends DataFormatter {
        SafeFormatter() {
            super(Locale.ROOT);
        }

        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString, boolean use1904Windowing) {
            if (DateUtil.isADateFormat(formatIndex, formatString) && DateUtil.isValidExcelDate(value)) {
                LocalDateTime dt = DateUtil.getLocalDateTime(value, use1904Windowing);
                return dt.toLocalTime().equals(LocalTime.MIDNIGHT) ? dt.toLocalDate().toString() : dt.toString();
            }
            if ((formatString == null || "General".equalsIgnoreCase(formatString)) && value == Math.rint(value)
                    && Math.abs(value) < 1e15) {
                return BigDecimal.valueOf(value).toBigInteger().toString();
            }
            return super.formatRawCellContents(value, formatIndex, formatString, use1904Windowing);
        }
    }
}
