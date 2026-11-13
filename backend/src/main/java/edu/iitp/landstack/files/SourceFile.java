package edu.iitp.landstack.files;

import edu.iitp.landstack.common.AbstractEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_file")
public class SourceFile extends AbstractEntity {
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /** Server-generated relative key. Never returned by the API. */
    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_type", nullable = false)
    private DetectedType detectedType;

    @Column(name = "declared_content_type")
    private String declaredContentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "purged_at")
    private Instant purgedAt;

    protected SourceFile() {}

    public SourceFile(String originalFilename, String storageKey, DetectedType type, String declaredContentType,
                      long sizeBytes, String sha256, UUID uploadedBy) {
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.detectedType = type;
        this.declaredContentType = declaredContentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public String getOriginalFilename() { return originalFilename; }
    public String getStorageKey() { return storageKey; }
    public DetectedType getDetectedType() { return detectedType; }
    public String getDeclaredContentType() { return declaredContentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getPurgedAt() { return purgedAt; }
    public void markPurged() { this.purgedAt = Instant.now(); }
}
