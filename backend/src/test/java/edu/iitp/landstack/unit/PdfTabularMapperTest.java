package edu.iitp.landstack.unit;

import edu.iitp.landstack.parsing.ParseFailure;
import edu.iitp.landstack.parsing.TabularBuilder;
import edu.iitp.landstack.parsing.TabularResult;
import edu.iitp.landstack.processing.PdfExtraction;
import edu.iitp.landstack.processing.PdfTabularMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTabularMapperTest {
    private static final TabularBuilder.Limits LIMITS = new TabularBuilder.Limits(100, 50, 4000);

    private static PdfExtraction x(String status, List<PdfExtraction.Table> tables, List<PdfExtraction.Candidate> cands) {
        return new PdfExtraction("1", "test", 2, status, List.of(new PdfExtraction.Page(1, 100, false, "text")), tables, cands, List.of());
    }

    @Test
    void scannedPdfIsReportedAsOcrRequired() {
        assertThatThrownBy(() -> PdfTabularMapper.toTabular(x("LIKELY_SCANNED", List.of(), List.of()), LIMITS))
                .isInstanceOf(ParseFailure.class).hasFieldOrPropertyWithValue("code", "PDF_OCR_REQUIRED");
    }

    @Test
    void emptyExtractionIsNotASuccess() {
        assertThatThrownBy(() -> PdfTabularMapper.toTabular(x("OK", List.of(), List.of()), LIMITS))
                .isInstanceOf(ParseFailure.class).hasFieldOrPropertyWithValue("code", "PDF_NO_STRUCTURED_DATA");
    }

    @Test
    void tablesKeepPageReferencesAndContinuationPagesMerge() {
        var t1 = new PdfExtraction.Table(1, 0, List.of("Khasra No", "Area"), List.of(List.of("0012", "1.5")));
        var t2 = new PdfExtraction.Table(2, 0, List.of("Khasra No", "Area"), List.of(List.of("0013", "2")));
        TabularResult r = PdfTabularMapper.toTabular(x("OK", List.of(t1, t2), List.of()), LIMITS);
        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(1).page()).isEqualTo(2);
        assertThat(r.rows().get(0).values().get("Khasra No")).isEqualTo("0012");
        assertThat(r.rows().get(0).method()).isEqualTo("pdf-table");
    }

    @Test
    void labelValueCandidatesFormRecordsWithLowestConfidence() {
        var c = List.of(new PdfExtraction.Candidate("Khasra No", "0012", 1, "regex-label", new BigDecimal("0.7")),
                new PdfExtraction.Candidate("Area", "1.5", 1, "regex-label", new BigDecimal("0.6")),
                new PdfExtraction.Candidate("Khasra No", "0013", 2, "regex-label", new BigDecimal("0.7")));
        TabularResult r = PdfTabularMapper.toTabular(x("OK", List.of(), c), LIMITS);
        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0).confidence()).isEqualByComparingTo("0.6");
        assertThat(r.rows().get(1).values().get("Area")).isEmpty();
    }
}
