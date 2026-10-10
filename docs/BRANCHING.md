# Branching strategy and quality gates

Argus uses **GitHub Flow with protected `main`**: `main` is always releasable, and all work
happens on short-lived branches merged through pull requests.

## Branches

| Branch | Purpose | Lifetime |
|---|---|---|
| `main` | Always builds and passes all tests. Milestone baselines are tagged here. | Permanent |
| `feature/<module>-<topic>` | New functionality, e.g. `feature/ratelimit-token-bucket` | Hours to a few days |
| `fix/<issue-id>-<topic>` | Defect fixes linked to a GitHub Issue, e.g. `fix/42-breaker-halfopen-race` | Short |
| `refactor/<topic>` | Behaviour-preserving restructuring (code-smell remediation evidence) | Short |
| `docs/<topic>` | Documentation and SQA artifacts | Short |

## Rules for `main` (GitHub branch protection)

1. No direct pushes: changes arrive only through pull requests.
2. The `ci` workflow must pass (build, tests, coverage report, Checkstyle).
3. The PR template checklist must be completed (tests written first, RTM updated, AI use logged).
4. Branches must be up to date with `main` before merging.
5. **Merge commits only. Squash merging is disabled**, so the red-green-refactor commit
   history of every feature remains visible on `main` (course requirement).

## Commit convention (Conventional Commits)

```
test: add failing test for token refill after period      <- red
feat: refill tokens based on elapsed time                 <- green
refactor: extract TimeSource to make refill testable      <- refactor
```

Types: `test`, `feat`, `fix`, `refactor`, `docs`, `build`, `ci`, `chore`.
Reference issues in the body (`Refs #12`, `Fixes #42`).

## Baselines

Each milestone submission is tagged on `main`: `v0.1-m1`, `v0.2-m2`, `v1.0-m3`.
Tags are never moved. The SQA baseline report references these tags.
