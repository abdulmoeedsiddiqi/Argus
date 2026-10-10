-- Argus configuration schema (owned by argus-admin). Implemented in Milestone 2.
CREATE TABLE plans (
    id               VARCHAR(64) PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    rate_capacity    INTEGER      NOT NULL CHECK (rate_capacity > 0),
    rate_period_ms   BIGINT       NOT NULL CHECK (rate_period_ms > 0),
    monthly_quota    BIGINT       NOT NULL CHECK (monthly_quota > 0)
);

CREATE TABLE consumers (
    id         VARCHAR(64) PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    plan_id    VARCHAR(64)  NOT NULL REFERENCES plans (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE api_keys (
    id          VARCHAR(64) PRIMARY KEY,
    consumer_id VARCHAR(64)  NOT NULL REFERENCES consumers (id),
    key_hash    CHAR(64)     NOT NULL UNIQUE,      -- SHA-256 hex; plain keys are never stored
    key_prefix  VARCHAR(12)  NOT NULL,             -- shown in logs instead of the key
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE routes (
    id              VARCHAR(64) PRIMARY KEY,
    path_prefix     VARCHAR(255) NOT NULL UNIQUE,
    methods         VARCHAR(64)  NOT NULL,         -- comma-separated, e.g. GET,POST
    service_id      VARCHAR(64)  NOT NULL,
    lb_policy       VARCHAR(32)  NOT NULL,
    timeout_ms      INTEGER      NOT NULL CHECK (timeout_ms BETWEEN 100 AND 60000),
    retry_attempts  INTEGER      NOT NULL DEFAULT 1 CHECK (retry_attempts BETWEEN 1 AND 5),
    canary_version  VARCHAR(32),
    canary_percent  INTEGER      CHECK (canary_percent BETWEEN 0 AND 100),
    version         BIGINT       NOT NULL DEFAULT 1
);

CREATE TABLE upstream_instances (
    id         VARCHAR(64) PRIMARY KEY,
    service_id VARCHAR(64)  NOT NULL,
    base_uri   VARCHAR(255) NOT NULL,
    weight     INTEGER      NOT NULL DEFAULT 1 CHECK (weight >= 1),
    version    VARCHAR(32)  NOT NULL DEFAULT 'v1'
);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor       VARCHAR(100) NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(30)  NOT NULL,
    entity_id   VARCHAR(64)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
