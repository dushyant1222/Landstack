package edu.iitp.landstack.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Append-only audit record. Summaries must be privacy-safe: no credentials, tokens or owner names. */
@Entity
@Table(name = "audit_event")
public class AuditEvent implements Persistable<UUID> {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summary;

    @Column(name = "ip_hash")
    private String ipHash;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Transient
    private boolean isNew = true;

    protected AuditEvent() {}

    public AuditEvent(UUID actorId, String actorRole, String action, String targetType, UUID targetId,
                      Map<String, Object> summary, String ipHash) {
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = summary;
        this.ipHash = ipHash;
        this.occurredAt = Instant.now();
    }

    @PostLoad @PostPersist void markNotNew() { isNew = false; }
    @Override public boolean isNew() { return isNew; }
    @Override public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public Map<String, Object> getSummary() { return summary; }
    public Instant getOccurredAt() { return occurredAt; }
}
