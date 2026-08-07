# Audit browsing

Waiotech records material actions as immutable audit evidence and provides
separate Tenant and platform browsing experiences.

## Audit Event

An Audit Event preserves at least:

- stable event identity;
- effective Tenant where applicable;
- real Actor kind and identity;
- Membership or platform authority basis where applicable;
- action code and owning module;
- subject type and identity;
- effective time and recorded time;
- request, operation, correlation, and causation identities;
- reason and Decision references where required;
- outcome and safe failure classification;
- bounded before/after or evidence references required by the action.

Audit is business and governance evidence, not a copy of application logs or raw
HTTP payloads. Audit Events are append-only and correction or explanatory events
never rewrite prior evidence.

## Tenant audit browsing

Authorized Tenant Users may browse only Audit Events belonging to their selected
Tenant. Filters include time range, Actor, action, module, subject, outcome,
correlation, and reason code. Results use stable cursor pagination and preserve a
deterministic order.

Tenant browsing never exposes another Tenant, platform-only security details,
secrets, raw tokens, storage keys, unrestricted personal data, or unredacted
request bodies.

## Platform audit browsing

Authorized platform Users may browse platform-owned Audit Events and narrowly
inspect Tenant audit metadata through dedicated platform contracts. Platform
browsing does not create Tenant Permission or permit ordinary Tenant actions.
Every sensitive audit query is itself audited with purpose and affected Tenant.

## Access and redaction

Audit access is checked for every query and export. Redaction is defined by the
owning event schema and remains stable across UI, API, Reports, and export.
Authorization changes may remove future access without altering historical audit
content.

## Retention and export

Audit retention follows preservation policy and legal obligations. Audit may be
included in Tenant Export through its versioned portability contract. Audit
browsing does not provide arbitrary bulk extraction outside governed Report or
Export actions.

## Related documents
- [Audit events and outbox](../../20-engineering/30-actions-and-contracts/020-audit-events-and-outbox.md)
- [Authorization enforcement](../../20-engineering/40-security/020-authorization-enforcement.md)
- [Tenant portability](../70-tenant-lifecycle/020-tenant-portability.md)
