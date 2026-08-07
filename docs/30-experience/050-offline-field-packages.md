# Offline field packages

Offline authority is explicit, server-issued, User- and installation-bound, narrow, expiring, and auditable. Waiotech uses distinct package contracts for disconnected Maintenance execution and bounded Process field evidence rather than treating the Android database as an alternate Tenant authority.

## Use offline authority only where safe meaning can be established before disconnection

Disconnected work is permitted only when the Server can issue an immutable package that contains or references every fact needed to bound the authorized action safely.

A package never grants general Tenant Permission. It grants only the exact offline actions declared by its package type, subject set, User, installation, Tenant, issuance basis, and expiry.

The two canonical Alpha package types are:

- **Work Package:** offline authority for one Work Order;
- **Process Package:** offline authority for an explicit bounded set of Process subjects and field-evidence actions.

These package types may share technical envelope mechanisms but must not lose their distinct product meaning.

## Work Package

A Work Package is an immutable server-issued offline execution snapshot for exactly one Work Order.

It may include:

- Work Order identity and concurrency basis;
- Work Target and relevant Plant context;
- exact Procedure Revision and Task snapshot;
- field-visible Requirements and Readiness basis;
- permitted offline action codes;
- evidence contract;
- material allocation or reservation references where authorized;
- issuance and expiry;
- User and Android installation;
- package schema and authenticity information.

A Work Package never authorizes planning, release, verification, closeout, unrestricted Inventory posting, or another Work Order unless Product Authority explicitly says otherwise.

## Process Package

A Process Package is an immutable server-issued offline authority snapshot for pre-identified Process field work.

It may include:

- one or more explicit Process Units or Process Streams;
- zero or more explicit Process Rounds together with their exact immutable Process Routine Revision entry snapshots, occurrence identity, execution window, and required-entry contract;
- explicit Measurement Points available for manual Reading entry;
- relevant active Process Conditions when their evidence may be updated offline;
- recent bounded context needed to interpret field work;
- permitted offline action codes;
- evidence contract;
- issuance and expiry;
- User and Android installation;
- package schema and authenticity information.

Canonical offline Process actions may include:

- start or continue an included Process Round;
- satisfy included Process Round entries by recording their canonical Reading or Observation evidence;
- complete an included Process Round only when the package contains its exact immutable entry/evidence contract and local validation can establish that every required entry is represented;
- record a manual Process Reading for an included Measurement Point;
- record an Operational Observation against an included subject;
- attach field evidence to an included subject or Condition;
- record an Operational Action or outcome evidence against an included Process Condition only when the package explicitly authorizes that action and contains the required authoritative basis.

Process Round completion is independent of plant-condition resolution: completing a Round establishes only that the required collection work was performed.

Opening, resolving, reopening, reassigning, or otherwise changing Process Condition lifecycle while disconnected is not implicitly authorized. When the Server cannot safely pre-authorize the lifecycle change, Android preserves a typed local draft or field evidence and establishes the canonical command only after synchronization and fresh authorization.

## Offline Work Request draft

Android may preserve a typed encrypted Work Request draft while disconnected.

The draft is not a Work Request, audit event, or accepted maintenance need. It may contain candidate Functional Location or Asset context, narrative, occurred time, evidence, and a stable local operation identity. On synchronization, an explicit User submission invokes the canonical Work Request command and receives the authoritative result.

## Package issuance

The Server issues a package only after authoritative evaluation of:

- authenticated User and active Membership;
- Android installation;
- Tenant condition;
- exact Permission and responsibility;
- subject lifecycle;
- action prerequisites;
- applicable Readiness or Process safety constraints;
- material authority where applicable;
- package expiry and security policy.

Issuance is an attributable auditable action and is duplicate-safe.

## Expiry and renewal

Every package expires at the earliest safe limit established by product, security, lifecycle, source freshness, or operational constraints.

Renewal creates a new issued package after fresh authoritative evaluation. Renewal does not mutate historical package evidence or extend authority silently.

Expiry prevents new offline actions but does not destroy already recorded field evidence.

## Device and User binding

A package is bound to exactly one User, one Android installation, and one Tenant.

It cannot be transferred, shared, exported as a reusable credential, or used after installation revocation, package revocation, or expiry. Several Users collaborating on the same plant subject receive independent authority and independent journals.

## Offline action journal

Accepted local field actions are appended to an encrypted ordered journal.

Each entry preserves:

- stable local operation identity;
- package identity and type;
- action code and schema version;
- local sequence;
- typed input;
- evidence references;
- device-observed time;
- correction relationship where applicable;
- synchronization condition.

Synced entries are not edited. Corrections are additive.

## Time and ordering

Device time is evidence, not authoritative system time.

The journal preserves local causal order independently from wall-clock accuracy. Synchronization records Server acceptance time and preserves both the field-observed time and authoritative recorded time required by the owning domain.

## Synchronization

Each offline journal operation synchronizes through the same owning Application command used by connected operation, with a stable operation identity and canonical Command Receipt.

Synchronization is operation-by-operation, resumable, idempotent, and ordered. One package upload is not one unbounded transaction.

Accepted earlier operations remain accepted if a later dependent operation cannot be established safely.

## Conflict handling

The Server never silently rewrites field evidence to make it fit changed authoritative state.

When authoritative state changed after package issuance, synchronization returns a typed result that distinguishes accepted, already established, rejected, needs review, or conflict conditions. Field evidence remains attributable even when the intended domain action cannot be accepted.

The User experience explains what changed and what action is required next.

## Offline Inventory boundary

Offline material evidence may be captured only when the Work Package or other explicit contract identifies the authorized allocation or custody basis.

Android cannot infer successful issue, return, transfer, Reservation release, or Stock Balance from cached values. Synchronization invokes canonical Inventory authority and preserves reconciliation if authoritative custody prevents posting.

## Evidence and files

Local files remain field evidence until canonical Attachment acceptance occurs.

Synchronization preserves file integrity, Actor, subject, package, operation identity, and relationship to the canonical action. Failed transfer or rejected action must not silently discard captured evidence.

## Revocation

Revocation stops future authority when the app learns of it. A disconnected device cannot be guaranteed to learn revocation before reconnecting, so package scope and expiry are the primary bound on disconnected risk.

Revocation never erases already recorded local evidence. Synchronization evaluates it truthfully against the package and authoritative state.

## Related documents

- [Android Work App](040-android-work-app.md)
- [Work Order lifecycle and Readiness](../10-product/40-maintenance/060-work-order-lifecycle-and-readiness.md)
- [Process Readings and Operational Observations](../10-product/35-process/010-readings-and-observations.md)
- [Android installation and offline authority](../20-engineering/60-applications/030-android-installation-and-offline-authority.md)
