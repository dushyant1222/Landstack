package edu.iitp.landstack.quality;

import edu.iitp.landstack.common.AbstractEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A potential data-quality issue for human review. It is never a legal determination and never
 * changes, merges or deletes the record it refers to.
 */
@Entity
@Table(name = "quality_issue")
public class QualityIssue extends AbstractEntity {
    public enum Source { RULE, STATISTICAL }

    @Column(name = "land_record_id") private UUID landRecordId;
    @Column(name = "import_job_id") private UUID importJobId;
    @Column(name = "rule_id", nullable = false) private String ruleId;
    @Column(name = "rule_version", nullable = false) private String ruleVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Source source;
    @Column(nullable = false) private String category;
    private String field;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Severity severity;
    @Column(name = "observed_value") private String observedValue;
    @Column(nullable = false) private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_record_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> relatedRecordIds = List.of();

    @Column(name = "dedup_key", nullable = false, unique = true) private String dedupKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private IssueStatus status = IssueStatus.OPEN;
    @Column(name = "reviewer_id") private UUID reviewerId;
    @Column(name = "reviewer_note") private String reviewerNote;
    @Column(name = "reviewed_at") private Instant reviewedAt;

    protected QualityIssue() {}

    public QualityIssue(UUID landRecordId, UUID importJobId, String ruleId, String ruleVersion, Source source, String category,
                        String field, Severity severity, String observedValue, String reason, List<UUID> related, String dedupKey) {
        this.landRecordId = landRecordId;
        this.importJobId = importJobId;
        this.ruleId = ruleId;
        this.ruleVersion = ruleVersion;
        this.source = source;
        this.category = category;
        this.field = field;
        this.severity = severity;
        this.observedValue = observedValue;
        this.reason = reason;
        this.relatedRecordIds = related == null ? List.of() : related;
        this.dedupKey = dedupKey;
    }

    public void review(IssueStatus status, String note, UUID reviewer) {
        this.status = status;
        this.reviewerNote = note;
        this.reviewerId = reviewer;
        this.reviewedAt = Instant.now();
    }

    public UUID getLandRecordId() { return landRecordId; }
    public UUID getImportJobId() { return importJobId; }
    public String getRuleId() { return ruleId; }
    public String getRuleVersion() { return ruleVersion; }
    public Source getSource() { return source; }
    public String getCategory() { return category; }
    public String getField() { return field; }
    public Severity getSeverity() { return severity; }
    public String getObservedValue() { return observedValue; }
    public String getReason() { return reason; }
    public List<UUID> getRelatedRecordIds() { return relatedRecordIds; }
    public String getDedupKey() { return dedupKey; }
    public IssueStatus getStatus() { return status; }
    public UUID getReviewerId() { return reviewerId; }
    public String getReviewerNote() { return reviewerNote; }
    public Instant getReviewedAt() { return reviewedAt; }
}
