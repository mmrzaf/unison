# Tenant lifecycle

Tenant lifecycle controls when a plant may operate, how its authority is suspended or ended, and how its evidence remains preserved.

## The Tenant lifecycle

```text
provisioning -> active -> suspended -> active
provisioning -> deactivated
active -> deactivated
suspended -> deactivated
```

Deactivation is terminal in the ordinary product model. Recovery from operator
error uses a protected platform recovery contract and preserved evidence rather
than a normal lifecycle action.

## Provisioning

A provisioning Tenant may receive validated configuration, imported data, and
protected platform setup. Ordinary Membership authority and operational work are
blocked until activation succeeds.

Tenant Import creates a new provisioning or inactive Tenant. It never merges
into an existing Tenant and never exposes partially validated data as active.

## Suspension

Suspension blocks ordinary operational mutation while preserving all Tenant data,
history, audit, scheduled obligations, attachments, and identity relationships.
Only explicit suspension-safe actions may run, including protected inspection,
export, remediation, and reactivation where authorized.

Background processing must not fabricate successful work during suspension.
Notifications and Android synchronization follow explicit suspension behavior;
queued facts remain durable and are reconciled without rewriting history.

## Reactivation

Reactivation resumes ordinary evaluation from current authoritative facts. It
does not pretend that the suspension period did not occur. Overdue work,
expired authority, missed schedules, stale offline field packages, and pending
Notifications are recalculated or reconciled through their owning contracts.

## Deactivation

Deactivation ends ordinary operation and new Membership authority. It preserves
historical identity, audit, maintenance, Inventory, Reports, Notifications,
Attachments, exports, imports, and lifecycle evidence according to retention and
preservation rules.

Deactivation revokes browser and mobile sessions, Android installations,
outstanding offline field packages, delegated download URLs, and other Tenant-scoped
credentials. It does not delete historical Memberships, assignments, audit, or
business evidence.

## Offboarding checks

Deactivation requires one protected assessment that makes unfinished plant obligations explicit without forcing them into false terminal states.

The assessment must identify at least:

- open Process Conditions and monitoring obligations;
- unfinished Work Orders and Scheduled Work;
- unresolved Findings and Failure Events;
- Inventory on-hand custody, active Reservations, replenishment, and Stocktaking obligations;
- queued or running Reports, Notifications, and Tenant portability operations;
- open file upload or accepted-file processing obligations;
- Android installations, sessions, offline field packages, unsynchronized evidence, and synchronization obligations;
- active Integration Principals and machine credentials;
- active Data Sources and machine-ingestion obligations;
- retention, preservation, and required export obligations.

Open business records do not have to be falsely closed before deactivation. The protected deactivation action records the unresolved counts, reviewing platform Actor, reason, assessment time, and explicit acceptance that ordinary Tenant operation is ending with those facts preserved.

Deactivation must not proceed while an atomic or externally consequential operation is in a state that cannot be safely stopped or preserved, including a queued or running Tenant import/export cutover, an in-progress destructive storage action, or another explicitly defined protected operation. Such operations must complete, fail safely, or be cancelled according to their owning contract.

Except for a provisioning Tenant that never entered ordinary operation, deactivation requires one completed Tenant Export whose declared cutoff is at least as recent as the latest authoritative Tenant activity at the start of the protected deactivation assessment. Activity created by the deactivation action itself does not invalidate that export requirement.

The deactivation transaction revokes ordinary browser and mobile authority, Android installations and offline field packages, Integration Principal credentials, delegated Tenant file URLs, and other Tenant-scoped credentials according to their engineering contracts. Historical identities and evidence remain preserved.

## Related documents
- [Tenant portability](020-tenant-portability.md)
- [Preservation and destruction](030-preservation-and-destruction.md)
- [Authentication, sessions, and secrets](../../20-engineering/40-security/030-authentication-sessions-and-secrets.md)
