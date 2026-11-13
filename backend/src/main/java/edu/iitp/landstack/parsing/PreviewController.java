package edu.iitp.landstack.parsing;

import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.common.Paging;
import edu.iitp.landstack.imports.ImportJobRepository;
import edu.iitp.landstack.security.Perms;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Read access to staged preview rows and parser diagnostics (operators/administrators). */
@RestController
@RequestMapping("/api/v1/imports/{id}")
@Tag(name = "Import preview")
public class PreviewController {
    private final ImportJobRepository jobs;
    private final ImportRowRepository rows;
    private final ProcessingAttemptRepository attempts;

    public PreviewController(ImportJobRepository jobs, ImportRowRepository rows, ProcessingAttemptRepository attempts) {
        this.jobs = jobs;
        this.rows = rows;
        this.attempts = attempts;
    }

    public record StagedRowDto(UUID id, int rowNumber, Map<String, String> rawValues, Map<String, String> correctedValues,
                               Map<String, Object> normalized, List<Map<String, Object>> validation, ImportRow.State state,
                               boolean excluded, Integer page, String extractionMethod, BigDecimal confidence) {
        static StagedRowDto from(ImportRow r) {
            return new StagedRowDto(r.getId(), r.getRowNumber(), r.getRawValues(), r.getCorrectedValues(), r.getNormalized(),
                    r.getValidation(), r.getRowState(), r.isExcluded(), r.getPageRef(), r.getExtractionMethod(), r.getConfidence());
        }
    }

    public record DiagnosticsDto(UUID attemptId, String stage, String processor, String processorVersion,
                                 ProcessingAttempt.Outcome outcome, Instant startedAt, Instant finishedAt,
                                 Map<String, Object> details) {}

    @GetMapping("/rows")
    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public PageResponse<StagedRowDto> rows(@PathVariable UUID id,
                                           @RequestParam(required = false) List<ImportRow.State> state,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        jobs.findById(id).orElseThrow(() -> ApiException.notFound("Import"));
        if (page < 0 || size < 1 || size > Paging.MAX_SIZE) throw ApiException.validation("Invalid page or size.");
        Optional<ProcessingAttempt> current = currentAttempt(id);
        if (current.isEmpty()) return new PageResponse<>(List.of(), page, size, 0, 0);
        var pageable = PageRequest.of(page, size, Sort.by("rowNumber"));
        var result = state == null || state.isEmpty()
                ? rows.findByImportJobIdAndAttemptId(id, current.get().getId(), pageable)
                : rows.findByImportJobIdAndAttemptIdAndRowStateIn(id, current.get().getId(), state, pageable);
        return PageResponse.of(result, StagedRowDto::from);
    }

    @GetMapping("/diagnostics")
    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public List<DiagnosticsDto> diagnostics(@PathVariable UUID id) {
        jobs.findById(id).orElseThrow(() -> ApiException.notFound("Import"));
        List<DiagnosticsDto> out = new ArrayList<>();
        for (ProcessingAttempt a : attempts.findByImportJobIdOrderByStartedAtAsc(id)) {
            Map<String, Object> d = new LinkedHashMap<>(a.getDiagnostics());
            d.remove("pages"); // PDF page text is served separately
            out.add(new DiagnosticsDto(a.getId(), a.getStage(), a.getProcessor(), a.getProcessorVersion(), a.getOutcome(),
                    a.getStartedAt(), a.getFinishedAt(), d));
        }
        Collections.reverse(out); // newest first
        return out;
    }

    /** Extracted page text for PDF imports, for side-by-side review. Content is untrusted text. */
    @GetMapping("/pdf-text")
    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public Map<String, Object> pdfText(@PathVariable UUID id) {
        jobs.findById(id).orElseThrow(() -> ApiException.notFound("Import"));
        return attempts.findByImportJobIdOrderByStartedAtAsc(id).stream()
                .filter(a -> "EXTRACT".equals(a.getStage()) && a.getDiagnostics().containsKey("pages"))
                .reduce((first, second) -> second)
                .map(a -> Map.<String, Object>of("pageCount", a.getDiagnostics().getOrDefault("pageCount", 0),
                        "pages", a.getDiagnostics().get("pages")))
                .orElse(Map.of("pageCount", 0, "pages", List.of()));
    }

    private Optional<ProcessingAttempt> currentAttempt(UUID jobId) {
        return attempts.findFirstByImportJobIdAndStageInAndOutcomeOrderByStartedAtDesc(
                jobId, ParsingService.PARSE_STAGES, ProcessingAttempt.Outcome.SUCCEEDED);
    }
}
