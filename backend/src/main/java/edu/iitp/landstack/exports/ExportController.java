package edu.iitp.landstack.exports;

import edu.iitp.landstack.landrecords.LandRecordFilter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/exports")
@Tag(name = "Exports")
public class ExportController {
    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    /** CSV of currently filtered records (administrator / data operator only). */
    @GetMapping("/land-records.csv")
    public ResponseEntity<byte[]> landRecords(LandRecordFilter filter) {
        byte[] csv = service.landRecordsCsv(filter);
        String name = "landstack-records-" + LocalDate.now(ZoneOffset.UTC) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(csv);
    }
}
