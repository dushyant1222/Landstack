package edu.iitp.landstack.unit;

import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.exports.CsvSafe;
import edu.iitp.landstack.files.DetectedType;
import edu.iitp.landstack.files.FileTypeDetector;
import edu.iitp.landstack.files.FilenameSanitizer;
import edu.iitp.landstack.geospatial.GeometryValidator;
import edu.iitp.landstack.imports.ImportStatus;
import edu.iitp.landstack.landrecords.OwnerMasking;
import edu.iitp.landstack.mapping.DateParser;
import edu.iitp.landstack.mapping.MappingDtos.SuggestionStatus;
import edu.iitp.landstack.mapping.MappingSuggestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;


class SafetyTest {
    @TempDir
    Path dir;

    private Path file(String name, byte[] content) throws Exception {
        Path p = dir.resolve(name);
        Files.write(p, content);
        return p;
    }

    private ErrorCode codeOf(Runnable r) {
        try {
            r.run();
            return null;
        } catch (ApiException e) {
            return e.code();
        }
    }

    @Test
    void detectsTypesBySignatureNotExtension() throws Exception {
        assertThat(FileTypeDetector.detect(file("a.csv", "A,B\n1,2\n".getBytes()), "csv")).isEqualTo(DetectedType.CSV);
        assertThat(FileTypeDetector.detect(file("a.pdf", "%PDF-1.4\n...".getBytes()), "pdf")).isEqualTo(DetectedType.PDF);
        Path xlsx = dir.resolve("a.xlsx");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(xlsx))) {
            z.putNextEntry(new ZipEntry("[Content_Types].xml")); z.write("<x/>".getBytes()); z.closeEntry();
            z.putNextEntry(new ZipEntry("xl/workbook.xml")); z.write("<x/>".getBytes()); z.closeEntry();
        }
        assertThat(FileTypeDetector.detect(xlsx, "xlsx")).isEqualTo(DetectedType.XLSX);
    }

    @Test
    void rejectsMismatchesExecutablesAndUnsupportedTypes() throws Exception {
        Path pdfAsCsv = file("x.csv", "%PDF-1.7 binary".getBytes());
        assertThat(codeOf(() -> detect(pdfAsCsv, "csv"))).isEqualTo(ErrorCode.FILE_SIGNATURE_MISMATCH);
        Path exe = file("y.csv", new byte[]{0x4D, 0x5A, 0, 0});
        assertThat(codeOf(() -> detect(exe, "csv"))).isEqualTo(ErrorCode.FILE_SIGNATURE_MISMATCH);
        Path textAsPdf = file("z.pdf", "hello".getBytes());
        assertThat(codeOf(() -> detect(textAsPdf, "pdf"))).isEqualTo(ErrorCode.FILE_SIGNATURE_MISMATCH);
        assertThat(codeOf(() -> detect(textAsPdf, "exe"))).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
        assertThat(codeOf(() -> detect(textAsPdf, "xls"))).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
        Path encrypted = file("e.pdf", "%PDF-1.4\n1 0 obj << /Encrypt 2 0 R >>".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(codeOf(() -> detect(encrypted, "pdf"))).isEqualTo(ErrorCode.FILE_UNREADABLE);
    }

    private static void detect(Path p, String ext) {
        try {
            FileTypeDetector.detect(p, ext);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void filenamesCannotTraversePaths() {
        assertThat(FilenameSanitizer.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FilenameSanitizer.sanitize("..\\..\\win.ini")).isEqualTo("win.ini");
        assertThat(FilenameSanitizer.sanitize("...csv")).isEqualTo("csv");
        assertThat(FilenameSanitizer.sanitize("a<b>:c|d?.csv")).isEqualTo("abcd.csv");
        assertThat(FilenameSanitizer.extension("Data.CSV")).isEqualTo("csv");
    }

    @Test
    void csvFormulaInjectionIsNeutralised() {
        assertThat(CsvSafe.cell("=HYPERLINK(\"x\")")).isEqualTo("'=HYPERLINK(\"x\")");
        assertThat(CsvSafe.cell("+1+1")).isEqualTo("'+1+1");
        assertThat(CsvSafe.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvSafe.cell("-2+3")).isEqualTo("'-2+3");
        assertThat(CsvSafe.cell("-73.5")).isEqualTo("-73.5");
        assertThat(CsvSafe.cell("Rampur")).isEqualTo("Rampur");
        assertThat(CsvSafe.cell(null)).isEqualTo("");
    }

    @Test
    void importStateMachineAllowsOnlyDocumentedTransitions() {
        assertThat(ImportStatus.PREVIEW_READY.canTransitionTo(ImportStatus.CONFIRMING)).isTrue();
        assertThat(ImportStatus.AWAITING_MAPPING.canTransitionTo(ImportStatus.CONFIRMING)).isFalse();
        assertThat(ImportStatus.COMPLETED.canTransitionTo(ImportStatus.PREVIEW_READY)).isFalse();
        assertThat(ImportStatus.FAILED.canTransitionTo(ImportStatus.PARSING)).isTrue();
        assertThat(ImportStatus.REMOVED.isTerminal()).isTrue();
        for (ImportStatus s : ImportStatus.values()) assertThat(s.canTransitionTo(ImportStatus.RECEIVED)).isFalse();
    }

    @Test
    void geometryValidation() {
        assertThat(GeometryValidator.parse("{\"type\":\"Point\",\"coordinates\":[85.1,25.6]}").problem()).isNull();
        assertThat(GeometryValidator.parse("{\"type\":\"Polygon\",\"coordinates\":[[[85,25],[85.1,25],[85.1,25.1],[85,25]]]}").problem()).isNull();
        assertThat(GeometryValidator.parse("{\"type\":\"Polygon\",\"coordinates\":[[[85,25],[85.1,25],[85.1,25.1],[85,25.2]]]}").problem()).contains("not closed");
        assertThat(GeometryValidator.parse("{\"type\":\"Point\",\"coordinates\":[185,25]}").problem()).contains("out of range");
        assertThat(GeometryValidator.parse("{\"type\":\"LineString\",\"coordinates\":[]}").problem()).contains("Unsupported");
        assertThat(GeometryValidator.parse("not json").problem()).contains("JSON");
    }

    @Test
    void ownerMaskingKeepsOnlyInitials() {
        assertThat(OwnerMasking.mask("Ram Kumar Singh")).isEqualTo("R**** K**** S****");
        assertThat(OwnerMasking.mask(null)).isNull();
    }

    @Test
    void datesRequireUnambiguousFormats() {
        assertThat(DateParser.parse("31/12/2020", false).problem()).isEqualTo(DateParser.Problem.NONE);
        assertThat(DateParser.parse("12/31/2020", false).problem()).isEqualTo(DateParser.Problem.MONTH_FIRST);
        assertThat(DateParser.parse("01/02/20", false).problem()).isEqualTo(DateParser.Problem.TWO_DIGIT_YEAR);
        assertThat(DateParser.parse("31/02/2020", false).problem()).isEqualTo(DateParser.Problem.INVALID);
        assertThat(DateParser.parse("2020-02-29", false).problem()).isEqualTo(DateParser.Problem.NONE);
    }

    @Test
    void mappingSuggestionsAreExactAndAmbiguityIsNotSuggested() {
        var s = new MappingSuggestionService().suggest(List.of("ULPIN", "Khasra No.", "Village", "Lat", "Longitude", "Area", "Unit"));
        assertThat(s).anyMatch(f -> f.field().equals("parcel_id") && f.status() == SuggestionStatus.SUGGESTED && f.suggestedColumn().equals("ULPIN"));
        assertThat(s).anyMatch(f -> f.field().equals("survey_khasra_no") && f.status() == SuggestionStatus.SUGGESTED);
        assertThat(s).anyMatch(f -> f.field().equals("owner_name") && f.status() == SuggestionStatus.NONE);
        var amb = new MappingSuggestionService().suggest(List.of("Village", "Locality"));
        assertThat(amb).anyMatch(f -> f.field().equals("village_locality") && f.status() == SuggestionStatus.AMBIGUOUS);
    }
}
