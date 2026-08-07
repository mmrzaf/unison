# Consistency, concurrency, and correction

Authoritative writes use explicit transaction, lock, isolation, concurrency, projection, deletion, and additive-correction rules. Redis and replicas cannot become authoritative write paths.

## Require explicit stale-write protection for mutable authoritative state

Waiotech must require explicit stale-write protection for mutable authoritative state.

Mutable aggregates carry a monotonic concurrency version. Commands update through a compare-and-swap condition and reject stale versions without overwriting intervening changes.

## Use database locking only where it directly protects an invariant

Waiotech must use database locking only where it directly protects an invariant.

Row locks are used for contested records where the invariant depends on serialized access, including stock posting, unique process claims, publication contention, and bounded assignment transitions. Locks remain narrow and transactions remain short.

## Do not use advisory locks as an unbounded global mutex

Waiotech must not use advisory locks as an unbounded global mutex.

Advisory locks are permitted for a defined coordination key when row ownership is unavailable or cross-row serialization is required. The key, acquisition order, timeout, and recovery behavior must be documented and tested.

## Choose isolation from invariant requirements and test real concurrency

Choose isolation from invariant requirements and test real concurrency.

The command contract selects the lowest PostgreSQL isolation level that preserves its invariants. Serialization failures and deadlocks are classified and retried only when the command remains idempotent and safe.

## Keep command truth on authoritative PostgreSQL

Waiotech must keep command truth on authoritative PostgreSQL.

Authoritative commands and Readiness evaluations use the primary authoritative database. Replica-backed queries are limited to contracts that declare tolerated lag and cannot affect protected action acceptance.

## Keep projections rebuildable and subordinate

Waiotech must keep projections rebuildable and subordinate.

A projection declares its authoritative sources, derivation, freshness, rebuild behavior, and explicit non-authority. Loss or staleness does not alter source records. Commands do not depend on a projection unless its consistency contract proves authoritative equivalence.

## Do not hide product lifecycle behind row deletion

Waiotech must not hide product lifecycle behind row deletion.

Product lifecycle actions use cancellation, retirement, revocation, withdrawal, or deactivation as defined by Product Authority. Physical deletion is limited to disposable technical data or a separate approved destruction contract.

## Make data correction additive, attributable, and verifiable

Waiotech must make data correction additive, attributable, and verifiable.

A correction uses a typed protected command or migration that preserves original evidence, correction reason, Actor, effective and recorded time, affected relationships, audit, and resulting canonical state. Direct operational SQL is not an ordinary correction path.

## Correct Process evidence without erasing provenance

Process Reading, Operational Observation, Operational Action, and immutable Reliability evidence use additive correction or supersession according to Product Authority.

A corrected Reading preserves the original reported value, source, effective time, quality, and Actor. Derived current views and Maintenance trigger evaluation exclude invalidated evidence according to the canonical correction state without deleting historical source evidence.

## Coordinate cross-domain consistency in Application transactions

When one User intent affects several owning domains, the Application layer coordinates explicit domain commands inside the smallest safe transaction boundary or through a documented durable follow-up obligation.

No domain aggregate may mutate another domain's tables directly. For example, Process escalation may establish a Maintenance Work Request and relationship while Process retains Condition authority; Maintenance material use may establish Inventory custody movement while Maintenance retains usage meaning.

## Use database constraints as part of the product-preserving implementation

Waiotech must use database constraints as part of the product-preserving implementation.

Migrations and continuous integration verify foreign keys, Tenant constraints, uniqueness, code domains, nullability, immutable relationships, revision effectivity, assignment integrity, Command Receipt uniqueness, outbox integrity, and artifact-to-Attachment relationships.

## Related documents
- [Persistence and Tenant isolation](010-persistence-and-tenant-isolation.md)
- [Commands and idempotency](../30-actions-and-contracts/010-commands-and-idempotency.md)
- [Audit, events, and transactional outbox](../30-actions-and-contracts/020-audit-events-and-outbox.md)
