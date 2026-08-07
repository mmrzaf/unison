# Tenant Dashboard and Platform Admin

Tenant Dashboard is the professional operating workspace of one Tenant plant. Platform Admin is a separate platform-control surface and must not become an alternate plant-operations interface.

## Tenant Dashboard

Tenant Dashboard serves plant managers, operators, engineers, maintenance planners, supervisors, technicians using browser workflows, warehouse personnel, reliability users, and Tenant administrators according to Permission.

Its primary navigation is:

```text
Home
My Work
Plant
Process
Maintenance
Inventory
Reliability
Reports
Settings
```

Permission restrictions remove inaccessible destinations without changing the conceptual information architecture for authorized users.

### Home

Home provides a concise operational orientation. It prioritizes what needs attention, why it matters, current blockers, responsibilities, overdue obligations, important Process Conditions, maintenance readiness, Inventory constraints, failures, and significant recent activity.

Home is not a configurable wall of charts.

### My Work

My Work is the cross-domain personal operational queue. It may include Process Conditions, Maintenance actions, Inventory obligations, Reliability investigation or review, and other records where the current principal has explicit responsibility or an executable decision-bearing action.

My Work preserves record identity and owning domain. It does not merge different domain records into a generic Task aggregate.

### Plant

Plant is the shared navigation and understanding surface for:

- Functional Locations;
- Assets and installation;
- Process Units and Streams;
- Measurement Points;
- connected operational history and relationships.

Plant should feel like navigating the plant, not administering database tables.

### Process

Process is the operational workspace for:

- human Process Reading entry;
- Operational Observations;
- open Process Conditions;
- Operational Actions and outcomes;
- focused Measurement Point trends;
- process-to-Maintenance coordination;
- operational continuity and handover views.

Machine-submitted Readings enrich the same Process experience rather than creating a separate machine-data product.

### Maintenance

Maintenance contains Work Requests, Findings, Work Orders, planning, readiness, Procedures, Maintenance Plans, Scheduled Work, and maintenance execution and verification experiences.

Planning is a Maintenance workspace rather than a separate top-level product domain.

### Inventory

Inventory contains stock, custody, Reservations, Movements, replenishment, Stocktaking, and material-availability experiences.

### Reliability

Reliability contains Failure Events, restoration, investigation, Cause Assessments, recurrence, and derived reliability learning.

### Reports

Reports provide governed reproducible views and artifacts. Reports do not become a generic analytics builder or replace operational workspaces.

### Settings

Settings contains Tenant-owned configuration such as Users and Teams, Access Profiles, plant classifications and configuration, Data Sources, Integration Principals, Process configuration, Maintenance configuration, Inventory configuration, and Tenant policy according to Permission.

Configuration must remain visually and conceptually distinct from operational work.

## Platform Admin

Platform Admin is a separate application for platform-owned functions such as:

- Tenant provisioning and lifecycle control;
- platform User and support administration explicitly defined by Product Authority;
- preservation, recovery, import, export, and platform readiness actions;
- operational support evidence and diagnostics where authorized.

Platform Admin must not expose ordinary Plant, Process, Maintenance, Inventory, or Reliability mutation merely because platform operators can technically access the infrastructure.

A platform User cannot impersonate a Tenant User.

## Keep cross-domain context connected

The Dashboard should carry known context forward between domains.

Examples include:

- Process Condition → request Maintenance;
- Functional Location → view installed Assets, open work, Process context, and failures;
- Asset → view installation, Measurement Points, work, failures, and material history;
- Failure Event → open related Process evidence and Maintenance response;
- Work Order → issue or return Inventory;
- Inventory Item → inspect usage by Maintenance or Process where authorized.

A related workflow should not ask the user to reselect plant context that Waiotech already knows.

## Related documents

- [Tenant Dashboard experience](080-tenant-dashboard-experience.md)
- [Dashboard design conventions](090-dashboard-design-conventions.md)
- [Browser application contracts](../20-engineering/60-applications/060-browser-application-contracts.md)
