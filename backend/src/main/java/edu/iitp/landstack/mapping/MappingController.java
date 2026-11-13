package edu.iitp.landstack.mapping;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.mapping.MappingDtos.*;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Field mapping & normalization")
public class MappingController {
    private final MappingService service;
    private final FieldMappingTemplateRepository templates;
    private final ImportMappingRepository mappings;
    private final AuditService audit;

    public MappingController(MappingService service, FieldMappingTemplateRepository templates,
                             ImportMappingRepository mappings, AuditService audit) {
        this.service = service;
        this.templates = templates;
        this.mappings = mappings;
        this.audit = audit;
    }

    @GetMapping("/canonical-fields")
    public List<CanonicalFieldDto> canonicalFields() {
        return Arrays.stream(CanonicalField.values()).map(f -> new CanonicalFieldDto(f.key(), f.label(), f.kind())).toList();
    }

    @GetMapping("/imports/{id}/mapping/suggestions")
    public SuggestionsDto suggestions(@PathVariable UUID id) {
        return service.suggestions(id);
    }

    @PutMapping("/imports/{id}/mapping")
    public PreviewSummary saveMapping(@PathVariable UUID id, @Valid @RequestBody MappingRequest req) {
        return service.applyMapping(id, req);
    }

    @GetMapping("/imports/{id}/preview-summary")
    public PreviewSummary summary(@PathVariable UUID id) {
        return service.summary(id);
    }

    @PatchMapping("/imports/{id}/rows/{rowId}")
    public PreviewSummary correct(@PathVariable UUID id, @PathVariable UUID rowId, @RequestBody CorrectionRequest req) {
        return service.correctRow(id, rowId, req);
    }

    @GetMapping("/mapping-templates")
    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public List<TemplateDto> listTemplates() {
        return templates.findByActiveTrueOrderByNameAscVersionDesc().stream().map(TemplateDto::from).toList();
    }

    @GetMapping("/mapping-templates/{templateId}")
    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public TemplateDto getTemplate(@PathVariable UUID templateId) {
        return TemplateDto.from(templates.findById(templateId).orElseThrow(() -> ApiException.notFound("Template")));
    }

    /** Saves the mapping of an import as a template. Re-using a name creates a new version. */
    @PostMapping("/mapping-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public TemplateDto createTemplate(@Valid @RequestBody TemplateRequest req) {
        ImportMapping m = mappings.findById(req.fromImportJobId())
                .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "Save a mapping for this import first."));
        String name = req.name().strip();
        int version = templates.maxVersion(name) + 1;
        FieldMappingTemplate t = templates.save(new FieldMappingTemplate(name, req.description(), version,
                Map.copyOf(m.getMapping()), Map.copyOf(m.getOptions()), CurrentUser.id()));
        audit.record("TEMPLATE_SAVED", "MAPPING_TEMPLATE", t.getId(), Map.of("version", version));
        return TemplateDto.from(t);
    }

    @DeleteMapping("/mapping-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Perms.OPERATE)
    @Transactional
    public void archiveTemplate(@PathVariable UUID templateId) {
        FieldMappingTemplate t = templates.findById(templateId).orElseThrow(() -> ApiException.notFound("Template"));
        t.setActive(false);
        audit.record("TEMPLATE_ARCHIVED", "MAPPING_TEMPLATE", t.getId(), Map.of());
    }
}
