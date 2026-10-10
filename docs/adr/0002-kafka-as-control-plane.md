# ADR-0002: Use Kafka as the cross-instance control plane

- Status: Accepted
- Date: 2026-10-10

## Context

With three gateway instances, each sees only part of the traffic. Usage metering, abuse detection and configuration
propagation need a durable, replayable, shared stream that tolerates consumers and producers restarting.

## Decision

Use Apache Kafka (KRaft mode): `gateway.request-events` (7-day retention, key = apiKeyId),
`gateway.enforcement` and `gateway.config` (log-compacted, latest value per key).

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Direct database writes | Puts the database on every request path; a database outage loses usage data or stalls the gateway. |
| Redis Pub/Sub | Fire-and-forget: an instance that is offline misses messages forever, so a revoked key could stay valid on it. |
| Redis Streams | Closest alternative. Rejected because history is memory-bound (7-day replay is costly) and it lacks compacted state topics. |
| RabbitMQ | Messages are removed once consumed, so independent consumers cannot replay or fan out as simply. |

## Consequences

One more piece of infrastructure to operate and test; enables replay, independent consumers and crash-safe configuration.
