# AI Usage Log

Required by Section 9 of the SE423/SE431 project guidelines. Updated continuously and submitted with every milestone.

| # | Date | Tool (name, model) | Purpose | Prompt / activity summary | AI material adopted | Modifications made | Verification performed |
|---|---|---|---|---|---|---|---|
| 1 | 2026-10-10 | Claude (claude-opus-5-5) | Project selection | Asked for project ideas suited to SE423/SE431 and backend/DevOps careers; compared options | Choice of distributed API gateway; Kafka role (metering, abuse detection, config propagation) | Rejected an earlier Kafka design that was only "nice to have"; required Kafka to solve a real problem | Checked the project against every Section 3.1 and 3.2 requirement in the guidelines |
| 2 | 2026-10-10 | Claude (claude-opus-5-5) | Scope document draft | Generated the Project Scope Document v1.0, then v1.1 merging parts of a second draft | Document structure and text | Chose title, scope boundaries, and in/out-of-scope items; selected which parts of the second draft to keep | Reviewed against the GIKI sample template; checked references |
| 3 | 2026-10-10 | Claude (claude-opus-5-5) | M1 design and repository skeleton | Designed core interfaces, contracts, Maven modules, CI, ADRs and SE423 M1 document | Interfaces and records in `argus-core` and `argus-contracts`, POMs, CI workflow, ADRs, M1 document draft | _To complete: what I changed after review_ | Core and contracts compiled with `javac -Xlint:all` (no warnings). _To complete: `mvn -B verify` result on my machine and CI run link_ |
