# ADR-0003: Atomic distributed rate limiting with Redis Lua scripts

- Status: Accepted
- Date: 2026-10-10

## Context

Rate limits must be exact across instances. A read followed by a separate write is a race condition: two instances can
both read "1 token left" and both allow a request.

## Decision

Implement the token bucket as a single Redis Lua script executed atomically. If Redis is unreachable,
`FallbackRateLimiter` (decorator) switches to a per-instance local limiter with capacity divided by the instance count.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Per-instance in-memory limits | A client can get N times its limit by spreading requests over N instances. |
| Redis INCR + EXPIRE in two calls | Not atomic as a pair; fixed windows also allow bursts at window edges. |
| Database row locks | Too slow for a per-request check. |

## Consequences

Redis becomes a dependency of the hot path, mitigated by the local fallback (graceful degradation).
