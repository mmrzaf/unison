# System architecture

Waiotech uses a modular monolith with explicit API, Application, Domain, and Infrastructure layers. Product modules own their facts and actions, layers depend inward, and cross-domain workflows are coordinated through declared contracts rather than private data access.

## Adopt a modular monolith

Waiotech must use one coordinated modular monolith rather than distributed services or an undifferentiated application.

The product contains strongly related Plant, Process, Maintenance, Inventory, Reliability, IAM, policy, evidence, and portability behavior with shared Tenant isolation and frequent cross-domain consistency requirements. One PostgreSQL authority and one transactional backend preserve those guarantees while module boundaries remain explicit.

## Canonical product modules

The backend must make these principal product modules explicit:

```text
plant
process
maintenance
inventory
reliability
iam
policy
attachments
reporting
notifications
portability
```

Tenancy and shared platform concerns remain explicit according to source ownership. The exact directory tree may evolve, but module ownership must remain mechanically visible and product-aligned.

`plant` owns Functional Locations, Assets and installation, Asset Classification, Operational Criticality, Process Units, Process Streams, Measurement Points, Data Sources, Measurement Source Mappings, and plant external mappings.

`process` owns Process Readings, Operational Observations, Process Routines and immutable revisions, Process Rounds, Process Conditions, Operational Actions, Outcome Assessments, and Process material-usage meaning.

`maintenance` owns Work Requests, Findings, Procedures, Maintenance Plans, Scheduled Work, Work Orders, Requirements, execution, completion, and maintenance verification.

`inventory` owns Items, Stock Locations, Movements, Reservations, replenishment, Stocktaking, and custody projections.

`reliability` owns Failure Events, restoration, Cause Assessments, recurrence, and reliability projections.

## Separate runtime responsibilities without distributing ownership

API and Worker run as separate processes. Public Website, Tenant Dashboard, Platform Admin, and Android Work App are separately built application artifacts. Migration and controlled engineering commands use explicit entry points.

Runtime separation must not create alternate product ownership.

## Canonical backend layers

Within each product module, dependencies point inward:

```text
API or Worker entry point
        ↓
Application
        ↓
Domain
        ↓
Ports
        ↑
Infrastructure adapters
```

Infrastructure types must not leak into Domain contracts.

## API layer

API owns HTTP routing, transport authentication integration, request parsing, transport validation, idempotency and concurrency metadata extraction, correlation metadata, Application invocation, response serialization, and safe error mapping.

API does not own product lifecycle or authorization decisions.

## Application layer

Application is the authoritative use-case and cross-domain orchestration boundary.

It owns named commands and queries, Actor and Tenant context, authorization, transaction orchestration, module coordination, Domain invocation, audit coordination, Command Receipts, outbox creation, and typed results.

Cross-domain workflows such as Process Condition → Work Request, Work Order material issue, Failure Event restoration, or Measurement Point-triggered maintenance are coordinated here.

A Domain aggregate must not call another product module's private Domain or repository directly.

## Domain layer

Domain owns product-significant invariants, lifecycle, Decisions, value objects, calculations, evidence meaning, correction semantics, and action guards assigned by Product Authority.

Domain code must remain independent from HTTP, ORM, React, Android, queues, and provider SDKs.

## Ports and Infrastructure

Ports are narrow typed capabilities required by Application or Domain behavior. Infrastructure implements those capabilities using PostgreSQL, Redis, ARQ, file storage, cryptography, external delivery, integration authentication, observability, and framework adapters.

Infrastructure may share technical mechanisms but cannot weaken product semantics.

## Cross-domain contracts

A module may depend on another module only through:

- public Application command;
- public query;
- module-owned event;
- narrow typed read port explicitly preserving owner semantics.

Private repositories, ORM models, internal tables, and infrastructure adapters are not cross-domain contracts.

## Atomic cross-domain actions

Where Product Authority requires one accepted cross-domain action to be atomic, one Application orchestrator may coordinate several module-owned ports through one transaction manager.

Examples include posting Inventory custody while accepting Process or Maintenance material usage, or creating a Maintenance Work Request from a Process Condition with the relationship established atomically.

Shared transaction coordination does not transfer business ownership.

## Keep the module dependency graph acyclic

Circular module dependencies must be decomposed through Application orchestration, events, neutral shared value types, or ownership correction.

Mutual private imports are prohibited.

## Explicit product contracts instead of meta-models

Waiotech must not use universal business engines for Plant Objects, arbitrary relationships, generic lifecycle, generic Conditions, generic Activities, generic Measurement, unrestricted selectors, arbitrary domain JSON, or generic entity CRUD.

Technical helpers may operate on typed product-owned definitions without becoming their authority.

## Human and machine authority

Human Users, Integration Principals, product-defined internal system Actors, and platform Users remain distinct authority paths.

Machine Reading ingestion reaches the same Process Application and Domain Reading behavior after authentication and source authorization; it must not create a parallel machine-data domain.

## Related documents

- [Repository and source ownership](010-repository-and-source-ownership.md)
- [Canonical engineering model](030-canonical-engineering-model.md)
- [Authorization architecture](../40-security/010-authorization-architecture.md)
