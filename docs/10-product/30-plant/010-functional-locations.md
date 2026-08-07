# Functional Locations

A Functional Location is a stable installed position, structure, or maintainable place in the plant whose identity may continue while physical Assets are installed, removed, or replaced.

## A Functional Location

A Functional Location represents where installed plant responsibility exists.

Examples include:

- a building, room, gallery, basin, or fixed structure that needs independent history;
- a pump position such as `P-101`;
- an MCC position;
- an equipment bay;
- a maintainable fixed plant place.

A Functional Location is not a physical Asset and is not a Process Unit. It may correspond closely to a physical or process concept in ordinary plant language, but Waiotech keeps the identities separate because their histories and relationships differ.

## Use one recursive hierarchy without fixed levels

A Functional Location may have one parent Functional Location in the same Tenant. The hierarchy has no fixed level taxonomy and must not contain cycles.

The hierarchy expresses stable installed containment only. It must not be used to express Asset assembly, Process Unit containment, process flow, Team authority, or Inventory custody.

A Tenant may have several root Functional Locations.

## Keep Functional Location identity stable

A Functional Location has immutable identity, required name, and optional human-facing plant code according to the Plant Model code contract.

Its identity survives ordinary changes to installed physical Assets. A pump replacement at `P-101` must not require replacing the Functional Location identity `P-101`.

Name, code, and hierarchy corrections are governed changes. A parent change must preserve effective hierarchy history when earlier Process, Maintenance, Inventory-reference, or Reliability evidence depends on the previous containment. A change that represents a genuinely different stable plant position requires retirement and a new Functional Location rather than identity reuse.

## The Functional Location lifecycle

Functional Location uses:

```text
active → retired
```

Active means the stable plant position or place remains available for ordinary current use.

Retired prevents new normal targeting and installation while preserving all history. Retirement must evaluate active installed Assets, open work, active Maintenance Plan coverage, Measurement Points, unresolved Failure Events, and other required relationships. Owning domains determine how those obligations are resolved or reassigned.

Retirement does not delete or rewrite historical work, failures, readings, installations, or evidence.

## Functional Locations as Work Targets

A Maintenance Work Order may use one Functional Location as its primary Work Target when the work concerns the stable position or place rather than one specific physical Asset.

Examples include:

- building or area repair;
- cleaning or fixed infrastructure work;
- a pump position whose installed failed object is not yet known;
- inspection of the installed function at a stable position.

Before release or emergency start, a Work Order must have a governed primary Work Target according to Maintenance Product Authority.

## Keep Stock Location separate

A Stock Location is an Inventory custody node and remains distinct from Functional Location.

A Stock Location may reference a Functional Location when its physical placement is useful, but Inventory balances and Movements must always use Stock Location identity.

Logical and mobile custody such as quarantine, goods in transit, or technician custody may have no Functional Location.

## Keep authority Tenant-scoped

Functional Location hierarchy does not create security or policy inheritance.

Teams, Permissions, policy, and responsibility remain governed by their owning domains.

## Related documents

- [Assets and installation](020-assets-and-installation.md)
- [Asset classification and Operational Criticality](030-asset-classification-and-criticality.md)
- [Work Order lifecycle and Readiness](../40-maintenance/060-work-order-lifecycle-and-readiness.md)
