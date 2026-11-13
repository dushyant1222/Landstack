-- LandStack V6: confirmed normalized land records and their source-value lineage
CREATE TABLE land_record (
    id                   UUID PRIMARY KEY,
    parcel_id            TEXT,
    survey_khasra_no     TEXT,
    owner_name           TEXT,
    land_area_value      NUMERIC(18,4) CHECK (land_area_value IS NULL OR land_area_value >= 0),
    land_area_unit_raw   TEXT,
    land_area_unit       TEXT,
    land_area_sqm        NUMERIC(20,4) CHECK (land_area_sqm IS NULL OR land_area_sqm >= 0),
    land_type_raw        TEXT,
    land_type_category   TEXT,
    registration_ref     TEXT,
    registration_date    DATE,
    village_locality     TEXT,
    tehsil               TEXT,
    district             TEXT,
    state                TEXT,
    latitude             NUMERIC(9,6) CHECK (latitude IS NULL OR (latitude BETWEEN -90 AND 90)),
    longitude            NUMERIC(9,6) CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180)),
    geometry             JSONB,
    mappable             BOOLEAN     NOT NULL DEFAULT FALSE,
    source_file_id       UUID        NOT NULL REFERENCES source_file (id),
    import_job_id        UUID        NOT NULL REFERENCES import_job (id),
    source_row_number    INT         NOT NULL,
    source_page          INT,
    record_status        TEXT        NOT NULL CHECK (record_status IN ('CONFIRMED','ARCHIVED')),
    archived_reason      TEXT,
    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_land_record_job_row UNIQUE (import_job_id, source_row_number),
    CONSTRAINT ck_land_record_mappable CHECK (mappable = (latitude IS NOT NULL AND longitude IS NOT NULL))
);
CREATE INDEX ix_land_record_parcel ON land_record (lower(parcel_id));
CREATE INDEX ix_land_record_khasra ON land_record (lower(survey_khasra_no));
CREATE INDEX ix_land_record_locality ON land_record (lower(district), lower(tehsil), lower(village_locality));
CREATE INDEX ix_land_record_state ON land_record (lower(state));
CREATE INDEX ix_land_record_job ON land_record (import_job_id);
CREATE INDEX ix_land_record_source ON land_record (source_file_id);
CREATE INDEX ix_land_record_type ON land_record (land_type_category);
CREATE INDEX ix_land_record_created ON land_record (created_at);
CREATE INDEX ix_land_record_mappable_coords ON land_record (latitude, longitude) WHERE mappable;

CREATE TABLE land_record_source_value (
    id               UUID PRIMARY KEY,
    land_record_id   UUID        NOT NULL REFERENCES land_record (id) ON DELETE CASCADE,
    canonical_field  TEXT        NOT NULL,
    source_column    TEXT,
    raw_value        TEXT,
    normalized_value TEXT,
    transformation   TEXT        NOT NULL,
    changed_by       UUID REFERENCES app_user (id),
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_source_value_record ON land_record_source_value (land_record_id, created_at);
