package edu.iitp.landstack.processing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** Response of processing-service POST /v1/pdf/extract (contract version 1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PdfExtraction(String contractVersion, String processor, int pageCount, String status,
                            List<Page> pages, List<Table> tables, List<Candidate> candidates, List<String> warnings) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(int page, int charCount, boolean likelyScanned, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Table(int page, int index, List<String> header, List<List<String>> rows) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(String label, String value, int page, String method, BigDecimal confidence) {}
}
