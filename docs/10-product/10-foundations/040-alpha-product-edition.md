# Alpha Product Edition

Alpha is a complete coherent Waiotech product for one wastewater plant. Every required capability must preserve the same Product Authority, security model, plant identity, evidence semantics, and operational history across its supported application surfaces.

## Application surfaces

### Server and workers

Server owns canonical product authority, commands, queries, transactions, background obligations, generated API contracts, and integration ingress.

### Tenant Dashboard

Tenant Dashboard is the primary professional workspace for plant configuration, Process Operations, Maintenance planning and control, Inventory, Reliability, Reports, and Tenant administration.

### Platform Admin

Platform Admin is limited to platform-owned provisioning, Tenant lifecycle, support, preservation, recovery, and other explicitly platform-owned actions. It is not a plant-operations interface.

### Android Work App

Android supports governed field execution for Maintenance and defined Process field workflows, including human Readings, Operational Observations, Process Condition interaction where authorized, evidence capture, and bounded Inventory use. Offline operation exists only through explicitly issued authority.

### Public Website

Public Website explains Waiotech truthfully and remains outside authenticated plant authority.

## Required Alpha capabilities

Alpha must include the following coherent capabilities.

### Plant Model

- Functional Location hierarchy and lifecycle;
- physical Asset identity, classification, lifecycle, composition, installation, replacement, and history;
- Operational Criticality for stable Functional Locations;
- Process Unit hierarchy;
- Process Streams;
- explicit Functional-Location-to-Process-Unit service relationships;
- Measurement Points with quantity, canonical unit, supported subject, optional instrument Asset, and lifecycle;
- Data Sources, effective Measurement Source Mappings with at most one active machine source per Measurement Point, and governed external mappings;
- connected Plant Explorer with Functional Location hierarchy, Process Unit hierarchy, Process Stream flow, installation context, Measurement Points, and cross-domain navigation.

### Process Operations

- human-entered Process Readings as a primary workflow;
- governed machine Process Readings for configured Measurement Points;
- Reading provenance, quality, unit, time, correction, and source evidence;
- Operational Observations;
- Process Routines with immutable published revisions, scheduled or ad-hoc Process Rounds, and low-friction human Reading/Observation collection;
- Process Conditions with explicit attention, accountable responsibility, and lifecycle;
- Operational Actions and outcome assessment;
- Process evidence, attachments, and operational history;
- focused trends and process context based on configured Measurement Points;
- Process-to-Maintenance escalation and linked maintenance response;
- Process evidence and consequence relationships to Reliability;
- Process Material Usage with explicit Inventory custody impact when tracked operational material is consumed.

### Maintenance

- Work Requests and triage;
- accepted maintenance need represented by Work Orders;
- Findings;
- Procedures and immutable published Procedure Revisions;
- Maintenance Plans and immutable published Plan Revisions;
- calendar and Measurement Point reading triggers;
- Scheduled Work;
- Work Order planning, Requirements, Readiness, assignment, release, execution, completion, verification, closeout, cancellation, correction, and emergency-start behavior;
- Work Targets using Functional Locations or Assets;
- maintenance measurements and evidence whose meaning belongs to execution context;
- material requirements, Reservations, issue, return, and usage reconciliation;
- explicit relationships to Process Conditions and Failure Events.

### Inventory

- Items and Item Categories;
- Stock Locations and balances;
- Movements, receiving, issue, return, transfer, adjustment, and disposal where defined;
- Reservations and availability;
- replenishment;
- Stocktaking;
- Maintenance material demand and usage;
- defined Process material usage;
- full quantity, unit, source, reason, custody, and audit semantics.

### Reliability

- Failure Events against Functional Locations or Assets;
- operational failure and temporary-restoration state;
- authoritative failure, detection, restoration, and resolution timing;
- consequence;
- proportional investigation;
- immutable Cause Assessments;
- recurrence and duplicate relationships;
- links to Process Conditions, Readings, Findings, Work Requests, and Work Orders;
- derived reliability analytics from preserved source facts.

### Identity, policy, evidence, and communications

- Users, Memberships, Teams, Permissions, Access Profiles, Access Grants, and Team Access Profile Assignments;
- Integration Principals with narrow machine authority;
- Tenant Control Policy and governed Decisions where defined;
- Attachments and evidence;
- Reports;
- Notifications;
- audit browsing;
- Tenant export, import, preservation, destruction, and recovery obligations defined by Product Authority;
- English and Persian experience parity.

## Product exclusions

Alpha does not include:

- Site or multi-plant hierarchy inside one Tenant;
- SCADA or PLC control;
- a general-purpose historian or unrestricted raw telemetry archive;
- generic alarm-management replacement;
- generic dashboard or workflow builders;
- 3D digital-twin authoring;
- engineering CAD or P&ID authoring;
- full GIS authority;
- process simulation;
- unrestricted formula, rules, or anomaly-detection engines;
- full procurement, accounting, payroll, or ERP replacement;
- arbitrary machine authority;
- universal Plant Object, Event, Measurement, Condition, or Relationship engines.

These exclusions define product boundaries; they do not permit incomplete implementation of required Alpha capabilities.

## Release completeness

An Alpha capability is complete only when its authoritative meaning, Server command and query behavior, authorization, audit, correction, evidence, generated contract, required Dashboard or Android experience, localization, loading and failure states, tests, export behavior, and operational recovery are mutually consistent.

A technical CRUD screen or partial endpoint does not satisfy a required capability.

## Related documents

- [Domain and plant model](020-domain-and-plant-model.md)
- [Product boundaries](030-product-boundaries.md)
- [Tenant Dashboard experience](../../30-experience/080-tenant-dashboard-experience.md)
