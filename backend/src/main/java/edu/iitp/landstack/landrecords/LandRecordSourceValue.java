package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Append-only lineage: raw source value, normalized value and the transformation applied. */
@Entity
@Table(name = "land_record_source_value")
public class LandRecordSourceValue extends AbstractEntity {
    public static final String MANUAL_CORRECTION = "MANUAL_CORRECTION";

    @Column(name = "land_record_id", nullable = false, updatable = false) private UUID landRecordId;
    @Column(name = "canonical_field", nullable = false, updatable = false) private String canonicalField;
    @Column(name = "source_column", updatable = false) private String sourceColumn;
    @Column(name = "raw_value", updatable = false) private String rawValue;
    @Column(name = "normalized_value", updatable = false) private String normalizedValue;
    @Column(nullable = false, updatable = false) private String transformation;
    @Column(name = "changed_by", updatable = false) private UUID changedBy;

    protected LandRecordSourceValue() {}

    public LandRecordSourceValue(UUID landRecordId, String canonicalField, String sourceColumn, String rawValue,
                                 String normalizedValue, String transformation, UUID changedBy) {
        this.landRecordId = landRecordId;
        this.canonicalField = canonicalField;
        this.sourceColumn = sourceColumn;
        this.rawValue = rawValue;
        this.normalizedValue = normalizedValue;
        this.transformation = transformation;
        this.changedBy = changedBy;
    }

    public UUID getLandRecordId() { return landRecordId; }
    public String getCanonicalField() { return canonicalField; }
    public String getSourceColumn() { return sourceColumn; }
    public String getRawValue() { return rawValue; }
    public String getNormalizedValue() { return normalizedValue; }
    public String getTransformation() { return transformation; }
    public UUID getChangedBy() { return changedBy; }
}
