# ADR-0005: At-least-once delivery with idempotent consumers

- Status: Accepted
- Date: 2026-10-10

## Context

Exactly-once delivery across process failures is not achievable end to end. Losing usage events is unacceptable; counting
an event twice is also unacceptable.

## Decision

Producers use `acks=all` and idempotence. Consumers commit offsets only after the database write succeeds, and
deduplicate by `eventId` (primary key, `ON CONFLICT DO NOTHING`). If Kafka is down, the gateway spills events to a local
file that a background task replays.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| At-most-once (commit before processing) | Loses events when a consumer crashes mid-batch. |
| Kafka transactions end to end | More complex and still does not cover the external database write. |

## Consequences

Requires a unique event ID and a deduplication check; gives effectively-once counting.
