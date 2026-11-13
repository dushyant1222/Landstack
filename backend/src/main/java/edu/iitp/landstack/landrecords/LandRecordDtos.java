package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.files.DetectedType;
import edu.iitp.landstack.imports.ImportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LandRecordDtos {
    private LandRecordDtos() {}

    public record LandRecordListItem(UUID id, String parcelId, String surveyKhasraNo, String ownerName, String villageLocality,
                                     String tehsil, String district, String state, BigDecimal landAreaValue, String landAreaUnitRaw,
                                     String landAreaUnit, BigDecimal landAreaSqm, String landTypeRaw, String landTypeCategory,
                                     String registrationRef, LocalDate registrationDate, BigDecimal latitude, BigDecimal longitude,
                                     boolean mappable, String recordStatus, UUID importJobId, UUID sourceFileId,
                                     long openIssues, Instant createdAt) {}

    public record SourceFileRef(UUID id, String originalFilename, DetectedType type, Instant uploadedAt, String sha256) {}

    public record ImportJobRef(UUID id, ImportStatus status, Instant confirmedAt, String confirmedByName) {}

    public record Provenance(String normalizationVersion, UUID templateId, Integer templateVersion, Map<String, String> mapping,
                             Map<String, Object> options, int sourceRowNumber, Integer sourcePage) {}

    public record SourceValueDto(String field, String sourceColumn, String rawValue, String normalizedValue,
                                 String transformation, String changedByName, Instant at) {}

    public record IssueRef(UUID id, String ruleId, String severity, String status, String field, String reason) {}

    public record LandRecordDetail(LandRecordListItem record, Map<String, Object> geometry, String archivedReason, long version,
                                   SourceFileRef sourceFile, ImportJobRef importJob, Provenance provenance,
                                   List<SourceValueDto> sourceValues, List<IssueRef> issues, boolean ownerMasked) {}

    /**
     * Correction request. Only the listed canonical keys may be changed; lineage, status and geometry cannot.
     * Values are raw text and go through the same normalization as imports.
     */
    public record UpdateRequest(@NotNull Long version, @NotBlank @Size(max = 500) String reason, @NotNull Map<String, String> changes) {}

    public record ArchiveRequest(@NotBlank @Size(max = 500) String reason) {}

    public record Facets(List<String> districts, List<String> states, List<String> landTypes) {}
}
