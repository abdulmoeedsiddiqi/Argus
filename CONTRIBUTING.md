# Contributing

## Test-driven development rhythm

1. Write a failing test that states the next small behaviour. Commit with `test:`.
2. Write the simplest code that makes it pass. Commit with `feat:` or `fix:`.
3. Improve the design without changing behaviour; all tests stay green. Commit with `refactor:`.

Business logic belongs in `argus-core` (plain Java, no framework imports, enforced by
`ArchitectureTest`). Infrastructure code (Spring, Kafka, Redis, JPA) belongs in adapter modules.

## Before opening a pull request

- `mvn -B verify` passes locally.
- New behaviour has tests, including failure and concurrency cases where relevant.
- The Requirements Traceability Matrix is updated if a requirement is affected.
- Significant AI assistance is recorded in `AI_USAGE_LOG.md`.
- Defects found are recorded as GitHub Issues with the `defect` label (feeds the CAPA log).
- Code smells found are recorded as Issues with the `code-smell` label (refactoring evidence).
