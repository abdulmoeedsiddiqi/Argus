# ADR-0006: Use Java 21 virtual threads instead of a reactive stack

- Status: Accepted
- Date: 2026-10-10

## Context

The gateway is I/O bound and must handle many concurrent requests. The concurrency model must be explainable and testable.

## Decision

Use blocking, readable code on virtual threads (Spring Boot `spring.threads.virtual.enabled=true`, `java.net.http.HttpClient`).
Avoid `synchronized` on hot paths (it pins virtual threads in Java 21); use `ReentrantLock`, atomics and immutable snapshots.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Spring WebFlux / Reactor | High throughput, but callback-style code is harder to test, debug and defend. |
| Platform thread pool | Thread count caps concurrency; slow upstreams exhaust the pool. |

## Consequences

Simple code with high concurrency; requires care to avoid pinning.
