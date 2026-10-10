# Argus: Distributed API Gateway

Argus is a horizontally scalable API gateway written in Java 21. It provides routing, load
balancing, authentication, distributed rate limiting and circuit breaking, and adds
**cross-instance traffic intelligence**: every gateway instance streams request events to Kafka,
so independent services can meter usage cluster-wide, detect abuse that no single instance can
see, and push enforcement decisions and configuration back to every instance.

> SE423 (Software Construction and Development) and SE431 (Software Quality Assurance)
> joint semester project, GIKI. Author: Abdul Moeed Siddiqi (2023009).

## Status

| Milestone | Scope | State |
|---|---|---|
| M1 (Week 6) | Design, contracts, repository and CI setup | In progress |
| M2 (Week 10) | Gateway data plane: modules M1 to M5 | Planned |
| M3 (Week 13) | Metering, abuse detection, deployment, failure-injection demo | Planned |

At M1 the repository contains the **contracts and interfaces** of the design (`argus-core`,
`argus-contracts`). Implementations are added **test-first** from Milestone 2, so the
red-green-refactor history is visible in the commit log.

## Architecture

```
Clients -> Nginx -> Argus Gateway x3 -> Upstream services (Users, Orders, Products)
                        |   |
                        |   +-- Redis (shared rate-limit counters)
                        +------ Kafka: request-events / enforcement / config
                                  |-> Metering & Quota Service -> PostgreSQL
                                  |-> Abuse Detection Service
                                  +<- Admin API (config changes)  -> PostgreSQL
```

Diagrams (PlantUML sources) are in [`docs/diagrams`](docs/diagrams); design decisions are recorded
as ADRs in [`docs/adr`](docs/adr).

## Repository layout

| Path | Purpose |
|---|---|
| `argus-contracts` | Event records shared by all services (`RequestEvent`, `EnforcementDecision`, `ConfigChange`, `Topics`) |
| `argus-core` | Framework-free business logic: filter pipeline, routing, load balancing, rate limiting, circuit breaker, ports |
| `argus-gateway` | Spring Boot data plane: HTTP proxy and adapters for Redis, Kafka and JWT |
| `argus-admin` | Admin API for routes, consumers, plans and API keys (PostgreSQL, Flyway) |
| `argus-metering` | Usage ledger and monthly quota enforcement (Kafka consumer) |
| `argus-abuse` | Cross-instance abuse detection (Kafka consumer) |
| `upstreams/mock-service` | Demo upstream run as Users, Orders and Products, with a fault-injection endpoint |
| `deploy` | Docker Compose, Nginx, Prometheus; Kubernetes manifests from M3 |
| `docs` | ADRs, diagrams, branching strategy |

## Prerequisites

- JDK 21 (Eclipse Temurin recommended)
- Maven 3.9+
- Docker Desktop with Compose v2 (on Windows, cap WSL2 memory in `%UserProfile%\.wslconfig`)
- Git

## Build and test

```bash
mvn -B verify          # compile, run all tests, JaCoCo coverage report, Checkstyle
```

Coverage reports: `*/target/site/jacoco/index.html`.

## Run locally

```bash
# Infrastructure only, for day-to-day development (PostgreSQL, Redis, Kafka)
docker compose -f deploy/docker-compose.yml --profile core up -d

# Full stack: 3 gateways behind Nginx, services, monitoring (from Milestone 2)
mvn -B -DskipTests package
docker compose -f deploy/docker-compose.yml --profile full up -d --build
```

| Service | URL |
|---|---|
| Gateway (via Nginx) | http://localhost:8000 |
| Admin API / Swagger UI | http://localhost:8081/swagger-ui.html (from M2) |
| Grafana | http://localhost:3000 |
| Kafka UI | http://localhost:8090 |
| Prometheus | http://localhost:9090 |

## Development workflow

- Branching strategy, commit conventions and quality gates: [`docs/BRANCHING.md`](docs/BRANCHING.md)
- Contribution rules and TDD commit rhythm: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- AI assistance is disclosed in [`AI_USAGE_LOG.md`](AI_USAGE_LOG.md)
