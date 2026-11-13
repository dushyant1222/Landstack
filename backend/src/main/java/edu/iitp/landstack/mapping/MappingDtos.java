package edu.iitp.landstack.mapping;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MappingDtos {
    private MappingDtos() {}

    public enum SuggestionStatus { SUGGESTED, AMBIGUOUS, NONE }

    public record FieldSuggestion(String field, String label, CanonicalField.Kind kind, SuggestionStatus status,
                                  String suggestedColumn, List<String> candidates) {}

    public record SuggestionsDto(List<String> sourceColumns, Map<String, List<String>> sampleValues,
                                 List<FieldSuggestion> fields, CurrentMappingDto current) {}

    public record CurrentMappingDto(Map<String, String> mapping, Map<String, Object> options, String normalizationVersion,
                                    UUID templateId, Integer templateVersion, Instant updatedAt) {}

    /** @param mapping canonical field key → source column (omit a key to leave the field unmapped) */
    public record MappingRequest(@NotNull Map<String, String> mapping,
                                 boolean dayFirst,
                                 @Size(max = 60) String declaredAreaUnit,
                                 @Size(max = 80) String declaredState,
                                 Boolean requireIdentifier,
                                 boolean requireDistrict,
                                 UUID templateId) {}

    public record CorrectionRequest(Map<String, String> values, Boolean excluded, Boolean reviewed) {}

    public record PreviewSummary(int totalRows, int validRows, int warningRows, int blockedRows, int excludedRows,
                                 Map<String, Long> issueCounts) {}

    public record TemplateRequest(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description,
                                  @NotNull UUID fromImportJobId) {}

    public record TemplateDto(UUID id, String name, String description, int version, Map<String, String> mapping,
                              Map<String, Object> options, Instant createdAt) {
        public static TemplateDto from(FieldMappingTemplate t) {
            return new TemplateDto(t.getId(), t.getName(), t.getDescription(), t.getVersion(), t.getMapping(), t.getOptions(), t.getCreatedAt());
        }
    }

    public record CanonicalFieldDto(String key, String label, CanonicalField.Kind kind) {}
}
