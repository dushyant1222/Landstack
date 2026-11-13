-- LandStack V3: uploaded source files and import jobs
CREATE TABLE source_file (
    id                    UUID PRIMARY KEY,
    original_filename     TEXT        NOT NULL,
    storage_key           TEXT        NOT NULL UNIQUE,
    detected_type         TEXT        NOT NULL CHECK (detected_type IN ('CSV','XLSX','PDF')),
    declared_content_type TEXT,
    size_bytes            BIGINT      NOT NULL CHECK (size_bytes > 0),
    sha256                TEXT        NOT NULL CHECK (length(sha256) = 64),
    uploaded_by           UUID        NOT NULL REFERENCES app_user (id),
    uploaded_at           TIMESTAMPTZ NOT NULL,
    purged_at             TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_source_file_sha256 ON source_file (sha256);

CREATE TABLE import_job (
    id                   UUID PRIMARY KEY,
    source_file_id       UUID        NOT NULL REFERENCES source_file (id),
    created_by           UUID        NOT NULL REFERENCES app_user (id),
    status               TEXT        NOT NULL CHECK (status IN (
                           'RECEIVED','VALIDATING','PARSING','AWAITING_SHEET_SELECTION','AWAITING_MAPPING',
                           'PREVIEW_READY','CONFIRMING','COMPLETED','COMPLETED_WITH_WARNINGS',
                           'FAILED','EXPIRED','CANCELLED','REMOVED')),
    status_code          TEXT,
    status_message       TEXT,
    retryable            BOOLEAN     NOT NULL DEFAULT FALSE,
    sheet_names          JSONB,
    selected_sheet       TEXT,
    header_row           INT CHECK (header_row IS NULL OR header_row >= 1),
    source_columns       JSONB,
    duplicate_of_job_id  UUID REFERENCES import_job (id) ON DELETE SET NULL,
    total_rows           INT,
    valid_rows           INT,
    warning_rows         INT,
    rejected_rows        INT,
    imported_rows        INT,
    result_summary       JSONB,
    confirmation_key     UUID UNIQUE,
    confirmed_by         UUID REFERENCES app_user (id),
    confirmed_at         TIMESTAMPTZ,
    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_import_job_status ON import_job (status);
CREATE INDEX ix_import_job_created ON import_job (created_at DESC);
CREATE INDEX ix_import_job_creator ON import_job (created_by);
CREATE INDEX ix_import_job_source_file ON import_job (source_file_id);
