# Product boundaries

Waiotech is authoritative for the connected operational meaning, accountable decisions, work, material custody, selected process evidence, failure history, and outcomes required to operate and maintain one wastewater plant. Specialist external systems retain their specialist authority.

## Waiotech is authoritative for

Waiotech owns the authoritative records required by its product domains, including:

- the canonical Plant Model used by Waiotech;
- human and machine Actors within Waiotech authority;
- Process Readings accepted into Waiotech, Operational Observations, Process Conditions, Operational Actions, and outcomes;
- Work Requests, Findings, Procedures, Maintenance Plans, Scheduled Work, Work Orders, Requirements, execution evidence, completion, and maintenance verification;
- Inventory Items, Stock Locations, Movements, Reservations, replenishment, Stocktaking, and material-use meaning where defined by the consuming domain;
- Failure Events, restoration history, Cause Assessments, recurrence relationships, and reliability interpretation;
- Attachments, Reports, Notifications, audit access, policy Decisions, and Tenant portability required by Product Authority.

## Preserve external authority instead of copying it

Waiotech may preserve source references, accepted values, snapshots, files, identifiers, and evidence needed to explain a Waiotech decision or history. Preservation does not transfer authority over the complete external source system.

Waiotech must make source, provenance, effective time, and correction meaning explicit whenever external evidence can materially affect interpretation.

## Process control and continuous telemetry

Waiotech is not SCADA and must not control the plant.

SCADA, PLC, DCS, or equivalent control systems remain authoritative for control commands, live control state, control logic, alarm configuration, and the operational guarantees required for process control.

Waiotech is not a general historian. A historian or source process system may remain authoritative for high-frequency continuous telemetry and long-retention raw time series.

Waiotech may accept selected Process Readings for configured Measurement Points when those readings are useful for plant operation, maintenance, reliability, evidence, or governed trends. Human entry is a normal first-class Process workflow. Machine ingestion supplements human operation and must use governed Data Sources and Integration Principals.

Waiotech must not require a plant to integrate SCADA or a historian before Process Operations is usable.

## Laboratory systems

Waiotech may preserve or reference laboratory results needed as operational evidence, but it is not the authoritative laboratory information management system unless separate Product Authority defines that responsibility.

A laboratory result used in Waiotech must preserve its source and must not be silently reclassified as a Waiotech Process Reading unless the Measurement Point and source contract explicitly support that meaning.

## Engineering drawings, GIS, and digital-twin visuals

Waiotech may display plant diagrams, simple P&ID-oriented views, maps, or imported references when they improve navigation and operational understanding.

Waiotech is not an engineering CAD or P&ID authoring system, a full GIS authority, a 3D modelling platform, or a process simulator.

Visuals are views over or references into authoritative product data. They must not create an alternate plant model.

## Safety and qualification

Waiotech may record governed safety requirements, qualifications, permits, attestations, and evidence defined by Product Authority. It does not replace statutory safety management, legal certification, or regulatory authority that belongs outside Waiotech.

The interface must never imply that a software action itself makes unsafe work safe.

## Procurement and financial systems

Inventory owns physical custody and availability inside Waiotech. Procurement, purchasing, invoicing, accounting, payroll, and general-ledger authority remain outside Waiotech unless separate Product Authority explicitly adopts them.

Waiotech may create replenishment demand and preserve procurement references without becoming the commercial system of record.

## External systems and integrations

External integration is explicit and bounded.

An Integration Principal is a machine Actor owned by one Tenant. It receives only explicitly granted machine authority. In Alpha, the principal external-write use case is submitting Process Readings for configured Measurement Points through governed Data Sources.

Integration Principal authority must not imply ordinary human authority, arbitrary Maintenance actions, Inventory posting, or platform administration.

## Platform operation

Platform Admin owns platform provisioning, Tenant lifecycle control, support and recovery functions explicitly defined by Product Authority. It is not an alternate path for ordinary plant work and cannot impersonate Tenant Users.

## Android boundary

Android is a governed plant-work surface, not an alternate database.

It supports defined Process and Maintenance field workflows, evidence capture, bounded Inventory interactions, and offline authority where Product Authority and Experience Contracts explicitly permit them. Configuration, publication, broad administration, and unrestricted machine ingestion remain outside Android.

## Related documents

- [Alpha Product Edition](040-alpha-product-edition.md)
- [Responsibility and actors](../20-identity-and-access/030-responsibility-and-actors.md)
- [Integration Principals](../20-identity-and-access/040-integration-principals.md)
- [Measurement Points](../30-plant/050-measurement-points.md)
- [Process Readings and Observations](../35-process/010-readings-and-observations.md)
