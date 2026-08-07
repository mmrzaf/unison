# Canonical engineering model

Waiotech is a Product-Authority-driven modular monolith whose implementation favors explicit ownership, exact history, least authority, deterministic contracts, professional user experience, and operational recovery over generic extensibility.

## Complete authority chain

Implementation remains subordinate to authority:

```text
Product Authority
→ Engineering Authority
→ bounded ADRs and Experience Contracts
→ module-owned canonical definitions
→ generated contracts and references
→ implementation
→ tests, release evidence, and operational evidence
```

A lower layer must not resolve a higher-layer ambiguity by inventing alternate product meaning.

## Complete runtime model

```text
Waiotech
├── API
├── Worker
├── Tenant Dashboard
├── Platform Admin
├── Android Work App
├── Public Website
├── Documentation and Help Center
├── learning Tenant experience
├── migration entry point
└── controlled engineering CLI
```

PostgreSQL owns durable structured truth. Governed file storage owns accepted immutable bytes. Redis provides non-authoritative delivery and transient coordination.

## Canonical module model

```text
Plant
Process
Maintenance
Inventory
Reliability
IAM
Policy
Evidence and communications
Portability
```

Module ownership follows Product Authority rather than existing table names or route layout.

## Strongest architecture rules

Waiotech must preserve:

- module-owned facts and actions;
- inward layer dependencies;
- Application-owned cross-domain orchestration;
- one truthful transaction boundary for atomic accepted actions;
- durable outbox obligations for external effects;
- additive correction of accepted evidence;
- explicit Tenant isolation;
- no generic business meta-models.

## Strongest authority rules

Human authority flows through Membership, Team Access Profile Assignment, Access Profile Revision, Access Grant, Permission, and domain action guards.

Integration Principals use separate narrow machine authority. Internal system Actors are exact product-defined jobs. Platform authority is explicit and cannot become ordinary Tenant authority.

No authority path may impersonate another Actor category.

## Strongest history rules

Published revisions, posted Inventory Movements, accepted Process Readings and Observations, Process Condition history, Operational Actions, Failure Event evidence, Cause Assessments, accepted attachments, audit, and other Product Authority-defined evidence are immutable or additively corrected.

Functional Location and Asset installation history must preserve physical replacement without rewriting stable-position history.

Measurement Source Mapping changes and machine source changes must preserve prior Reading provenance.

## Strongest contract rules

Commands and queries are typed. APIs expose named domain actions. OpenAPI is canonical transport authority for browser and machine clients. Generated Dashboard and Admin clients must remain in parity. Android contracts must remain version-compatible with the supported release policy.

Clients never recreate Server authority.

## Strongest experience rules

Tenant Dashboard must implement Product Authority as an operational workspace rather than storage-shaped CRUD.

Purpose-specific read models, available actions, connected context, fast Process Reading entry, master-detail workspaces, operational timelines, and consistent Plant navigation are engineering obligations where Experience Contracts require them.

Frontend architecture must not force each Domain aggregate into a generic registry/detail/edit template.

## Strongest operational rules

Background obligations are recoverable from PostgreSQL. Workers use durable claims and bounded leases. Machine ingress is authenticated, idempotent where required, rate-limited, attributable, and source-authorized. Deployments use immutable artifacts. Backup correctness requires tested restoration. Observability remains protected and non-authoritative.

## Prohibited generic mechanisms

Waiotech must not introduce universal Product Authority through:

- Role authorization;
- direct profile grants outside the canonical human path;
- generic IAM deny/scope expressions;
- universal lifecycle engines;
- universal Plant Object or relationship engines;
- generic Measurement or Event authorities;
- unrestricted selectors or workflow scripts;
- arbitrary core-domain JSON;
- browser-side authorization;
- Redis business truth;
- external mutation of derived state;
- in-place replacement of accepted files or evidence.

## Canonical migration baseline and retained history

Disposable development databases and migration histories that have never governed retained external data may be regenerated from the canonical persistence model.

Once a migration has governed retained external data, it is immutable history. Subsequent schema evolution is forward-only and history-preserving according to Delivery Authority.

## Conformance

A conforming implementation uses one canonical vocabulary, explicit module ownership, enforced Tenant isolation, canonical human and machine authority, duplicate-safe commands, immutable evidence, recoverable background work, typed APIs, generated-client parity, non-authoritative clients, governed offline authority, bilingual experience parity, forward-only migration for retained data, layered tests, and verified recovery.

## Related documents

- [System architecture](020-system-architecture.md)
- [Canonical product model](../../70-reference/020-canonical-product-model.md)
- [Tenant Dashboard experience](../../30-experience/080-tenant-dashboard-experience.md)
