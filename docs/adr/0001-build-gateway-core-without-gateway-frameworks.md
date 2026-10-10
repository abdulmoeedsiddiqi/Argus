# ADR-0001: Build the gateway core without Spring Cloud Gateway or Resilience4j

- Status: Accepted
- Date: 2026-10-10

## Context

The course grades design patterns and construction techniques that the team designs, implements and defends.
Spring Cloud Gateway already provides the filter chain, routing and proxying; Resilience4j provides circuit breakers,
retries and rate limiters. Using them would mean the central patterns of the system are library code.

## Decision

Implement the filter pipeline (Chain of Responsibility), load-balancing strategies (Strategy), circuit breaker
(State) and rate limiters ourselves in `argus-core`. Use Spring Boot only as the runtime host and for adapters
(web server, Kafka, Redis, JDBC).

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Spring Cloud Gateway | Mature and fast, but provides the patterns we must build; its default stack is reactive (WebFlux), which makes code harder to test and explain. |
| Resilience4j | Production-grade breaker and retry, but replaces the State and Strategy designs we need to demonstrate. |
| Netflix Zuul | Zuul 1 is in maintenance mode and uses a blocking thread-per-request model. |

## Consequences

More code to write and test; in exchange every pattern is ours, testable in isolation, and defensible in the demo.
