-- LandStack V7: data-quality issues (potential issues for human review, not legal findings)
CREATE TABLE quality_issue (
    id                 UUID PRIMARY KEY,
    land_record_id     UUID REFERENCES land_record (id) ON DELETE CASCADE,
    import_job_id      UUID REFERENCES import_job (id) ON DELETE CASCADE,
    rule_id            TEXT        NOT NULL,
    rule_version       TEXT        NOT NULL,
    source             TEXT        NOT NULL CHECK (source IN ('RULE','STATISTICAL')),
    category           TEXT        NOT NULL,
    field              TEXT,
    severity           TEXT        NOT NULL CHECK (severity IN ('INFO','WARNING','HIGH')),
    observed_value     TEXT,
    reason             TEXT        NOT NULL,
    related_record_ids JSONB       NOT NULL DEFAULT '[]'::jsonb,
    dedup_key          TEXT        NOT NULL UNIQUE,
    status             TEXT        NOT NULL CHECK (status IN ('OPEN','REVIEWED','RESOLVED','DISMISSED')),
    reviewer_id        UUID REFERENCES app_user (id),
    reviewer_note      TEXT,
    reviewed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_quality_issue_status ON quality_issue (status, severity);
CREATE INDEX ix_quality_issue_record ON quality_issue (land_record_id);
CREATE INDEX ix_quality_issue_job ON quality_issue (import_job_id);
CREATE INDEX ix_quality_issue_rule ON quality_issue (rule_id);
