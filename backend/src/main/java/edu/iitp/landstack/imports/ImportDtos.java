package edu.iitp.landstack.imports;

import edu.iitp.landstack.files.DetectedType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ImportDtos {
    private ImportDtos() {}

    public record ImportJobDto(UUID id, ImportStatus status, String statusCode, String statusMessage, boolean retryable,
                               UUID sourceFileId, String originalFilename, DetectedType fileType, long sizeBytes,
                               String sha256, UUID createdBy, String createdByName, Instant createdAt, Instant updatedAt,
                               UUID duplicateOfJobId, List<String> sheetNames, String selectedSheet, Integer headerRow,
                               List<String> sourceColumns, Integer totalRows, Integer validRows, Integer warningRows,
                               Integer rejectedRows, Integer importedRows, Instant confirmedAt,
                               Map<String, Object> resultSummary, long version) {}

    public record SheetSelectionRequest(@Size(max = 255) String sheetName, @Min(1) Integer headerRow) {}

    public record TimelineEntry(Instant at, String kind, String label, String detail, String actorName) {}
}
