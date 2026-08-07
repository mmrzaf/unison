# Browser application contracts

Tenant Dashboard and Platform Admin are non-authoritative browser applications built from generated API clients, purpose-specific read models, explicit action handlers, stable navigation, and shared interaction infrastructure.

## Generated clients are mandatory

Browser application code must use generated canonical clients for Server contracts. Hand-written undocumented HTTP requests are prohibited.

Generated wire types are adapted into presentation models only where the adapter adds user-facing meaning without changing Product Authority.

## Feature organization

Tenant Dashboard feature ownership should align with the canonical experience:

```text
features/
├── home/
├── my-work/
├── plant/
│   ├── functional-locations/
│   ├── assets/
│   ├── process-model/
│   └── measurement-points/
├── process/
├── maintenance/
├── inventory/
├── reliability/
├── reports/
└── settings/
```

Exact source folders may differ, but browser architecture must not reproduce obsolete Product Authority or hide Process and Reliability inside Maintenance.

## Purpose-specific read models

The Server should expose read models suited to user tasks rather than forcing the browser to assemble operational meaning from dozens of low-level entity queries.

Examples include:

- Plant subject summary with connected context;
- Process Condition workspace row and detail;
- Work Order readiness and blocker summary;
- Inventory availability summary;
- Failure Event operational story;
- My Work queue item;
- Home attention projection.

Read models remain projections. Mutation always invokes owning domain actions.

## Action registry

Every Server-provided available action shown by the browser must map to a registered typed interaction.

Unknown action descriptors are contract defects and generate diagnostics rather than being silently hidden.

The action handler owns interaction shape such as dialog, full page, confirmation, form section, idempotency behavior, and post-success refresh. It does not re-evaluate Server authority locally.

## Contextual creation

Browser routing and creation flows should preserve known canonical context.

Examples:

- Process Condition → Work Request carries Condition and relevant Plant references;
- Asset → Finding carries Asset identity;
- Process Unit → Reading selects compatible Measurement Points;
- Work Order → material Reservation carries Work Order and requirement context.

The browser must not ask users to re-enter canonical relationships already known from the initiating context.

## Workspace state

Search, filter, ordering, page size, queue mode, and selected record should be URL-backed where this improves repeated operational use and browser back/forward behavior.

Tenant identity and Permission are never encoded as trusted URL authority.

## Process Reading entry

Human Reading entry must optimize for minimal input.

When Measurement Point context is known, the browser uses generated metadata to show quantity and canonical unit, default effective time safely, and submit only legitimate user input. It never invents unit conversion or source provenance.

Machine Reading ingestion is not exposed through ordinary Dashboard data-entry forms. Data Source and Integration Principal configuration belongs in Settings.

## Operational timelines

The browser may render cross-domain chronological projections but every entry retains source record type, canonical identity, time semantics, Actor, and authorized navigation to source detail.

Timeline composition must not create a generic Event mutation model.

## Home and My Work

Home and My Work use Server-owned projections for attention, executable actions, blockers, due state, and responsibility.

The browser may filter and order within explicit contract parameters but must not infer hidden business states from raw records.

## Errors and concurrency

Browser error handling uses stable error codes and typed blockers. Concurrency conflict causes authoritative refetch and an understandable user recovery path.

Uncertain retries reuse the same idempotency identity when the command contract requires it.

## Platform Admin separation

Platform Admin must use explicit platform contracts and components appropriate to platform operation. Sharing technical UI primitives with Tenant Dashboard is permitted; sharing plant authority or hidden Tenant mutation paths is prohibited.

## Related documents

- [API contracts](../30-actions-and-contracts/030-api-contracts.md)
- [OpenAPI and generated clients](../30-actions-and-contracts/050-openapi-and-generated-clients.md)
- [Tenant Dashboard experience](../../30-experience/080-tenant-dashboard-experience.md)
