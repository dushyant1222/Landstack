-- LandStack V5: mapping templates and the exact mapping snapshot used per import
CREATE TABLE field_mapping_template (
    id           UUID PRIMARY KEY,
    name         TEXT        NOT NULL,
    description  TEXT,
    version      INT         NOT NULL CHECK (version >= 1),
    mapping      JSONB       NOT NULL,
    options      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_by   UUID        NOT NULL REFERENCES app_user (id),
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_template_name_version UNIQUE (name, version)
);

CREATE TABLE import_mapping (
    import_job_id         UUID PRIMARY KEY REFERENCES import_job (id) ON DELETE CASCADE,
    template_id           UUID REFERENCES field_mapping_template (id) ON DELETE SET NULL,
    template_version      INT,
    mapping               JSONB       NOT NULL,
    options               JSONB       NOT NULL DEFAULT '{}'::jsonb,
    normalization_version TEXT        NOT NULL,
    mapped_by             UUID        NOT NULL REFERENCES app_user (id),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);
