# Preservation and destruction

Operational Tenant records remain preserved after deactivation unless a complete governed destruction contract explicitly authorizes physical destruction. Learning-purpose Tenant reset follows its separate synthetic-data contract.

## Physical destruction is outside this Product Authority and requires a complete retention and destruction contract

Physical destruction is outside this Product Authority and requires a complete retention and destruction contract.

Physical Tenant destruction is outside Waiotech Product Authority.

A correct destruction capability requires a dedicated retention and destruction model covering:

- legal and contractual holds;
- evidence classes;
- privacy requirements;
- financial and safety records;
- backups and replicas;
- external-system references;
- partial failure;
- authorization;
- irreversible proof.

Deactivation alone never destroys data.

## Keep learning reset as a narrow synthetic-data exception to operational preservation

Waiotech must keep learning reset as a narrow synthetic-data exception to operational preservation.

A learning-purpose Tenant contains explicitly synthetic disposable state under the learning-environment contract. Its deterministic reset contract may replace or remove that synthetic state.

The exception does not apply to operational Tenants, imported operational evidence, onboarding progress, or any record whose source is not safely synthetic.

## Preserve governed Tenant evidence by default

Waiotech must preserve governed Tenant evidence by default. A retention or destruction amendment must define every exception.

Retention cannot be safely represented through one configurable duration because different records and evidence classes may have different obligations and restrictions.

## Preserve deactivated Tenant history by default

Waiotech must preserve deactivated Tenant history by default.

Tenant-owned records, histories, Decisions, and Attachments remain preserved after deactivation according to mandatory product-integrity rules.

Ordinary Users cannot physically destroy governed evidence.

The Tenant remains unavailable for normal operation while its historical records remain interpretable.

## The minimum Tenant offboarding and portability model

Waiotech must keep Tenant lifecycle, export, and protected import explicit. Tenant cloning, configurable retention, and physical destruction are outside this Product Authority.

The minimum model is:

```text
Tenant
├── active
├── suspended
└── deactivated

Tenant Export
├── product-defined export type
├── versioned export contract
├── execution lifecycle
├── declared cutoff
├── frozen artifact
├── integrity evidence
└── effective-access retrieval

Tenant Import
├── versioned package validation
├── integrity and safety checks
├── new inactive Tenant
├── identity reconciliation
├── staged evidence restoration
└── atomic activation
```

Capabilities outside Waiotech Product Authority are:

```text
Tenant Cloning
Tenant Migration
Configurable Retention
Physical Destruction
```

The governing distinctions are:

- Suspension temporarily blocks ordinary operation.
- Deactivation permanently ends normal operation.
- Neither state deletes or rewrites data.
- Export provides governed portability of Tenant-owned facts and evidence.
- User identity remains platform-wide.
- Credentials and security secrets are excluded.
- Projections are rebuildable and non-authoritative.
- Tenant Import restores a valid Waiotech package only into a new inactive Tenant and activates it atomically after complete validation.
- Physical destruction requires a separate Product Authority amendment.

## Related documents
- [Tenant lifecycle](010-tenant-lifecycle.md)
- [Tenant portability](020-tenant-portability.md)
- [Learning environment](../../30-experience/070-learning-environment.md)
