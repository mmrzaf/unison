# Backup and recovery

A recovery set combines PostgreSQL, accepted file bytes, encryption material, manifests, and required configuration. Backup reliability is proven through isolated restore rehearsal and explicit disaster-recovery sequencing.

## A backup

Waiotech must keep recovery infrastructure separate from portability.

A backup is a protected recovery copy of the installation's durable state. It is not Tenant Export, report export, Tenant Import, database migration, or product undo.

## A recovery set

Back up structured truth and accepted bytes as one recoverable system.

A recovery set contains PostgreSQL backup or point-in-time chain, accepted Attachment and artifact bytes, integrity metadata, backup manifest, migration head, release identity, configuration schema, and required encryption-key references.

## Keep recovery independent from transient delivery state

Waiotech must keep recovery independent from transient delivery state.

Recovery must succeed with empty Redis by reconstructing queues, leases, and caches from PostgreSQL. Redis backup may be an operational convenience only.

## Capture database and file state under one consistency contract

Capture database and file state under one consistency contract.

A coordinated pause, snapshots, transaction-log boundary plus storage snapshot, or another verified protocol ensures the restored database does not reference absent accepted bytes.

## Make recovery sets self-describing and verifiable

Waiotech must make recovery sets self-describing and verifiable.

It contains backup identity, source environment, creation time, database recovery position, migration head, release identity, storage snapshot, artifact list, sizes, checksums, encryption metadata, and component outcomes.

## Treat recovery copies as highly sensitive assets

Waiotech must treat recovery copies as highly sensitive assets.

Backups are encrypted in transit and at rest, integrity-protected, privately stored, access-controlled, audited, and isolated from the active host and applicable administrative failure domains.

## Make accepted data-loss and recovery-time tolerances explicit

Waiotech must make accepted data-loss and recovery-time tolerances explicit.

Each durable environment declares recovery point objective, recovery time objective, method, isolation, rehearsal cadence, and operational authority in an Operations Standard or ADR.

## Treat restoration as the proof of recoverability

Waiotech must treat restoration as the proof of recoverability.

An isolated restore rehearsal proves that PostgreSQL, files, application release, authentication, Tenant isolation, Workers, outbox, and governed artifacts can be reconstructed. Successful backup creation alone is insufficient.

## Prevent rehearsal from creating operational consequences

Waiotech must prevent rehearsal from creating operational consequences.

It uses isolated credentials, storage, database, Redis, and network. External Notification delivery, public downloads, and other non-local side effects are disabled or redirected.

## Test the complete system, not only database import

Test the complete system, not only database import.

It verifies manifest integrity, schema compatibility, database records, Attachment existence and digest, Report and Tenant Export Artifacts, authentication, Tenant isolation, representative protected behavior, Worker recovery, outbox recovery, and rebuildable projections.

## The disaster-recovery sequence

Restore one authoritative installation and prevent split brain.

The sequence is:

```text
declare incident and fence writes
-> select verified recovery position
-> establish replacement infrastructure
-> restore PostgreSQL and accepted files
-> verify integrity and schema
-> activate compatible release
-> keep external delivery controlled
-> reconcile outbox, processes, and integrations
-> run recovery checks
-> authorize service
-> preserve evidence
```

## Make recovery-point loss and external divergence explicit

Waiotech must make recovery-point loss and external divergence explicit.

The selected PostgreSQL transaction-log position is reconciled with file state, outbox, process records, artifacts, and external acknowledgements. Commands after the recovery point are absent and require incident reconciliation rather than invention.

## Recover processes from PostgreSQL truth

Waiotech must recover processes from PostgreSQL truth.

Durable state determines resume, retry, fail, cancel, or reconcile. Expired Worker lease does not prove product failure. Redis may be empty.

## Do not use backup as Tenant movement or creation

Waiotech must not use backup as Tenant movement or creation.

Injecting backup data into another Tenant or active installation requires a separate Product Authority contract for identities, Users, credentials, Attachments, integrations, events, and collision handling.

## Include key recoverability in disaster-recovery design

Waiotech must include key recoverability in disaster-recovery design.

Key identity, protected backup, separation from encrypted data, authorization, and rotation compatibility must preserve decryptability for the recovery lifetime. Keys are not stored inside ordinary backup payloads.

## Liveness, startup, and readiness

Waiotech must keep health meanings distinct and responsibility-aware.

Liveness proves process health, startup proves initialization and compatibility, and readiness proves the process can safely perform its assigned responsibility.

## Remove unsafe API instances from service

Waiotech must remove unsafe API instances from service.

Configuration, PostgreSQL connectivity, schema compatibility, security initialization, and required storage access. Redis is required only for API responsibilities that cannot safely operate without it.

## Prevent unqualified Workers from claiming obligations

Waiotech must prevent unqualified Workers from claiming obligations.

Configuration, PostgreSQL, schema, Redis delivery, claim capability, file storage, and supported job schemas.

## Base activation on verified responsibility

Base activation on verified responsibility.

Only after manifest, migrations, verification, readiness, smoke tests, and deployment identity all succeed with no unresolved critical compatibility defect.

## Verify release and recovery as complete operational contracts

Waiotech must verify release and recovery as complete operational contracts.

Tests cover immutable server and web artifacts, signed Android artifacts, secret absence, locking, migrations, overlap compatibility, website and help activation, Android compatibility policy, readiness, smoke tests, non-destructive rollback, coordinated backup, encryption, isolated restoration, file integrity, Worker and outbox recovery, learning-progress recovery, and integration and learning-side-effect fencing.

## Related documents
- [Deployment and migrations](010-deployment-and-migrations.md)
- [Generated artifacts and file recovery](../70-files/030-generated-artifacts-and-recovery.md)
- [Testing and software release](050-testing-and-release.md)
