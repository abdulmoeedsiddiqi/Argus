<div align="center">

# Argus

**A distributed API gateway with load balancing, rate limiting, and cross-instance traffic intelligence**

[![CI](https://github.com/abdulmoeedsiddiqi/Argus/actions/workflows/ci.yml/badge.svg)](https://github.com/abdulmoeedsiddiqi/Argus/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-KRaft-231F20?logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Status](https://img.shields.io/badge/status-Milestone%201-blue)

</div>

---

## Table of contents

- [Overview](#overview)
- [The problem](#the-problem)
- [Key features](#key-features)
- [Architecture](#architecture)
- [Design highlights](#design-highlights)
- [Technology stack](#technology-stack)
- [Repository structure](#repository-structure)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Testing and quality](#testing-and-quality)
- [Project status and roadmap](#project-status-and-roadmap)
- [Documentation](#documentation)
- [Development workflow](#development-workflow)
- [AI usage disclosure](#ai-usage-disclosure)
- [Author](#author)

---

## Overview

Argus is an **API gateway**: the single entry point through which client applications reach a set of backend microservices. It authenticates every request once, routes it to the correct service, balances load across that service's replicas, enforces per-client rate limits, and protects the system when a backend fails.

Because a single gateway is both a bottleneck and a single point of failure, Argus runs as **multiple identical, stateless instances** behind a load balancer. Its distinguishing feature is **cross-instance traffic intelligence**: every instance streams request events to Kafka, so independent services can meter usage across the whole cluster, detect abuse that no single instance can see, and push enforcement decisions and configuration back to every instance.

> Developed as the joint semester project for **SE423 – Software Construction and Development** and **SE431 – Software Quality Assurance** at the Ghulam Ishaq Khan Institute (GIKI).

---

## The problem

Replicating a gateway solves capacity and availability, but creates a new problem: **each instance sees only part of the traffic.**

| Without cross-instance coordination | What goes wrong |
|---|---|
| Rate limits counted per instance | A client spreading requests over 3 instances gets 3× its limit |
| Usage counted in memory | Counts are wrong cluster-wide and lost when an instance crashes |
| Attacks seen per instance | 60 failed logins spread over 3 instances look like a normal 20 each |
| Fire-and-forget config updates | An instance that was restarting keeps accepting a revoked API key |

Argus keeps all important state **outside** the instances: shared counters in Redis, and events, decisions and configuration in Kafka.

---

## Key features

### Data plane (every gateway instance)

| Capability | Description |
|---|---|
| **Routing** | Path- and method-based route matching with immutable, versioned route snapshots |
| **Load balancing** | Pluggable strategies: round-robin, weighted, least-connections; weighted **canary** releases |
| **Health checking** | Active checks remove failed upstream instances and reintegrate recovered ones |
| **Authentication** | JWT validation and API keys stored only as SHA-256 hashes |
| **Distributed rate limiting** | Token bucket enforced **atomically across all instances** via a Redis Lua script, with local fallback |
| **Resilience** | Circuit breaker (closed / open / half-open), timeouts, and retries **only for idempotent requests** |

### Control plane (cross-instance intelligence)

| Capability | Description |
|---|---|
| **Usage metering and quotas** | Every request counted exactly once in a durable, replayable ledger; monthly plan quotas enforced |
| **Abuse detection** | Rules over the combined traffic of all instances; offending keys blocked everywhere |
| **Consistent configuration** | Route and key changes delivered through a compacted topic, so restarting instances rebuild current state |
| **Graceful degradation** | If Kafka is down, events spill to disk and replay later; if Redis is down, limits fall back locally |

### Observability

Prometheus metrics, Grafana dashboards, structured JSON logs with correlation IDs, and Kafka UI for inspecting topics.

---

## Architecture

```mermaid
flowchart LR
    C[API clients] --> N[Nginx<br/>load balancer]
    N --> G["Argus Gateway<br/>3 identical, stateless instances"]
    G --> U[Upstream services<br/>Users · Orders · Products]
    G <-->|atomic checks| R[(Redis<br/>shared rate limits)]
    G -->|request events| K{{Kafka}}
    K -->|decisions + config| G
    K --> M[Metering &<br/>Quota Service]
    K --> A[Abuse Detection<br/>Service]
    M -->|quota decisions| K
    A -->|block decisions| K
    AD[Admin API] -->|config changes| K
    M --> P[(PostgreSQL)]
    AD --> P
```

### Kafka topics

| Topic | Type | Key | Producers → consumers |
|---|---|---|---|
| `gateway.request-events` | 7-day retention | API key ID | Gateways → Metering, Abuse Detection |
| `gateway.enforcement` | Log-compacted | API key ID | Metering, Abuse Detection → Gateways |
| `gateway.config` | Log-compacted | `entityType:entityId` | Admin API → Gateways |
| `gateway.request-events.dlq` | Dead-letter | – | Events that repeatedly fail processing |

### Request lifecycle

Each request passes through an ordered filter pipeline. Any filter may reject it, and later filters never run.

```
Authentication → Enforcement → Rate limit → Routing / load balancing → Proxy (circuit breaker, retries)
     401             403           429              404 / 503                    503 / 504
```

A detailed component diagram, six class diagrams, a sequence diagram and a state diagram are in [`docs/diagrams`](docs/diagrams).

---

## Design highlights

### Framework-free core (ports and adapters)

All business logic lives in `argus-core`, which depends **only on the JDK**. The core declares the interfaces it needs (`RateLimiter`, `EventPublisher`, `ApiKeyStore`, `EnforcementView`, `UpstreamClient`), and `argus-gateway` provides adapters for Redis, Kafka, JWT and HTTP. ArchUnit tests fail the build if the core imports Spring, Kafka, Redis or JPA, or if its packages form a dependency cycle.

### Design patterns

| Pattern | Category | Where | Problem solved |
|---|---|---|---|
| Builder | Creational | `Route.Builder` | Immutable routes with many optional parts |
| Factory Method | Creational | `LoadBalancingStrategyFactory` | Create strategies from configuration without exposing concrete classes |
| Chain of Responsibility | Behavioural | `GatewayFilter`, `FilterChain` | Ordered pipeline where any step can stop the request |
| Strategy | Behavioural | `LoadBalancingStrategy`, `RetryPolicy`, `DetectionRule` | Interchangeable algorithms per route |
| State | Behavioural | `CircuitState` (Closed, Open, HalfOpen) | Breaker behaviour depends on its state; thread-safe transitions |
| Decorator | Structural | `FallbackRateLimiter`, `SpillingEventPublisher` | Graceful degradation without changing wrapped components |
| Adapter | Structural | `RedisRateLimiter`, `KafkaEventPublisher` | Fit infrastructure APIs to core ports |
| Observer | Behavioural | `ConfigurationListener` | Several components react to each config change |

### Concurrency and consistency

| Shared state | Technique |
|---|---|
| Route table | Immutable snapshot behind an `AtomicReference`, swapped atomically |
| Round-robin position | `AtomicInteger` |
| Circuit-breaker state | `AtomicReference` with compare-and-set transitions; `Semaphore` for half-open trials |
| Local token buckets | `ReentrantLock` per bucket (avoids virtual-thread pinning) |
| Cross-instance rate limits | Single atomic Redis Lua script |
| Usage ledger | At-least-once delivery with deduplication by `event_id` → effectively-once counting |

**Consistency boundary:** rate limits are exact and immediate; quotas and abuse blocks are eventually consistent (seconds), by design, to keep the database and analytics off the request path.

Architectural decisions and the alternatives rejected are recorded in [`docs/adr`](docs/adr).

---

## Technology stack

| Layer | Technologies |
|---|---|
| Language and runtime | Java 21 (virtual threads), Spring Boot 3.5, Maven |
| Data | PostgreSQL 16, Flyway migrations, Redis 7 |
| Messaging | Apache Kafka (KRaft mode), Spring for Apache Kafka |
| Security | JWT (Nimbus JOSE + JWT), SHA-256 hashed API keys |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit, JaCoCo, PIT, k6 |
| Code quality | SonarCloud, Checkstyle, SpotBugs, PMD |
| DevOps | GitHub Actions, Docker, Docker Compose, Kubernetes (kind), Nginx |
| Observability | Micrometer, Prometheus, Grafana, SLF4J/Logback |

---

## Repository structure

```
Argus/
├── argus-contracts/         Shared event records: RequestEvent, EnforcementDecision, ConfigChange, Topics
├── argus-core/              Framework-free business logic and ports
│   └── dev/argus/core/
│       ├── model/           Immutable value types (requests, routes, policies)
│       ├── pipeline/        Filter chain (Chain of Responsibility)
│       ├── routing/         Route and upstream registries, upstream client port
│       ├── loadbalancing/   Load-balancing strategies and factory
│       ├── auth/            Authenticators and API key store port
│       ├── ratelimit/       Rate limiter contract and policies
│       ├── resilience/      Circuit breaker states and retry policy
│       ├── events/          Event publisher, enforcement and configuration ports
│       └── exception/       Typed exception hierarchy
├── argus-gateway/           Spring Boot data plane: HTTP proxy, Redis/Kafka/JWT adapters
├── argus-admin/             Admin API for routes, consumers, plans and keys (+ Flyway schema)
├── argus-metering/          Usage ledger and quota enforcement (Kafka consumer)
├── argus-abuse/             Cross-instance abuse detection (Kafka consumer)
├── upstreams/mock-service/  Demo upstream (Users / Orders / Products) with fault injection
├── deploy/                  Docker Compose, Nginx, Prometheus; Kubernetes manifests (M3)
├── docs/
│   ├── adr/                 Architecture Decision Records
│   ├── diagrams/            UML sources and rendered diagrams
│   └── BRANCHING.md         Branching strategy and quality gates
└── .github/workflows/ci.yml Continuous integration
```

---

## Getting started

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Eclipse Temurin recommended |
| Maven | 3.9+ | |
| Docker Desktop | Compose v2 | On Windows, cap WSL2 memory (see below) |
| Git | any recent | |

<details>
<summary><b>Windows: limit Docker's memory use</b></summary>

Create `%UserProfile%\.wslconfig` so the full stack fits comfortably on a 16 GB machine:

```ini
[wsl2]
memory=10GB
processors=6
```

Then run `wsl --shutdown` and restart Docker Desktop.
</details>

### Clone and build

```bash
git clone https://github.com/abdulmoeedsiddiqi/Argus.git
cd Argus
mvn -B verify        # compile, run tests and architecture rules, coverage report, style checks
```

### Run locally

Docker Compose profiles keep day-to-day development light:

| Profile | Starts | Use for |
|---|---|---|
| `core` | PostgreSQL, Redis, Kafka | Daily development and integration tests |
| `full` | Everything: 3 gateways, Nginx, services, upstreams, monitoring | Demos and end-to-end testing (from Milestone 2) |

```bash
# Infrastructure only
docker compose -f deploy/docker-compose.yml --profile core up -d

# Full stack
mvn -B -DskipTests package
docker compose -f deploy/docker-compose.yml --profile full up -d --build

# Stop everything
docker compose -f deploy/docker-compose.yml --profile full down
```

### Service endpoints

| Service | URL |
|---|---|
| Gateway (through Nginx) | http://localhost:8000 |
| Admin API / Swagger UI | http://localhost:8081/swagger-ui.html |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Kafka UI | http://localhost:8090 |

---

## Configuration

Services are configured through environment variables (set in `deploy/docker-compose.yml`):

| Variable | Used by | Purpose |
|---|---|---|
| `ARGUS_INSTANCE_ID` | Gateway | Identifies the instance in events and metrics |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Gateway, Admin, Metering, Abuse | Kafka address |
| `SPRING_DATA_REDIS_HOST` | Gateway | Redis address for shared rate limits |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Admin, Metering | PostgreSQL connection |
| `PORT` | All services | HTTP port (defaults: gateway 8080, admin 8081, metering 8082, abuse 8083) |
| `SERVICE_NAME` | Mock upstream | Runs the mock as `users`, `orders` or `products` |

---

## Testing and quality

| Layer | Tools | What it verifies |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Core logic, using a fake clock for time-based behaviour |
| Concurrency | JUnit 5, virtual threads | e.g. 50 concurrent requests against a limit of 10 allow exactly 10 |
| Architecture | ArchUnit | Core is framework-free and its packages are acyclic |
| Integration | Testcontainers, WireMock | Real Redis, Kafka and PostgreSQL; simulated failing upstreams |
| Load | k6 | Throughput, p95/p99 latency, gateway overhead |
| Failure injection | Docker Compose | Kafka stopped, gateway killed, key revoked during restart, distributed attack |
| Mutation | PIT | Tests actually detect injected faults |

**Quality gates (CI):** build, tests, JaCoCo coverage (target ≥ 60% of core logic by Milestone 3), Checkstyle, and SonarCloud static analysis (from Milestone 2).

Development is **test-driven**: every feature is built as `test:` → `feat:` → `refactor:` commits, and squash merging is disabled so this history stays visible.

---

## Project status and roadmap

| Milestone | Scope | Status |
|---|---|---|
| **M1** (Week 6) | Requirements mapping, object-oriented design, SOLID and pattern plan, core contracts, repository and CI | ✅ In review |
| **M2** (Week 10) | Gateway data plane: routing, load balancing, authentication, rate limiting, resilience, config propagation | ⏳ Planned |
| **M3** (Week 13) | Metering and quotas, abuse detection, observability, Kubernetes and cloud deployment, failure-injection demo | ⏳ Planned |

**What exists today:** the core contracts (interfaces, records and the exception hierarchy), shared event contracts, database schemas, architecture tests, CI pipeline, Docker Compose setup and full design documentation. Implementations are added test-first from Milestone 2.

<details>
<summary><b>Detailed checklist</b></summary>

- [x] Core contracts and shared event records
- [x] Exception hierarchy and validation in value types
- [x] Database schemas (Flyway)
- [x] Architecture rules (ArchUnit) and CI pipeline
- [x] Architecture Decision Records and UML diagrams
- [ ] Filter pipeline and routing with load-balancing strategies
- [ ] JWT and API-key authentication
- [ ] Local and Redis-backed rate limiting with fallback
- [ ] Circuit breaker, timeouts and safe retries
- [ ] Admin API and config propagation through Kafka
- [ ] Metering and quota service
- [ ] Abuse detection service
- [ ] Grafana dashboards, Kubernetes manifests, cloud deployment
- [ ] Load tests and failure-injection experiments

</details>

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/adr`](docs/adr) | Architecture Decision Records: why the core is framework-free, why Kafka is the control plane, atomic rate limiting, delivery guarantees, virtual threads |
| [`docs/diagrams`](docs/diagrams) | Component, class, sequence, state and package-dependency diagrams (PlantUML / Graphviz sources included) |
| [`docs/BRANCHING.md`](docs/BRANCHING.md) | Branching strategy, commit conventions, quality gates and baselines |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | TDD workflow and pull-request checklist |
| [`AI_USAGE_LOG.md`](AI_USAGE_LOG.md) | Record of AI assistance, as required by the course guidelines |

---

## Development workflow

- **Branching:** GitHub Flow with a protected `main`; short-lived `feature/`, `fix/`, `refactor/` and `docs/` branches.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) (`test:`, `feat:`, `fix:`, `refactor:`, `docs:`, `build:`, `ci:`).
- **Merging:** pull requests with passing CI; merge commits only (no squash) to preserve TDD history.
- **Baselines:** each milestone is tagged on `main` (`v0.1-m1`, `v0.2-m2`, `v1.0-m3`).
- **Defects and code smells:** tracked as GitHub Issues with `defect` and `code-smell` labels.

See [`docs/BRANCHING.md`](docs/BRANCHING.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md) for details.

---

## AI usage disclosure

AI-assisted tools were used as supporting resources during planning, design and documentation. All adopted material is reviewed and verified, and each significant use is recorded in [`AI_USAGE_LOG.md`](AI_USAGE_LOG.md), in line with Section 9 of the course guidelines.

---

## Author

**Abdul Moeed** (2023009)
BS Software Engineering, Ghulam Ishaq Khan Institute of Engineering Sciences and Technology (GIKI)
Course instructor: Dr. Mian Muaz Razzaq

[GitHub](https://github.com/abdulmoeedsiddiqi) · [LinkedIn](https://www.linkedin.com/in/abdul-moeed-031391286)
