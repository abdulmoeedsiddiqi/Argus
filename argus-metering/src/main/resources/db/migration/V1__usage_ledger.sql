-- Usage ledger (owned by argus-metering). event_id as primary key makes inserts idempotent:
-- INSERT ... ON CONFLICT (event_id) DO NOTHING ignores redelivered events.
CREATE TABLE usage_ledger (
    event_id            UUID PRIMARY KEY,
    api_key_id          VARCHAR(64) NOT NULL,
    consumer_id         VARCHAR(64) NOT NULL,
    route_id            VARCHAR(64),
    status              INTEGER     NOT NULL,
    latency_ms          BIGINT      NOT NULL,
    gateway_instance_id VARCHAR(64) NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_usage_key_month ON usage_ledger (api_key_id, occurred_at);

CREATE TABLE usage_monthly (
    api_key_id    VARCHAR(64) NOT NULL,
    billing_month DATE        NOT NULL,
    request_count BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (api_key_id, billing_month)
);
