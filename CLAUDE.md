# CLAUDE.md — rules for working on Argus

Argus is a distributed API gateway: three identical gateway instances behind Nginx, sharing
Redis rate-limit counters and a Kafka event log, with PostgreSQL for configuration and usage
data. It is a solo semester project for SE423 (Software Construction) and SE431 (Software
Quality Assurance) at GIKI. The author must be able to explain and defend every line in a viva,
so prefer simple, readable code and explain each step in plain words.

Source of truth, in `docs/milestones/`:
`Argus_Scope_Document_v1.3.pdf`, `Argus_SE423_M1_Report.pdf`, `Argus_SE431_M1_Report.pdf`,
`[Updated] SE423_SE431_Joint_Project_Guidelines.pdf`. Console designs are in `docs/console/`,
diagram sources in `docs/diagrams/`, decisions in `docs/adr/`.

## 1. The design is fixed by the Milestone 1 reports

- Keep the seven functional modules and their code locations. Do not invent new modules and do
  not rename classes that appear in the reports.

  | # | Module | Code location |
  |---|---|---|
  | M1 | Routing & Load Balancing | `dev.argus.core.routing`, `dev.argus.core.loadbalancing` |
  | M2 | Authentication & API Key Management | `dev.argus.core.auth`, `argus-admin` |
  | M3 | Distributed Rate Limiting | `dev.argus.core.ratelimit`, Redis adapter in `argus-gateway` |
  | M4 | Resilience | `dev.argus.core.resilience` |
  | M5 | Admin API & Configuration Propagation | `argus-admin`, config consumer in `argus-gateway` |
  | M6 | Metering & Quota Service | `argus-metering` |
  | M7 | Abuse Detection Service | `argus-abuse` |

- Shared core packages: `core.model` (immutable value types, JDK only), `core.pipeline`
  (filter chain), `core.events` (control-plane ports), `core.exception`, `core.time`.
- If the reports and the code disagree, or the reports are unclear, **stop and ask** before
  changing either. Record the decision.

## 2. Supporting components (not functional modules)

- `argus-contracts` — shared event records and topic names.
- `upstreams/mock-service` — demo backend run as Users, Orders and Products, with
  `POST /fault` for fault injection.
- `dashboard/` — admin console (React + TypeScript). No gateway business logic.
- `tests/k6/` — load, burst and attack scripts.
- `deploy/` — Docker Compose, Nginx, Prometheus, Grafana, Kubernetes manifests, seed script.

## 3. Framework-free core

- `argus-core` depends only on the JDK and `argus-contracts`. No Spring, Redis, Kafka or JPA
  imports in core. Infrastructure is reached through ports implemented in `argus-gateway`.
- This is enforced by ArchUnit (`argus-core/src/test/java/dev/argus/core/ArchitectureTest.java`),
  which also forbids package cycles. Never weaken these rules to make code compile.

## 4. Design patterns — use the ones named in the reports

| Pattern | Classes |
|---|---|
| Builder | `Route.Builder` |
| Factory Method | `LoadBalancingStrategyFactory`, `FilterChainFactory` |
| Chain of Responsibility | `GatewayFilter` (with `FilterChain`) |
| Strategy | `LoadBalancingStrategy`, `RetryPolicy`, `DetectionRule` |
| State | `CircuitState` (Closed, Open, HalfOpen) |
| Decorator | `FallbackRateLimiter`, `SpillingEventPublisher` |
| Adapter | `RedisRateLimiter`, `KafkaEventPublisher`, `JdkHttpUpstreamClient` |
| Observer | `ConfigTopicConsumer`, `ConfigurationListener` |

No Singleton (use dependency injection) and no Template Method base filter — both were
considered and rejected in the SE423 report.

## 5. Concurrency (Java 21, virtual threads)

- Use `ReentrantLock`, not `synchronized`, on hot paths (`synchronized` pins virtual threads).
- Use `AtomicReference` compare-and-set for state changes (circuit breaker, route snapshot).
- Prefer immutable records and defensive copies; no static mutable state.

## 6. Errors

- Use the existing `ArgusException` hierarchy in `dev.argus.core.exception`:
  `ClientRejectionException` (401/403/404/429), `UpstreamException` (503/504),
  `InfrastructureUnavailableException` (never reaches the client),
  `InvalidConfigurationException` (400, Admin API).
- Validate at boundaries (record constructors, Admin API). Clients never see stack traces.

## 7. Technology stack

- The stack is Section 6.4 of the scope document. **Ask before adding any library that is not
  listed there.**

## 8. Test-driven development — always

1. Write a failing test.
2. Run it and **show that it fails**.
3. Write the smallest code that makes it pass.
4. Refactor with all tests green.

- Separate commits, Conventional Commits: `test: …`, `feat: …`, `refactor: …`
  (also `fix`, `docs`, `build`, `ci`, `chore`). **Never squash.**
- Work on short-lived branches (`feature/<module>-<topic>`, `fix/…`, `refactor/…`, `docs/…`);
  `main` is protected. See `docs/BRANCHING.md`.

## 9. Test names

- Name tests after requirement and test-case IDs where they exist (for example `TC-08.1`,
  `FR-10`). IDs come from the RTM in the SE431 report.
- Never attach an ID to a test that does not actually verify that requirement.

## 10. Definition of done

- Run `mvn verify` (or `./mvnw -B verify`) and see it pass before saying any task is done.
- If something could not be run or verified, say so plainly.

## 11. Secrets

- Never read, print or commit secrets, tokens or `.env` files.

## 12. AI usage log

- After each task, add a row to `AI_USAGE_LOG.md` (keep its existing columns): tool and model,
  purpose, prompt summary, what was adopted, modifications made, and what the author should
  verify.
