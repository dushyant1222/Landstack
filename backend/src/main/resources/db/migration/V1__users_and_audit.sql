-- LandStack V1: users and append-only audit trail
CREATE TABLE app_user (
    id             UUID PRIMARY KEY,
    email          TEXT        NOT NULL,
    display_name   TEXT        NOT NULL,
    password_hash  TEXT        NOT NULL,
    role           TEXT        NOT NULL CHECK (role IN ('ADMINISTRATOR','DATA_OPERATOR','READ_ONLY_REVIEWER')),
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT app_user_email_lower CHECK (email = lower(email))
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (email);

CREATE TABLE audit_event (
    id           UUID PRIMARY KEY,
    actor_id     UUID REFERENCES app_user (id) ON DELETE SET NULL,
    actor_role   TEXT,
    action       TEXT        NOT NULL,
    target_type  TEXT,
    target_id    UUID,
    summary      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ip_hash      TEXT,
    occurred_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_audit_event_occurred ON audit_event (occurred_at DESC);
CREATE INDEX ix_audit_event_actor ON audit_event (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_event_target ON audit_event (target_type, target_id);
CREATE INDEX ix_audit_event_action ON audit_event (action);
