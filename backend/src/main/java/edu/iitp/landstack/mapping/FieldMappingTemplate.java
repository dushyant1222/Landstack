package edu.iitp.landstack.mapping;

import edu.iitp.landstack.common.AbstractEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/** Reusable, versioned mapping for a known source format. Saving with an existing name creates a new version. */
@Entity
@Table(name = "field_mapping_template")
public class FieldMappingTemplate extends AbstractEntity {
    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> mapping;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> options;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private boolean active = true;

    protected FieldMappingTemplate() {}

    public FieldMappingTemplate(String name, String description, int version, Map<String, String> mapping,
                                Map<String, Object> options, UUID createdBy) {
        this.name = name;
        this.description = description;
        this.version = version;
        this.mapping = mapping;
        this.options = options;
        this.createdBy = createdBy;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getVersion() { return version; }
    public Map<String, String> getMapping() { return mapping; }
    public Map<String, Object> getOptions() { return options; }
    public UUID getCreatedBy() { return createdBy; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
