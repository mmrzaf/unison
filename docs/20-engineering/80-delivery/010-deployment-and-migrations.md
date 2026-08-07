# Deployment and migrations

Waiotech deploys immutable versioned artifacts through serialized, verified activation. Schema and data changes use forward-only migrations, expand-and-contract compatibility, explicit verification, and non-destructive application rollback.

## The canonical release unit

Release identifiable compatible server, web, content, and Android artifacts.

One coordinated server-and-web release set contains server image, Dashboard image, Platform Admin image, Public Website artifact, migration set, OpenAPI, generated clients, catalogues and registries, documentation content identity, mission content identity, release manifest, and build provenance. API and Worker may use one server image with separate entry points.

Android Work App is an independently distributed signed artifact whose release manifest declares compatible API, Work Package, journal, synchronization, documentation, and minimum-server contract versions.

## Use immutable role-specific server and web artifacts plus a separately signed Android artifact

Waiotech must use immutable role-specific server and web artifacts plus a separately signed Android artifact.

The canonical deployable web and server artifacts are `waiotech-server`, `waiotech-dashboard`, `waiotech-admin`, and `waiotech-website`. The server image provides explicit API, Worker, migration, and controlled CLI entry points. Android Work App is a signed application package rather than a runtime image.

## Share code artifacts without sharing runtime responsibility

Share code artifacts without sharing runtime responsibility.

They share Domain, Application, persistence, migrations, generated definitions, and schema assumptions. One image reduces interpretive drift while role-specific commands and privileges remain separate.

## Deploy the exact artifact that passed verification

Deploy the exact artifact that passed verification.

Digests, tags, and bytes do not change after acceptance. Environment configuration is external. Promotion does not rebuild.

## Make software supply chain reconstructable

Waiotech must make software supply chain reconstructable.

Release identity includes image digest, source commit, build identity, dependency provenance, software bill of materials, and signature or equivalent provenance where required.

## Specify orchestration capabilities rather than one vendor

Specify orchestration capabilities rather than one vendor.

Docker Compose is conforming when it provides immutable images, separate roles, private networks, secrets, health, resources, persistent storage, locking, and compatible rollback. Another adapter requires equivalent guarantees.

## Separate immutable software, configuration, and credentials

Waiotech must separate immutable software, configuration, and credentials.

Configuration is external, schema-validated, environment-scoped, and technical. Secrets use approved secret management and never appear in images, frontend bundles, logs, migrations, or release manifests.

## A release manifest

Waiotech must make the compatible deployment set explicit.

It identifies source commit, image digests, migration head, OpenAPI digest, generated-client and catalogue digests, configuration schema, supported PostgreSQL and Redis versions, and compatibility constraints.

## Separate deployment authority from product administration

Waiotech must separate deployment authority from product administration.

A narrowly authorized engineering or automation identity may activate verified images, run approved migrations, update approved configuration, inspect bounded health, and perform compatible rollback. It has no ordinary business-record authority.

## Ensure one controller for release activation

Ensure one controller for release activation.

An attributable recoverable lock prevents overlapping deployment and schema change in one environment.

## The deployment sequence

Waiotech must use one repeatable verified activation process.

The sequence is:

```text
verify manifest
-> validate configuration and secrets
-> verify infrastructure compatibility
-> acquire deployment lock
-> establish required recovery protection
-> run migrations
-> activate compatible backend roles
-> activate Public Website, Tenant Dashboard, Platform Admin, and compatible help content
-> verify Android compatibility policy and distribution metadata
-> verify readiness
-> run external smoke tests
-> record deployment
-> release lock
```

## Use explicit operational fencing rather than inconsistent service

Waiotech must use explicit operational fencing rather than inconsistent service.

Maintenance mode or write fencing is required whenever online compatibility cannot preserve one authoritative meaning. It safely rejects mutations, pauses affected Workers, and does not alter Tenant lifecycle.

## Do not mix versions that interpret durable records differently

Waiotech must not mix versions that interpret durable records differently.

Only when the release manifest declares compatibility with the active schema, event and job envelopes, receipts, process records, and generated contracts.

## Fail closed on schema incompatibility

Fail closed on schema incompatibility.

API, Worker, migration, and CLI verify compatible migration head before performing responsibilities.

## Use one governed schema evolution mechanism

Waiotech must use one governed schema evolution mechanism.

Alembic or an approved equivalent provides ordered immutable migration identity, history, transactional execution where supported, and head verification.

## Keep schema change explicit and separately privileged

Waiotech must keep schema change explicit and separately privileged.

Startup verifies compatibility. Only the protected migration entry point changes schema.

## Preserve authoritative schema and data history

Waiotech must preserve authoritative schema and data history.

Relied-upon migration files are immutable. Recovery uses rollback of uncommitted transactions, forward correction, compatible application rollback, or verified restoration rather than destructive down migration.

## Make migration semantics and operational risk explicit

Waiotech must make migration semantics and operational risk explicit.

It defines identity, owner, predecessor, affected structures, data transformation, compatibility, locking, recovery, verification, and removal eligibility for obsolete structures.

## Expand-and-contract

Waiotech must use bounded compatibility for zero- or low-downtime change.

The sequence is:

```text
expand compatible structure
-> deploy code supporting the compatibility contract
-> migrate or backfill data
-> switch canonical reads and writes
-> verify
-> remove obsolete compatibility
```

Only one write meaning is authoritative throughout.

## Prevent dual data authority

Waiotech must prevent dual data authority.

A compatibility stage declares canonical write, deterministic projection, or transactionally constrained dual representation. Conflicting writes are prohibited.

## Make large transformations resumable and isolated

Waiotech must make large transformations resumable and isolated.

They use durable engineering jobs, bounded Tenant-scoped batches, deterministic ordering, checkpoints, idempotency, safe pause, verification, and completion evidence. They do not use one unbounded transaction.

## Preserve truthful history during transformation

Waiotech must preserve truthful history during transformation.

Unknown source meaning remains unresolved, uses only an explicitly authorized migration-origin value, or blocks affected action. Database nullability does not justify invention.

## Remove old representation after its authority has ended and verification passes

Waiotech must remove old representation after its authority has ended and verification passes.

Only after canonical data is complete, all writers and required readers use canonical contracts, constraints and counts reconcile, compatibility use is absent, recovery supports the canonical shape, and rollback no longer depends on the obsolete representation.

## Fail deployment on migration uncertainty

Fail deployment on migration uncertainty.

Deployment is not accepted. Engineering uses transaction rollback, forward correction, or restoration. Ordinary service does not continue against an unverified authoritative shape.

## Application rollback

Waiotech must keep software rollback non-destructive and compatibility-based.

It activates a preceding image set without reversing schema or product history. It is permitted only when that release supports the active schema, event and job envelopes, receipts, configuration, and artifact formats.

## Separate software recovery from business correction

Waiotech must separate software recovery from business correction.

It does not reverse Work Orders, Inventory Movements, Decisions, revisions, audit, Notifications, or Tenant lifecycle.

## Recover durable learning progress and definitions while rebuilding disposable synthetic Tenant state

Waiotech must recover durable learning progress and definitions while rebuilding disposable synthetic Tenant state.

Learning-Tenant state is reproducible from versioned seeds and may be excluded from operational recovery objectives. Onboarding progress, mission definitions, and seed definitions remain durable and recoverable.

A backup containing learning data must still protect it as Tenant data, but restoration does not require preserving a learner's synthetic business state when deterministic reset is available.

## Related documents
- [Backup and recovery](020-backup-and-recovery.md)
- [Testing and software release](050-testing-and-release.md)
