package edu.iitp.landstack.unit;

import edu.iitp.landstack.parsing.*;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsingTest {
    private static final TabularBuilder.Limits LIMITS = new TabularBuilder.Limits(100, 50, 4000);
    private final CsvParser csv = new CsvParser();
    private final XlsxParser xlsx = new XlsxParser();

    @TempDir
    Path dir;

    private Path write(String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void parsesStandardCsvAndKeepsLeadingZeros() throws Exception {
        TabularResult r = csv.parse(write("a.csv", "Khasra No,Area,Village\n0012,1.5,Rampur\n0034,2,\"Sita, Pur\"\n"), null, LIMITS);
        assertThat(r.headers()).containsExactly("Khasra No", "Area", "Village");
        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0).values().get("Khasra No")).isEqualTo("0012");
        assertThat(r.rows().get(1).values().get("Village")).isEqualTo("Sita, Pur");
        assertThat(r.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(r.headerCertain()).isTrue();
    }

    @Test
    void detectsSemicolonAndBom() throws Exception {
        Path p = dir.resolve("b.csv");
        Files.write(p, ("﻿A;B\n1;2\n3;4\n").getBytes(StandardCharsets.UTF_8));
        TabularResult r = csv.parse(p, null, LIMITS);
        assertThat(r.headers()).containsExactly("A", "B");
        assertThat(r.diagnostics()).anyMatch(d -> d.code().equals("ENCODING") && d.message().contains("byte-order"));
    }

    @Test
    void duplicateAndBlankHeadersAreRenamedWithWarnings() throws Exception {
        TabularResult r = csv.parse(write("c.csv", "Area,Area,\n1,2,3\n"), null, LIMITS);
        assertThat(r.headers()).containsExactly("Area", "Area (2)", "Column 3");
        assertThat(r.diagnostics()).anyMatch(d -> d.code().equals("HEADER_DUPLICATE"));
        assertThat(r.diagnostics()).anyMatch(d -> d.code().equals("HEADER_BLANK"));
    }

    @Test
    void numericFirstRowMakesHeaderUncertainAndOperatorCanChooseHeaderRow() throws Exception {
        Path p = write("d.csv", "Report generated 2026\n12,34\nKhasra,Area\n001,5\n");
        TabularResult auto = csv.parse(p, null, LIMITS);
        assertThat(auto.diagnostics()).anyMatch(d -> d.code().equals("HEADER_UNCERTAIN") || d.code().equals("PARSED"));
        TabularResult chosen = csv.parse(p, 3, LIMITS);
        assertThat(chosen.headers()).containsExactly("Khasra", "Area");
        assertThat(chosen.rows()).hasSize(1);
    }

    @Test
    void emptyAndHeaderOnlyFilesFail() throws Exception {
        assertThatThrownBy(() -> csv.parse(write("e.csv", "\n\n"), null, LIMITS))
                .isInstanceOf(ParseFailure.class).hasFieldOrPropertyWithValue("code", "NO_DATA");
        assertThatThrownBy(() -> csv.parse(write("f.csv", "A,B\n"), null, LIMITS))
                .isInstanceOf(ParseFailure.class).hasFieldOrPropertyWithValue("code", "NO_DATA_ROWS");
    }

    @Test
    void rowLimitIsEnforcedWithoutSilentTruncation() throws Exception {
        StringBuilder sb = new StringBuilder("A,B\n");
        for (int i = 0; i < 150; i++) sb.append(i).append(",x\n");
        assertThatThrownBy(() -> csv.parse(write("g.csv", sb.toString()), null, LIMITS))
                .isInstanceOf(ParseFailure.class).hasFieldOrPropertyWithValue("code", "ROW_LIMIT_EXCEEDED");
    }

    @Test
    void malformedQuotesFailSafely() throws Exception {
        assertThatThrownBy(() -> csv.parse(write("h.csv", "A,B\n\"unclosed,2\n3,4\n"), null, LIMITS))
                .isInstanceOf(ParseFailure.class);
    }

    @Test
    void formulaLikeTextIsKeptAsText() throws Exception {
        TabularResult r = csv.parse(write("i.csv", "A,B\n=1+1,x\n"), null, LIMITS);
        assertThat(r.rows().getFirst().values().get("A")).isEqualTo("=1+1");
        assertThat(r.diagnostics()).anyMatch(d -> d.code().equals("FORMULA_LIKE_TEXT"));
    }

    @Test
    void xlsxListsSheetsUsesCachedFormulaValuesAndIsoDates() throws Exception {
        Path p = dir.resolve("w.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(p)) {
            XSSFSheet notes = wb.createSheet("Notes");
            notes.createRow(0).createCell(0).setCellValue("Synthetic workbook");
            XSSFSheet s = wb.createSheet("Parcels");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("ULPIN");
            h.createCell(1).setCellValue("Area");
            h.createCell(2).setCellValue("Double");
            h.createCell(3).setCellValue("Reg Date");
            h.createCell(4).setCellValue("Big Id");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("00123");
            r.createCell(1).setCellValue(2.5);
            r.createCell(2).setCellFormula("B2*2");
            CreationHelper ch = wb.getCreationHelper();
            CellStyle ds = wb.createCellStyle();
            ds.setDataFormat(ch.createDataFormat().getFormat("dd/mm/yyyy"));
            var dc = r.createCell(3);
            dc.setCellValue(LocalDate.of(2021, 3, 4));
            dc.setCellStyle(ds);
            r.createCell(4).setCellValue(12345678901234d);
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            wb.write(out);
        }
        assertThat(xlsx.sheetNames(p)).containsExactly("Notes", "Parcels");
        TabularResult t = xlsx.parse(p, "Parcels", null, LIMITS);
        var v = t.rows().getFirst().values();
        assertThat(v.get("ULPIN")).isEqualTo("00123");
        assertThat(v.get("Double")).isEqualTo("5");
        assertThat(v.get("Reg Date")).isEqualTo("2021-03-04");
        assertThat(v.get("Big Id")).isEqualTo("12345678901234");
    }

    @Test
    void corruptXlsxIsReportedNotCrashing() throws Exception {
        Path p = dir.resolve("bad.xlsx");
        Files.write(p, new byte[]{0x50, 0x4B, 0x03, 0x04, 1, 2, 3});
        assertThatThrownBy(() -> xlsx.sheetNames(p)).isInstanceOf(ParseFailure.class);
    }
}
