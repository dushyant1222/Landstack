package edu.iitp.landstack.mapping;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Exact mapping snapshot (columns + options + normalization version) used for one import. */
@Entity
@Table(name = "import_mapping")
public class ImportMapping implements Persistable<UUID> {
    @Id
    @Column(name = "import_job_id")
    private UUID importJobId;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_version")
    private Integer templateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> mapping;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> options;

    @Column(name = "normalization_version", nullable = false)
    private String normalizationVersion;

    @Column(name = "mapped_by", nullable = false)
    private UUID mappedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    protected ImportMapping() {}

    public ImportMapping(UUID importJobId) {
        this.importJobId = importJobId;
        this.createdAt = Instant.now();
    }

    public void update(MappingConfig cfg, UUID templateId, Integer templateVersion, UUID by) {
        this.mapping = cfg.columnsByKey();
        this.options = cfg.optionsMap();
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.normalizationVersion = MappingConfig.NORMALIZATION_VERSION;
        this.mappedBy = by;
        this.updatedAt = Instant.now();
    }

    public MappingConfig toConfig() {
        return MappingConfig.fromStored(mapping, options);
    }

    @PostLoad @PostPersist void markNotNew() { isNew = false; }
    @Override public boolean isNew() { return isNew; }
    @Override public UUID getId() { return importJobId; }
    public UUID getTemplateId() { return templateId; }
    public Integer getTemplateVersion() { return templateVersion; }
    public Map<String, String> getMapping() { return mapping; }
    public Map<String, Object> getOptions() { return options; }
    public String getNormalizationVersion() { return normalizationVersion; }
    public UUID getMappedBy() { return mappedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
