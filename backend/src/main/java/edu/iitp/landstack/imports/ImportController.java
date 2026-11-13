package edu.iitp.landstack.imports;

import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.imports.ImportDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Imports", description = "Upload and import-job lifecycle")
public class ImportController {
    private final ImportService service;
    private final ImportRemovalService removal;

    public ImportController(ImportService service, ImportRemovalService removal) {
        this.service = service;
        this.removal = removal;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportJobDto upload(@RequestPart("file") MultipartFile file) {
        return service.upload(file);
    }

    @GetMapping
    public PageResponse<ImportJobDto> list(@RequestParam(required = false) ImportStatus status,
                                           @RequestParam(required = false) UUID createdBy,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "25") int size,
                                           @RequestParam(required = false) String sort) {
        return service.list(status, createdBy, q, from, to, page, size, sort);
    }

    @GetMapping("/{id}")
    public ImportJobDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/timeline")
    public List<TimelineEntry> timeline(@PathVariable UUID id) {
        return service.timeline(id);
    }

    @PostMapping("/{id}/cancel")
    public ImportJobDto cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/retry")
    public ImportJobDto retry(@PathVariable UUID id) {
        return service.retry(id);
    }

    @PostMapping("/{id}/sheet-selection")
    public ImportJobDto selectSheet(@PathVariable UUID id, @Valid @RequestBody SheetSelectionRequest req) {
        return service.selectSheet(id, req);
    }

    /** Administrator removal of a (demonstration) import, its records and issues. Audit history is retained. */
    @DeleteMapping("/{id}")
    public ImportRemovalService.RemovalResult remove(@PathVariable UUID id, @RequestParam("confirm") boolean confirm) {
        return removal.remove(id, confirm);
    }
}
