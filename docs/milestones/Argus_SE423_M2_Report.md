# Argus: A Distributed API Gateway — SE423 Milestone 2 Report

**SE423 — Software Construction and Development**

| | |
|---|---|
| **Submitted by** | Abdul Moeed (2023009) |
| **Instructor** | Dr. Mian Muaz Razzaq |
| **Repository** | [github.com/abdulmoeedsiddiqi/Argus](https://github.com/abdulmoeedsiddiqi/Argus) |
| **Date** | 22 November 2026 |

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Working Core Architecture](#2-working-core-architecture)
3. [Design Patterns Implemented](#3-design-patterns-implemented)
4. [Defensive Programming and Exception Handling](#4-defensive-programming-and-exception-handling)
5. [Concurrency and Thread-Safety](#5-concurrency-and-thread-safety)
6. [Code Quality and SOLID Adherence](#6-code-quality-and-solid-adherence)
7. [TDD and Automated-Test Baseline](#7-tdd-and-automated-test-baseline)
8. [Repository Discipline and Contribution Traceability](#8-repository-discipline-and-contribution-traceability)
9. [Deviations from the M1 Design](#9-deviations-from-the-m1-design)
10. [AI Usage Declaration](#10-ai-usage-declaration)

---

## 1. Introduction

### 1.1 Purpose

This report is Milestone 2 for SE423 in the joint SE423/SE431 project. It presents the evidence
that the Argus gateway has been designed, built and tested according to the construction criteria
in the project guidelines.

Milestone 1 delivered the design: interfaces, records, an exception hierarchy, seven ADRs and a
plan for eight design patterns. Milestone 2 delivers the running system: three functional modules
are fully implemented, tested and running in a Docker Compose stack of 13 containers.

### 1.2 How this report maps to the Milestone 2 rubric

| Rubric criterion | Weight | Where it is answered |
|---|---|---|
| Working core architecture | 20% | Section 2 (end-to-end demo, module progress, architecture enforcement) |
| Design patterns implemented correctly | 20% | Section 3 (six patterns, file locations, rationale, rejected alternatives) |
| Defensive programming and exception handling | 10% | Section 4 (exception hierarchy, validation strategy, error reporting) |
| Concurrency and thread-safety implementation | 15% | Section 5 (strategy, implementations, testing evidence) |
| Code quality and SOLID adherence | 15% | Section 6 (each principle with code evidence, ArchUnit enforcement) |
| TDD and automated-test baseline | 10% | Section 7 (red-green-refactor history, 331 tests, JaCoCo coverage) |
| Repository discipline and contribution traceability | 10% | Section 8 (branches, commits, CI pipeline, AI usage log) |

### 1.3 What was built

| Phase | Branch | Module(s) | Commits | Tests added |
|---|---|---|---|---|
| 1 — Walking skeleton | `feature/skeleton-walking-skeleton` | Pipeline (core), mock-service | 12 | 30 |
| 2 — Routing and load balancing | `feature/routing-load-balancing` | M1: Routing and Load Balancing | 19 | 130 |
| 3 — Authentication | `feature/auth-api-key-jwt` | M2: Authentication and API Key Management | 12 | 51 |
| 4 — Admin API and config propagation | `feature/admin-config-propagation` | M5: Admin API and Configuration Propagation | 16 | 120 |

All 73 commits are on `main`. The total test count is **331** (147 core, 84 gateway, 89 admin, 11 mock-service).

---

## 2. Working Core Architecture

### 2.1 The system builds and runs end-to-end

The system is verified in two ways:

**Automated (CI on every push):**
```
./mvnw -B verify
```
This command compiles all modules, runs all 331 tests (unit, integration, ArchUnit), generates
JaCoCo coverage reports and runs Checkstyle. The GitHub Actions CI pipeline runs on every push
to every branch. The latest run on `main` passed in 2 minutes 8 seconds
([CI badge](https://github.com/abdulmoeedsiddiqi/Argus/actions/workflows/ci.yml)).

**Docker Compose demo (13 containers):**
```
docker compose -f deploy/docker-compose.yml up --build -d
scripts/seed.sh
deploy/smoke-test.sh
deploy/propagation-test.sh
```
This starts Nginx, three identical gateway instances, the Admin API, PostgreSQL, Kafka (KRaft)
and six mock backend copies. The seed script loads the demo configuration through the Admin API,
and both test scripts verify that requests are routed, authenticated and propagated correctly.

### 2.2 Module boundaries are consistent with the M1 design

The M1 report defined seven functional modules. Three are fully implemented:

| # | Module | Status | Evidence |
|---|---|---|---|
| M1 | Routing and Load Balancing | **Complete** | `core.routing`, `core.loadbalancing`; 3 strategies, canary, health checks |
| M2 | Authentication and API Key Management | **Complete** | `core.auth`; API-key hash lookup, JWT verification, filter chain |
| M3 | Distributed Rate Limiting | Planned for M3 | Interfaces exist in `core.ratelimit`; Redis adapter not yet built |
| M4 | Resilience | Planned for M3 | `CircuitState` designed; `ProxyFilter` in `core.resilience` handles timeouts |
| M5 | Admin API and Configuration Propagation | **Complete** | `argus-admin` (59 classes); Kafka `gateway.config` topic |
| M6 | Metering and Quota Service | Planned for M3 | `argus-metering` module exists, no implementation yet |
| M7 | Abuse Detection Service | Planned for M3 | `argus-abuse` module exists, no implementation yet |

The package structure matches the M1 class diagram:

```
argus-core/src/main/java/dev/argus/core/
    auth/           ApiKeyAuthenticator, ApiKeyHasher, AuthenticationFilter, Authenticator
    events/         ConfigurationListener, EventPublisher
    exception/      ArgusException hierarchy (15 exception classes)
    loadbalancing/  LoadBalancingStrategy, RoundRobin/Weighted/LeastConnections, Factory, CanarySelector
    model/          Route (with Builder), UpstreamInstance, ApiKeyRecord, IncomingRequest, ...
    pipeline/       GatewayFilter, FilterChain, DefaultFilterChain, FilterChainFactory, FilterResult
    ratelimit/      RateLimiter, RateLimitResult (interfaces only, M3)
    resilience/     ProxyFilter, CircuitState, RetryPolicy (interfaces for M4)
    routing/        RouteRegistry, SnapshotRouteRegistry, UpstreamRegistry, HealthChecker, RoutingFilter
    time/           TimeSource
```

### 2.3 Substantial implementation progress

| Metric | Count |
|---|---|
| Java source files (main) | 152 (69 core + 17 gateway + 59 admin + 7 mock-service) |
| Java source files (test) | 56 (20 core + 21 gateway + 15 admin) |
| Lines of code (main) | 6,215 |
| Lines of code (test) | 5,688 |
| Total automated tests | 331 |
| Flyway migration (PostgreSQL) | 1 migration, 6 tables |
| Architecture Decision Records | 7 |
| Docker Compose services | 13 containers |

### 2.4 Ports-and-adapters architecture

The M1 design chose a hexagonal (ports-and-adapters) architecture: `argus-core` contains all
business logic and defines **ports** (interfaces) for the infrastructure it needs; the Spring Boot
modules supply **adapters** that implement those ports.

This is enforced at build time by three ArchUnit rules in
`argus-core/src/test/java/dev/argus/core/ArchitectureTest.java`:

1. **Framework-free core:** no class in `dev.argus.core..` may import from `org.springframework`,
   `org.apache.kafka`, `io.lettuce`, or `jakarta.persistence`.
2. **No package cycles:** `dev.argus.core..` must be free of dependency cycles.
3. **Model isolation:** `dev.argus.core.model..` depends only on `dev.argus.core.model..` and the
   JDK (`java..`).

These tests run on every `mvn verify` and on every CI push. Any violation breaks the build.

---

## 3. Design Patterns Implemented

The M1 report planned eight design patterns. Six are implemented in Milestone 2; the remaining
two (State and Decorator) belong to modules M3 and M4 that are scheduled for Milestone 3.

The rubric requires at least three patterns, including at least one **creational** and at least one
**structural or behavioral** pattern. The count is:
- Creational: **Builder**, **Factory Method** (2)
- Behavioral: **Chain of Responsibility**, **Strategy**, **Observer** (3)
- Structural: **Adapter** (1)

### 3.1 Builder — `Route.Builder`

| | |
|---|---|
| **File** | `argus-core/src/main/java/dev/argus/core/model/Route.java:43` |
| **Tests** | `Route_BuilderTest.java` (8 test cases) |
| **Problem** | A `Route` has 11 fields (id, path prefix, service ID, timeout, load-balancing type, methods, canary version, canary percent, retry attempts, health-check path, circuit-breaker thresholds). Constructing one with correct defaults and validation in a constructor call is error-prone. |
| **Solution** | `Route.Builder` sets sensible defaults (timeout 5 s, round-robin, health path `/health`, no canary) and validates the whole route at `build()` time: `pathPrefix` must start with `/`, `canaryPercent` must be 0-100, `timeout` must be positive. The resulting `Route` is an immutable Java record. |
| **Rejected alternative** | A constructor with many parameters. Rejected because it makes call sites fragile and forces callers to know the default values. |
| **Maintainability** | New route fields are added to the builder; existing call sites are not affected. |

### 3.2 Factory Method — `LoadBalancingStrategyFactory` and `FilterChainFactory`

| | |
|---|---|
| **Files** | `core/loadbalancing/DefaultLoadBalancingStrategyFactory.java:14`, `core/pipeline/FilterChainFactory.java:11` |
| **Tests** | `LoadBalancingStrategyFactoryTest.java` (4 cases), `FilterChainFactoryTest.java` (3 cases) |
| **Problem** | The routing filter needs a load-balancing strategy for each route, chosen by name (`ROUND_ROBIN`, `WEIGHTED`, `LEAST_CONNECTIONS`). The pipeline needs an ordered chain of filters built from a list. Neither caller should know the concrete classes. |
| **Solution** | `LoadBalancingStrategyFactory.create(type, instances)` returns the matching `LoadBalancingStrategy` implementation. `FilterChainFactory.create(filters)` sorts the filters by their `order()` and returns a `DefaultFilterChain`. Both throw `InvalidConfigurationException` for invalid input. |
| **Rejected alternative** | A `switch` in the caller. Rejected because every new strategy type would change the caller, violating the Open/Closed Principle. |

### 3.3 Chain of Responsibility — `GatewayFilter` and `FilterChain`

| | |
|---|---|
| **Files** | `core/pipeline/GatewayFilter.java:9`, `core/pipeline/DefaultFilterChain.java:13` |
| **Tests** | `DefaultFilterChainTest.java` (5 cases), `FilterChainFactoryTest.java` (3 cases) |
| **Problem** | Every request must pass through several checks (authentication, routing, proxying) in order. Adding a new check (rate limiting, circuit breaker) must not change the existing ones. |
| **Solution** | Each filter implements `GatewayFilter.filter(context, chain)`. It either calls `chain.proceed(context)` to pass the request on, or returns a `FilterResult.Rejected`. The chain is immutable: `proceed` creates a new chain with `index + 1`, so concurrent requests on the same chain cannot interfere. The current chain is: `AuthenticationFilter` (order 100) → `RoutingFilter` (order 200) → `ProxyFilter` (order 500). |
| **Rejected alternative** | Template Method (a base class with `before`/`after` hooks). Rejected in the M1 report because it forces a single inheritance hierarchy and makes it harder to reorder filters. |

### 3.4 Strategy — `LoadBalancingStrategy`

| | |
|---|---|
| **Files** | `core/loadbalancing/LoadBalancingStrategy.java:15`, `RoundRobinStrategy.java`, `WeightedStrategy.java`, `LeastConnectionsStrategy.java` |
| **Tests** | `RoundRobinStrategyTest.java` (3 cases), `WeightedStrategyTest.java` (4 cases), `LeastConnectionsStrategyTest.java` (5 cases), `CanarySelectorTest.java` (5 cases) |
| **Problem** | Different routes need different algorithms to choose among their backend copies. The routing filter must not know which algorithm is in use. |
| **Solution** | `LoadBalancingStrategy.choose(instances)` returns the chosen `UpstreamInstance`. Three implementations exist: round-robin (cyclic), weighted (probability proportional to `weight`), and least-connections (fewest in-flight requests, with `started()`/`finished()` hooks). `CanarySelector` wraps any strategy and diverts a configured percentage of traffic to the canary version. |
| **Rejected alternative** | Hardcoded `if/else` on a strategy name. Rejected because it violates OCP and makes testing harder (each branch must be tested inside a larger method). |

### 3.5 Observer — `ConfigurationListener` and `ConfigTopicConsumer`

| | |
|---|---|
| **Files** | `core/events/ConfigurationListener.java:7`, `gateway/events/ConfigTopicConsumer.java:33`, `gateway/events/RouteConfigApplier.java`, `gateway/auth/ApiKeyCache.java` |
| **Tests** | `ConfigTopicConsumerTest.java` (5 cases), `RouteConfigApplierTest.java` (5 cases), `ApiKeyCacheListenerTest.java` (3 cases), `ConfigPropagationIntegrationTest.java` (5 cases) |
| **Problem** | When the Admin API changes a route or revokes a key, every gateway instance must update its in-memory state. The gateway core must not depend on Kafka. |
| **Solution** | `ConfigurationListener` is a port: `onRouteChanged(id, config)`, `onUpstreamChanged(id, config)`, `onApiKeyChanged(id, config)`. The `ConfigTopicConsumer` reads the compacted Kafka topic from offset 0 on every start and calls each registered listener for every record. Two listeners are registered: `RouteConfigApplier` (rebuilds the route table and upstream registries) and `ApiKeyCache` (updates the key cache). The consumer opens a `ReadinessGate` after the replay finishes; the gateway answers `503 NOT_READY` until the gate opens. |
| **Rejected alternative** | Gateways poll the database. Rejected in the M1 report because it ties gateways to the admin schema, adds load, and is slower than a push model. |

### 3.6 Adapter — `JdkHttpUpstreamClient`, `JwtAuthenticator`, `KafkaConfigChangePublisher`

| | |
|---|---|
| **Files** | `gateway/upstream/JdkHttpUpstreamClient.java:31`, `gateway/auth/JwtAuthenticator.java:36`, `admin/events/KafkaConfigChangePublisher.java:22` |
| **Tests** | `JdkHttpUpstreamClientTest.java` (5 cases), `JwtAuthenticatorTest.java` (8 cases), `ConfigChangeRelayTest.java` (4 cases) |
| **Problem** | The core defines ports (`UpstreamClient`, `Authenticator`, `ConfigChangePublisher`) but must not know about HTTP clients, JWT libraries or Kafka producers. |
| **Solution** | Each adapter implements a core port using an infrastructure library: `JdkHttpUpstreamClient` wraps `java.net.http.HttpClient`, `JwtAuthenticator` wraps Nimbus JOSE+JWT, `KafkaConfigChangePublisher` wraps `KafkaTemplate`. The adapters live in the Spring Boot modules, never in `argus-core`. |
| **Rejected alternative** | Putting infrastructure code directly in the core. Rejected because it makes the core untestable without the infrastructure, violates DIP and breaks the ArchUnit rules. |

### 3.7 Patterns not yet implemented

| Pattern | Module | Status |
|---|---|---|
| **State** (`CircuitState`: Closed, Open, HalfOpen) | M4 Resilience | Designed in M1; `CircuitState` interface exists. Implementation in M3. |
| **Decorator** (`FallbackRateLimiter`, `SpillingEventPublisher`) | M3 Rate Limiting | Designed in M1; interfaces exist. Implementation in M3. |

---

## 4. Defensive Programming and Exception Handling

### 4.1 Exception hierarchy

All gateway exceptions extend `ArgusException`, an abstract `RuntimeException`. The hierarchy has
three branches, each deciding who is responsible:

```
ArgusException (abstract)
    ClientRejectionException (abstract) ............. caller's fault (4xx)
        AuthenticationFailedException ............... 401
        AccessBlockedException ...................... 403
        RouteNotFoundException ...................... 404
        RateLimitExceededException .................. 429
        QuotaExceededException ...................... 429
    UpstreamException (abstract) .................... backend's fault (5xx)
        UpstreamTimeoutException .................... 504
        CircuitOpenException ........................ 503
        NoHealthyUpstreamException .................. 503
    InfrastructureUnavailableException (abstract) ... internal, never reaches client
        RateLimiterUnavailableException
        EventPublishException
    InvalidConfigurationException ................... admin error (400)
```

**Key rule:** the type of exception decides the HTTP status and the log level. This is enforced by
two centralized exception handlers:

- **`GatewayExceptionHandler`** (`argus-gateway`): `ClientRejectionException` → 4xx (not logged
  as error), `UpstreamException` → 5xx (logged as warning), any other `RuntimeException` → 500
  with a generic message (logged as error with full stack trace). Stack traces never reach the
  client.

- **`AdminExceptionHandler`** (`argus-admin`): `MethodArgumentNotValidException` (Bean Validation)
  → 400 with per-field messages, `ResourceNotFoundException` → 404,
  `ResourceConflictException` → 409, `ConfigNotPropagatedException` → 503,
  `InvalidConfigurationException` → 400, any other → 500. Stack traces never reach the client.

### 4.2 Input validation at boundaries

Validation happens at the first point where data enters the system:

**Record constructors (core):**
```java
// Route.java — the constructor of an immutable record
public Route {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(pathPrefix, "pathPrefix");
    Objects.requireNonNull(serviceId, "serviceId");
    Objects.requireNonNull(loadBalancing, "loadBalancing");
    Objects.requireNonNull(timeout, "timeout");
}
```

`Route.Builder.build()` validates cross-field constraints: `pathPrefix` starts with `/`,
`canaryPercent` is 0-100, `timeout` is positive. Invalid routes throw `IllegalArgumentException`
so that `core.model` stays JDK-only.

**Bean Validation on DTOs (Admin API):**
```java
// PlanDto.java
public record PlanDto(
    @NotBlank @Pattern(regexp = Ids.PATTERN) String id,
    @NotBlank @Size(max = 100) String name,
    @NotNull @Min(1) Integer rateCapacity,
    @NotNull @Min(1) Long ratePeriodMs,
    @NotNull @Min(1) Long monthlyQuota) { }
```

Every `@PostMapping` and `@PutMapping` in the Admin API controllers takes `@Valid @RequestBody`,
so malformed JSON is rejected with a structured 400 response before it reaches the service layer.

**Additional validation limits (Admin API):**
- Route timeout: 100 ms to 60,000 ms
- Canary percent: 0 to 100
- Retry attempts: 1 to 5
- Upstream weight: 1 to 100
- Plan rate capacity, period and quota: minimum 1

These limits are tested in `ValidationLimitsTest.java` (8 test cases).

### 4.3 Error reporting

- JSON error bodies follow a consistent shape: `{"reason": "...", "message": "..."}` for the
  gateway, `{"reason": "...", "message": "...", "fields": {...}}` for the Admin API.
- Machine-readable reason codes (`AUTHENTICATION_FAILED`, `RATE_LIMITED`, `ROUTE_NOT_FOUND`, etc.)
  are defined in the `RejectionReason` enum, allowing API clients to branch on the code rather
  than parsing human-readable messages.
- Sensitive information (stack traces, internal class names, SQL errors) is logged at
  `WARN`/`ERROR` level but never included in the HTTP response.

---

## 5. Concurrency and Thread-Safety

### 5.1 Concurrency model

Argus runs on Java 21 virtual threads (`spring.threads.virtual.enabled=true`), as decided in
ADR-0006. This means every incoming HTTP request runs on its own lightweight virtual thread.
The gateway handles many concurrent requests without a fixed thread pool, and each request uses
simple, blocking code rather than reactive callbacks.

### 5.2 Thread-safety strategy

The strategy follows three rules:

**Rule 1 — Immutable snapshots behind `AtomicReference`:**

Shared state that many readers access concurrently is stored as an immutable object behind an
`AtomicReference`. Updates build a new object and swap the reference atomically. Readers never
block; they read the reference once and work on that snapshot for the duration of the request.

| Class | What it holds | File and line |
|---|---|---|
| `SnapshotRouteRegistry` | The route table (all routes, indexed by path prefix) | `core/routing/SnapshotRouteRegistry.java:25` |
| `InMemoryUpstreamRegistry` | The list of backend instances per service | `core/routing/InMemoryUpstreamRegistry.java:26` |
| `ApiKeyCache` | The map of key hash to `ApiKeyRecord` | `gateway/auth/ApiKeyCache.java:35` |
| `FaultState` | The current fault injection mode (mock-service) | `mock-service/FaultState.java:16` |

**Rule 2 — `ReentrantLock`, not `synchronized`:**

Java 21 virtual threads can be "pinned" (stuck on a platform thread) when they enter a
`synchronized` block. All mutable shared state uses `ReentrantLock` instead:

```java
// RouteConfigApplier.java — rebuilds the route table on configuration changes
private final ReentrantLock lock = new ReentrantLock();

public void onRouteChanged(String id, RouteConfig config) {
    lock.lock();
    try {
        // ... rebuild the snapshot and swap the AtomicReference
    } finally {
        lock.unlock();
    }
}
```

**Rule 3 — Immutable records and defensive copies:**

All data objects are Java records (immutable by definition): `Route`, `UpstreamInstance`,
`ApiKeyRecord`, `IncomingRequest`, `RequestContext`, `FilterResult`, and all contract records
(`RouteConfig`, `UpstreamConfig`, `ApiKeyConfig`, `PlanConfig`). Collections are wrapped in
`List.copyOf()` or `Collections.unmodifiableList()`. There is no static mutable state anywhere
in the codebase; all shared state is injected through constructors.

### 5.3 Genuine concurrent operations

1. **Virtual-thread request handling:** multiple requests are processed concurrently on virtual
   threads. Each reads the route/key snapshots without blocking.
2. **Health checker:** a `ScheduledExecutorService` runs health probes on a background thread while
   request threads read the health status concurrently
   (`core/routing/HealthChecker.java`).
3. **Configuration replay:** the `ConfigTopicConsumer` reads the Kafka topic on its own thread
   while the gateway entry point answers `503 NOT_READY` on request threads
   (`gateway/events/ConfigTopicConsumer.java`).

### 5.4 Testing evidence

- `DefaultFilterChainTest`: verifies the chain is safe for concurrent use because `proceed` creates
  a new chain with `index + 1` rather than mutating a shared index.
- `SnapshotRouteRegistryTest`: verifies that `replace()` atomically swaps the whole table.
- `ReadinessGateTest` and `ReadinessGateIntegrationTest`: verify the `AtomicBoolean` gate works
  correctly across threads.
- `ConfigTopicConsumerTest`: verifies the consumer thread and the readiness gate interaction.

---

## 6. Code Quality and SOLID Adherence

### 6.1 Single Responsibility Principle (SRP)

Each class has one reason to change:

| Class | Responsibility |
|---|---|
| `AuthenticationFilter` | Decides whether a request is authenticated |
| `RoutingFilter` | Matches a request to a route and chooses a backend |
| `ProxyFilter` | Forwards the request to the chosen backend |
| `GatewayExceptionHandler` | Maps exceptions to HTTP responses |
| `RouteConfigApplier` | Applies configuration changes to the route table |
| `ApiKeyCache` | Stores and looks up API keys by hash |
| `AuditRecorder` | Writes audit log entries |

The M1 report's `GatewayEntryPoint` builds the request context and calls the chain; it does not
authenticate, route or proxy. Each Admin API service (e.g. `PlanService`, `RouteService`,
`ApiKeyService`) handles exactly one entity type.

### 6.2 Open/Closed Principle (OCP)

- **Filter chain:** adding a new filter (rate limiter, circuit breaker) requires implementing
  `GatewayFilter` and setting its `order()`. Existing filters are not changed.
- **Load-balancing strategies:** adding a new strategy requires implementing
  `LoadBalancingStrategy` and adding one case in the factory. Existing strategies are not changed.
- **Configuration listeners:** adding a new listener requires implementing `ConfigurationListener`
  and registering it. Existing listeners are not changed.

### 6.3 Liskov Substitution Principle (LSP)

- All three `LoadBalancingStrategy` implementations honour the contract: given a list of instances,
  they return one, and the choice is deterministic for the same input (round-robin) or
  probabilistically correct (weighted).
- All `ClientRejectionException` subclasses carry an HTTP status in the 4xx range.
- All `UpstreamException` subclasses carry an HTTP status in the 5xx range.

### 6.4 Interface Segregation Principle (ISP)

- `UpstreamClient` has one method: `send(request, instance, timeout)`.
- `Authenticator` has one method: `authenticate(request)`.
- `HealthProbe` has one method: `check(instance)`.
- `ConfigurationListener` has three methods, one per entity type (route, upstream, key), each
  independently called. A listener that cares only about routes ignores the other two (the default
  implementations are no-ops in the interface).

### 6.5 Dependency Inversion Principle (DIP)

This is the defining principle of the architecture. `argus-core` defines ports:

| Port (interface in core) | Adapter (class in a Spring Boot module) |
|---|---|
| `UpstreamClient` | `JdkHttpUpstreamClient` (gateway) |
| `Authenticator` | `JwtAuthenticator` (gateway) |
| `HealthProbe` | `JdkHttpHealthProbe` (gateway) |
| `ConfigurationListener` | `RouteConfigApplier`, `ApiKeyCache` (gateway) |
| `ConfigChangePublisher` | `KafkaConfigChangePublisher` (admin) |

The core depends on abstractions (the interfaces); the Spring Boot modules depend on the core
and on concrete infrastructure (HTTP client, Nimbus JWT, KafkaTemplate). The dependency arrows
point inward, never outward.

**ArchUnit enforcement:** the three rules in `ArchitectureTest` guarantee this at build time:
1. No Spring, Kafka, Redis or JPA import in core.
2. No package cycles.
3. `core.model` depends only on itself and the JDK.

### 6.6 Cohesion and coupling evidence

- **Core module:** 69 source files, organized into 10 packages by domain concept. Each package
  has a narrow responsibility (e.g. `core.auth` has 4 classes, all about authentication).
- **Gateway module:** 17 source files. Only the adapters and configuration; no business logic.
- **Admin module:** 59 source files. Persistence, services and controllers for the Admin API.
- **Inter-module coupling:** the gateway and admin depend on `argus-core` and `argus-contracts`
  but not on each other. At runtime, they communicate only through the Kafka topic.

---

## 7. TDD and Automated-Test Baseline

### 7.1 Red-green-refactor evidence

Every feature was built as a `test:` commit followed by a `feat:` commit. The commit log shows
**27 test/feat pairs**, each following the pattern:

```
253345a test: add failing tests for Route.Builder (defaults, validation, TC-04.2)
c28b542 feat: add Route.Builder with defaults and whole-route validation
```

The `test:` commit was run and confirmed failing before the `feat:` commit was written. This is
visible in the commit history: each `test:` commit compiles (the interface or class under test
exists or is stubbed) but its tests fail because the implementation is not yet present.

The 27 TDD pairs are:

| # | test: commit | feat: commit | Requirement |
|---|---|---|---|
| 1 | `61951f2` | `4f8f352` | DefaultFilterChain and FilterChainFactory |
| 2 | `374697a` | `52ead1b` | ProxyFilter forwarding |
| 3 | `eaa2c47` | `b35b6ba` | JdkHttpUpstreamClient adapter |
| 4 | `af1b260` | `a8dcd0f` | GatewayEntryPoint HTTP entry |
| 5 | `e8add0d` | `b15335a` | Walking skeleton integration (UC-01) |
| 6 | `253345a` | `c28b542` | Route.Builder (TC-04.2) |
| 7 | `2615942` | `1b162ec` | SnapshotRouteRegistry (FR-01, TC-01.1-TC-01.3) |
| 8 | `ff6e64a` | `91c4152` | Load-balancing strategies and factory (FR-02) |
| 9 | `e00e809` | `bd0fc96` | CanarySelector (FR-04, TC-04.1, TC-04.2) |
| 10 | `133d0ee` | `c6da833` | UpstreamRegistry and HealthChecker (FR-03) |
| 11 | `29d45bd` | `66f9f0a` | RoutingFilter and RequestContext.withUpstream |
| 12 | `b5d065c` | `6f102ff` | Routed proxying and M1 end to end |
| 13 | `ac59c8e` | `d82dd60` | Error mapping and X-Argus-Upstream header |
| 14 | `622c7aa` | `ac3b804` | Active health checks (FR-03, QR-01) |
| 15 | `180ce69` | `615f476` | ApiKeyHasher (FR-06, QR-11) |
| 16 | `2c3f5db` | `c665ded` | ApiKeyAuthenticator (FR-06, TC-06.1-TC-06.4) |
| 17 | `ed53a05` | `1907fcb` | AuthenticationFilter (TC-05.1, TC-05.4, TC-06.x) |
| 18 | `a123b53` | `666d9e1` | JwtAuthenticator (FR-05, TC-05.1-TC-05.3) |
| 19 | `45ae221` | `ebc5c73` | Authentication end to end (UC-01, QR-11, QR-12) |
| 20 | `d1e75ac` | `17d8704` | Config changes in gateway (FR-12, TC-12.3) |
| 21 | `824d825` | `27cc331` | Kafka topic replay and readiness gate (FR-12, QR-09) |
| 22 | `1bb1537` | `940ab3c` | Admin API: plans, consumers, keys, audit (FR-07, QR-11, QR-13) |
| 23 | `c8eaa30` | `c8f4e10` | Admin API: routes, upstreams, validation limits (FR-07, TC-07.2) |
| 24 | `6870070` | `40b4e83` | Publishing config changes to Kafka (FR-12) |
| 25 | `c650fae` | `43d7a29` | YAML import and OpenAPI |

(Plus 2 `refactor:` commits: `9a45108` moving ProxyFilter, `9a45108` package restructure.)

### 7.2 Test suite summary

| Module | Test classes | Test cases | Line coverage |
|---|---|---|---|
| `argus-core` | 20 | 147 | **88%** (409/465 lines) |
| `argus-gateway` | 21 | 84 | **93%** (397/428 lines) |
| `argus-admin` | 15 | 89 | **95%** (667/701 lines) |
| `mock-service` | 2 | 11 | **90%** (47/52 lines) |
| **Total** | **58** | **331** | — |

Branch coverage: core 83%, gateway 85%, admin 87%, mock-service 83%.

Coverage is measured by JaCoCo 0.8.12, configured in the parent POM and run on every
`mvn verify`. Reports are uploaded as CI artifacts on every push.

### 7.3 Test types

| Type | Count | What it covers |
|---|---|---|
| Unit tests (plain JUnit 5 + AssertJ) | ~180 | Core logic with fakes and mocks |
| Integration tests (Testcontainers) | ~90 | Real PostgreSQL and Kafka in Docker |
| Architecture tests (ArchUnit) | 3 | Framework-free core, no cycles, model isolation |
| Integration tests (Spring MockMvc) | ~50 | HTTP endpoint behavior |
| End-to-end (spring-boot-test) | ~8 | Full gateway pipeline with real mock-service |

---

## 8. Repository Discipline and Contribution Traceability

### 8.1 Commit history

The repository has **73 commits** on `main`, all following Conventional Commits:

| Prefix | Count | Purpose |
|---|---|---|
| `test:` | 27 | Failing tests (red phase of TDD) |
| `feat:` | 27 | Implementations that make the tests pass (green phase) |
| `docs:` | 7 | Reports, README, ADRs, AI usage log |
| `build:` | 6 | POM changes, Dockerfile, Compose, Maven Wrapper |
| `refactor:` | 2 | ProxyFilter move, package restructure |
| `chore:` | 2 | Gitattributes, gitignore |

The `test:` and `feat:` commits alternate strictly, making the red-green-refactor history visible.
Squash merging is disabled so this history is preserved on `main`.

### 8.2 Branch strategy

Work happens on short-lived feature branches, as documented in `docs/BRANCHING.md`:

| Branch | Base | Commits | Purpose |
|---|---|---|---|
| `feature/skeleton-walking-skeleton` | `main` | 12 | Phase 1: pipeline, mock-service, Compose |
| `feature/routing-load-balancing` | previous branch | 19 | Phase 2: M1 routing and load balancing |
| `feature/auth-api-key-jwt` | previous branch | 12 | Phase 3: M2 authentication |
| `feature/admin-config-propagation` | previous branch | 16 | Phase 4: M5 Admin API, config propagation |

Each branch is merged to `main` with a fast-forward merge, preserving the full commit history.
All four branches are pushed to GitHub.

### 8.3 Continuous integration

`.github/workflows/ci.yml` runs on every push to every branch:

1. Checks out the code (full history for future SonarCloud analysis).
2. Sets up JDK 21 (Temurin) with Maven caching.
3. Runs `./mvnw -B verify` — compile, all tests, JaCoCo coverage, Checkstyle.
4. Uploads JaCoCo coverage reports as CI artifacts.

The latest CI run on `main` passed in 2 minutes 8 seconds. All pushes to `main` and
`feature/admin-config-propagation` have green CI runs.

### 8.4 Documentation

| Document | Location |
|---|---|
| README.md | Root — build, run, architecture, coverage, thread-safety strategy |
| CLAUDE.md | Root — project rules for AI-assisted development |
| AI_USAGE_LOG.md | Root — 8 rows, one per AI session, with tool, purpose, adopted material, modifications and verification |
| ADR-0001 to ADR-0007 | `docs/adr/` — architecture decisions with context, alternatives and consequences |
| BRANCHING.md | `docs/` — branch naming and merge strategy |
| M1 reports | `docs/milestones/` — SE423 and SE431 Milestone 1 reports |
| Scope document | `docs/milestones/Argus_Scope_Document_v1.3.pdf` |
| Console designs | `docs/console/` — dashboard and request flow diagrams |

### 8.5 AI usage traceability

All AI-assisted work is logged in `AI_USAGE_LOG.md` with eight columns: date, tool and model,
purpose, prompt summary, material adopted, modifications made after review, and verification
performed. The log has 8 rows covering project selection (row 1), scope document (row 2), M1
design (row 3), project rules and Phase 1 plan (row 4), and Phases 1 through 4 (rows 5-8).

---

## 9. Deviations from the M1 Design

| Change | M1 design | M2 implementation | Justification |
|---|---|---|---|
| `ProxyFilter` package | `core.pipeline` | `core.resilience` | Avoids a package cycle between pipeline and resilience; refactored in commit `9a45108` |
| `RequestContext.upstream` field | Not present | Added | `RoutingFilter` needs to pass the chosen instance to `ProxyFilter`; recorded in `RequestContext.withUpstream()` |
| `LoadBalancingStrategy.started()/finished()` hooks | Not in M1 interface | Added | `LeastConnectionsStrategy` needs to track in-flight requests; hooks are default no-ops |
| API-key cache as `ConfigurationListener` | M1: static YAML config | M2: Kafka topic replay | Keys are managed by the Admin API and stored in PostgreSQL; the gateway receives them through the config topic |
| Testcontainers version | Not specified | 1.21.4 (not 1.21.3) | 1.21.3 cannot communicate with Docker Engine 29 (`BadRequestException Status 400`) |
| Added springdoc-openapi 2.8.13 | Not in M1 | Added for Swagger UI | The Admin API needs an interactive API explorer; the library is part of the Spring ecosystem |
| Added jackson-dataformat-yaml | Not in M1 | Added for YAML import | The Admin API accepts YAML seed documents at `POST /admin/import` |

Each deviation is justified by a genuine implementation need. The core architecture (ports-and-adapters,
filter chain, exception hierarchy, immutable records) remains as designed in M1.

---

## 10. AI Usage Declaration

AI-assisted tools (Claude Code, models claude-fable-5-2 and claude-opus-4-6) were used to implement
the system test-first across four phases. For each phase, I provided the requirements and design
constraints, reviewed and approved a plan, watched the tests fail before the implementation, ran
`mvn verify` to confirm all tests passed, and reviewed every generated class. The detailed log
of each session — including the prompt summary, material adopted, modifications I made, and
verification I performed — is in `AI_USAGE_LOG.md` (8 rows).

Final decisions on architecture (ADR-0001 to ADR-0007), design patterns, exception hierarchy,
concurrency strategy and all report-versus-code conflicts were made by me based on the project
scope, the M1 reports and the academic guidelines.
