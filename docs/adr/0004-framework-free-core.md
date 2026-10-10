# ADR-0004: Keep business logic in a framework-free core (ports and adapters)

- Status: Accepted
- Date: 2026-10-10

## Context

Business rules must be unit-testable in milliseconds and independent of Kafka, Redis and Spring so that coverage targets
are realistic and the Dependency Inversion Principle is demonstrable.

## Decision

`argus-core` depends only on the JDK and `argus-contracts`. It defines ports (`EventPublisher`, `ApiKeyStore`,
`EnforcementView`, `RateLimiter`) that adapters in `argus-gateway` implement. An ArchUnit test fails the build if core
imports Spring, Kafka, Redis or JPA classes.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Layered Spring application | Business logic would be tied to framework annotations and need Spring context in tests. |

## Consequences

Slightly more interfaces; much faster tests and a clear boundary to defend.
