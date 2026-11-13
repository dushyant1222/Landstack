package edu.iitp.landstack.parsing;

import edu.iitp.landstack.common.AbstractEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A staged source row. Holds raw values exactly as read, optional operator corrections, and the
 * normalization/validation result. Never a confirmed land record.
 */
@Entity
@Table(name = "import_row")
public class ImportRow extends AbstractEntity {
    public enum State { PENDING, VALID, WARNING, BLOCKED, EXCLUDED }

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_values", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> rawValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "corrected_values", columnDefinition = "jsonb")
    private Map<String, String> correctedValues;

    @Column(name = "page_ref")
    private Integer pageRef;

    @Column(name = "extraction_method")
    private String extractionMethod;

    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> normalized;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> validation;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_state", nullable = false)
    private State rowState = State.PENDING;

    @Column(nullable = false)
    private boolean excluded;

    /** Operator has reviewed this (machine-extracted) row; suppresses EXTRACTION_LOW_CONFIDENCE. */
    @Column(nullable = false)
    private boolean reviewed;

    protected ImportRow() {}

    public ImportRow(UUID importJobId, UUID attemptId, int rowNumber, Map<String, String> rawValues,
                     Integer pageRef, String extractionMethod, BigDecimal confidence) {
        this.importJobId = importJobId;
        this.attemptId = attemptId;
        this.rowNumber = rowNumber;
        this.rawValues = rawValues;
        this.pageRef = pageRef;
        this.extractionMethod = extractionMethod;
        this.confidence = confidence;
    }

    /** Effective input for normalization: raw values overlaid with operator corrections. */
    public Map<String, String> effectiveValues() {
        if (correctedValues == null || correctedValues.isEmpty()) return rawValues;
        var m = new java.util.LinkedHashMap<>(rawValues);
        m.putAll(correctedValues);
        return m;
    }

    public UUID getImportJobId() { return importJobId; }
    public UUID getAttemptId() { return attemptId; }
    public int getRowNumber() { return rowNumber; }
    public Map<String, String> getRawValues() { return rawValues; }
    public Map<String, String> getCorrectedValues() { return correctedValues; }
    public void setCorrectedValues(Map<String, String> v) { this.correctedValues = v; }
    public Integer getPageRef() { return pageRef; }
    public String getExtractionMethod() { return extractionMethod; }
    public BigDecimal getConfidence() { return confidence; }
    public Map<String, Object> getNormalized() { return normalized; }
    public List<Map<String, Object>> getValidation() { return validation; }
    public State getRowState() { return rowState; }
    public boolean isExcluded() { return excluded; }
    public void setExcluded(boolean excluded) { this.excluded = excluded; }
    public boolean isReviewed() { return reviewed; }
    public void setReviewed(boolean reviewed) { this.reviewed = reviewed; }

    public void applyResult(Map<String, Object> normalized, List<Map<String, Object>> validation, State state) {
        this.normalized = normalized;
        this.validation = validation;
        this.rowState = excluded ? State.EXCLUDED : state;
    }
}
