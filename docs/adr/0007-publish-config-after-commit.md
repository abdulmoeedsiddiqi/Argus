# ADR-0007: Publish configuration after the database commit, repair by republishing

- Status: Accepted
- Date: 2026-11-15

## Context

The Admin API stores configuration in PostgreSQL and must send every change to all gateway instances through the
compacted topic `gateway.config` (FR-12). The database and Kafka cannot be changed in one atomic step. The Milestone 1
reports name six admin tables and do not say how the two writes are kept in line, how the administrator signs in, or
how a gateway that is not ready is kept out of rotation. These points were decided when module M5 was built.

## Decision

1. **Publish after commit.** A service saves the change and its audit record in one transaction. The change is sent
   to Kafka from the transaction's after-commit hook, synchronously with `acks=all`. A rollback publishes nothing.
2. **Repair by republishing.** If Kafka cannot be reached after the commit, the request is answered
   `503 NOT_PROPAGATED`. The Admin API sends the whole current state again when it starts and on
   `POST /admin/config/republish`. This is safe because the topic is compacted and gateways apply the latest record
   per key.
3. **One partition.** All changes have one total order, so a delete can never overtake the create it belongs to.
4. **Gateways read without a consumer group**, from the beginning of the topic, on every start. A gateway answers
   `503 NOT_READY` until it has reached the end offset it saw at start-up (readiness gate, QR-09). Nginx retries a 503
   on the next instance.
5. **Admin sign-in** is `POST /admin/login` for one administrator from the environment. It returns an HS256 token
   (Nimbus, issuer `argus-admin`, own secret) that a servlet filter checks on every `/admin/**` call. Spring Security
   is not used because it is not in the technology stack of the scope document.
6. **Consumers are not published.** A gateway needs a consumer only as the owner of a key, and each published key
   carries its consumer and plan. When a consumer moves to another plan, its keys are published again.
7. **The API-key cache is an immutable snapshot** behind an `AtomicReference`, like the route table. (SE423 Table 11
   names a `ConcurrentHashMap`; the snapshot was already in place from the authentication phase and gives the same
   guarantee to readers.)
8. **The seed script** is `scripts/seed.sh`. It sends `deploy/seed/demo-config.yaml` to `POST /admin/import`, which
   applies the document through the same services as single requests.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Transactional outbox table with a relay | The safest option, but it adds a seventh table and a background relay that the Milestone 1 design does not have. Republishing covers the same gap with much less code. |
| Publish before the commit | A failed transaction would leave gateways with a change the database never stored. |
| Kafka transactions | They do not include the PostgreSQL write, so the gap remains. |
| Gateways poll the database | Rejected in the SE423 report: more load, slower, and it ties gateways to the admin schema. |
| Consumer group with stored offsets | A restarted gateway would skip the history and start with empty routes and keys. |

## Consequences

- Between a failed publish and the next republish the gateways lag behind the database.
- Known gap: a deletion whose publish failed is not repaired by republishing, because there is no row left to send.
  The gateways keep that entity until it is created and deleted again. Accepted for this project; an outbox table
  would close it.
- A gateway cannot become ready while Kafka or the configuration topic is unavailable. This is intended: it must not
  serve with unknown revocations.
- Revoked keys stay in the topic as records marked revoked; they are never deleted, so every replay sees them.
