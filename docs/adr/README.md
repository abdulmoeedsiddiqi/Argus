# Architecture Decision Records

| ADR | Decision |
|---|---|
| [0001](0001-build-gateway-core-without-gateway-frameworks.md) | Build the gateway core without Spring Cloud Gateway or Resilience4j |
| [0002](0002-kafka-as-control-plane.md) | Use Kafka as the cross-instance control plane |
| [0003](0003-atomic-distributed-rate-limiting.md) | Atomic distributed rate limiting with Redis Lua scripts |
| [0004](0004-framework-free-core.md) | Keep business logic in a framework-free core (ports and adapters) |
| [0005](0005-at-least-once-with-idempotent-consumers.md) | At-least-once delivery with idempotent consumers |
| [0006](0006-virtual-threads-over-reactive.md) | Use Java 21 virtual threads instead of a reactive stack |

New ADRs are numbered sequentially and never deleted; a superseded ADR is marked "Superseded by ADR-xxxx".
