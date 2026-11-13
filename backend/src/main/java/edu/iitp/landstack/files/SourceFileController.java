package edu.iitp.landstack.files;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.security.Perms;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/source-files")
@Tag(name = "Source files")
public class SourceFileController {
    private final SourceFileRepository repo;
    private final FileStorageService storage;
    private final AuditService audit;

    public SourceFileController(SourceFileRepository repo, FileStorageService storage, AuditService audit) {
        this.repo = repo;
        this.storage = storage;
        this.audit = audit;
    }

    public record SourceFileDto(UUID id, String originalFilename, DetectedType detectedType, long sizeBytes,
                                String sha256, Instant uploadedAt, boolean contentAvailable) {
        public static SourceFileDto from(SourceFile f) {
            return new SourceFileDto(f.getId(), f.getOriginalFilename(), f.getDetectedType(), f.getSizeBytes(),
                    f.getSha256(), f.getUploadedAt(), f.getPurgedAt() == null);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perms.READ)
    public SourceFileDto get(@PathVariable UUID id) {
        return SourceFileDto.from(repo.findById(id).orElseThrow(() -> ApiException.notFound("Source file")));
    }

    /** Streams the original bytes as an attachment. Operators/administrators only. */
    @GetMapping("/{id}/content")
    @PreAuthorize(Perms.OPERATE)
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID id) throws IOException {
        SourceFile f = repo.findById(id).orElseThrow(() -> ApiException.notFound("Source file"));
        if (f.getPurgedAt() != null || !storage.exists(f.getStorageKey())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "The stored file is no longer available (removed by retention policy).");
        }
        MediaType type = switch (f.getDetectedType()) {
            case PDF -> MediaType.APPLICATION_PDF;
            case CSV -> new MediaType("text", "csv", StandardCharsets.UTF_8);
            case XLSX -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        };
        audit.record("SOURCE_FILE_DOWNLOADED", "SOURCE_FILE", f.getId(), Map.of());
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(f.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(storage.open(f.getStorageKey())));
    }
}
