package edu.iitp.landstack.parsing;

import edu.iitp.landstack.common.AbstractEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One traceable parse/extract run for an import job. Retries create new attempts. */
@Entity
@Table(name = "processing_attempt")
public class ProcessingAttempt extends AbstractEntity {
    public enum Outcome { RUNNING, SUCCEEDED, FAILED, TIMED_OUT, UNAVAILABLE }

    @Column(name = "import_job_id", nullable = false)
    private UUID importJobId;

    @Column(nullable = false)
    private String stage;

    @Column(nullable = false)
    private String processor;

    @Column(name = "processor_version")
    private String processorVersion;

    @Enumerated(EnumType.STRING)
    private Outcome outcome = Outcome.RUNNING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> diagnostics = new HashMap<>();

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ProcessingAttempt() {}

    public ProcessingAttempt(UUID importJobId, String stage, String processor, String processorVersion) {
        this.importJobId = importJobId;
        this.stage = stage;
        this.processor = processor;
        this.processorVersion = processorVersion;
    }

    public void finish(Outcome outcome, Map<String, Object> diagnostics) {
        this.outcome = outcome;
        this.diagnostics = diagnostics == null ? new HashMap<>() : diagnostics;
        this.finishedAt = Instant.now();
    }

    public UUID getImportJobId() { return importJobId; }
    public String getStage() { return stage; }
    public String getProcessor() { return processor; }
    public String getProcessorVersion() { return processorVersion; }
    public void setProcessorVersion(String v) { this.processorVersion = v; }
    public Outcome getOutcome() { return outcome; }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
