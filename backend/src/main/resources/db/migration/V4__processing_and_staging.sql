-- LandStack V4: processing attempts and staged (unconfirmed) preview rows
CREATE TABLE processing_attempt (
    id                UUID PRIMARY KEY,
    import_job_id     UUID        NOT NULL REFERENCES import_job (id) ON DELETE CASCADE,
    stage             TEXT        NOT NULL CHECK (stage IN ('PARSE','EXTRACT','NORMALIZE','CONFIRM')),
    processor         TEXT        NOT NULL,
    processor_version TEXT,
    outcome           TEXT        CHECK (outcome IN ('RUNNING','SUCCEEDED','FAILED','TIMED_OUT','UNAVAILABLE')),
    diagnostics       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    started_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_processing_attempt_job ON processing_attempt (import_job_id, started_at DESC);

CREATE TABLE import_row (
    id                UUID PRIMARY KEY,
    import_job_id     UUID        NOT NULL REFERENCES import_job (id) ON DELETE CASCADE,
    attempt_id        UUID        NOT NULL REFERENCES processing_attempt (id) ON DELETE CASCADE,
    row_number        INT         NOT NULL CHECK (row_number >= 1),
    raw_values        JSONB       NOT NULL,
    corrected_values  JSONB,
    page_ref          INT,
    extraction_method TEXT,
    confidence        NUMERIC(4,3) CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    normalized        JSONB,
    validation        JSONB,
    row_state         TEXT        NOT NULL DEFAULT 'PENDING'
                      CHECK (row_state IN ('PENDING','VALID','WARNING','BLOCKED','EXCLUDED')),
    excluded          BOOLEAN     NOT NULL DEFAULT FALSE,
    reviewed          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_import_row_attempt_row UNIQUE (attempt_id, row_number)
);
CREATE INDEX ix_import_row_job ON import_row (import_job_id, row_number);
