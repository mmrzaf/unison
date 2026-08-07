# Measurement Points

A Measurement Point is the stable semantic identity of an operational quantity that Waiotech understands can be observed on one explicit plant subject. It separates plant meaning from individual readings, instruments, and external tag names.

## A Measurement Point

A Measurement Point records:

- immutable identity;
- Tenant;
- required name and optional code under the Plant Model code contract;
- measured quantity;
- canonical unit;
- exactly one supported subject;
- optional instrument Asset;
- active or retired state;
- applicable manual-entry behavior;
- governed Measurement Source Mappings where machine submission is supported.

Supported subjects are exactly one of:

- Functional Location;
- Asset;
- Process Unit;
- Process Stream.

Adding another subject category requires explicit Product Authority.

## Keep Measurement Point separate from Reading

Measurement Point answers what can be observed. Process Reading records an observed value.

Replacing an instrument Asset, changing a SCADA tag, or rotating an Integration Principal must not require changing Measurement Point identity when the operational meaning remains the same.

## Use a canonical quantity and unit

Every Measurement Point has one governed measured quantity and one canonical unit.

A Reading submitted in a supported compatible unit may be normalized according to governed unit-conversion rules, but Waiotech must preserve the reported value and unit when they differ from the canonical representation.

An incompatible quantity or unit must be rejected rather than silently coerced.


## Preserve semantic identity after operational use

Measurement Point identity means one quantity on one supported subject in one canonical unit context. After the first accepted Reading or publication of a Maintenance Plan Revision that binds the point, changing the subject or measured quantity requires retirement and a new Measurement Point.

A compatible canonical-unit correction may occur only through an explicit governed change that preserves how earlier reported and normalized values remain interpreted. A change that would alter the physical quantity or reinterpret historical values requires a new Measurement Point.

Instrument Asset and Measurement Source Mappings may change over time without changing Measurement Point identity because they describe how the same semantic quantity is observed. Their effective history preserves prior provenance.

## Manual entry is first-class

A Measurement Point may allow human entry when plant staff legitimately observe or record the quantity manually.

The Process experience must not require an external Data Source for such a Measurement Point.

Where manual entry is prohibited for a specific Measurement Point because only a governed machine source is meaningful, that restriction must be explicit configuration and visible to Users.

## Optional instrument Asset

A Measurement Point may identify the instrument Asset that physically measures the quantity.

The instrument relationship is optional because a Measurement Point may be manually observed, calculated externally, or measured by an instrument not modelled as a Waiotech Asset.

Replacing the instrument Asset does not replace the Measurement Point when the semantic observation remains unchanged.

## The Measurement Point lifecycle

Measurement Point uses:

```text
active → retired
```

Retirement prevents new ordinary Readings while preserving all historical Readings, Process evidence, Maintenance trigger evidence, Reports, and Measurement Source Mappings.

A retired Measurement Source Mapping must not be silently repurposed to represent a different operational quantity.

## Keep Measurement Source Mapping explicit

Machine-submitted Readings require the one active effective Measurement Source Mapping from the Measurement Point to a governed Data Source. Alpha permits at most one active machine-ingress mapping for one Measurement Point at one effective instant.

The mapping preserves the external source key or tag, effective history, source-specific unit or quality semantics where required, and whether machine submission is enabled. It does not replace Measurement Point identity.

A general External Identifier Mapping to a Measurement Point is useful for lookup but does not by itself authorize or define machine Reading ingestion.

## Related documents

- [External identifiers and Data Sources](060-external-identifiers-and-data-sources.md)
- [Process Readings and Observations](../35-process/010-readings-and-observations.md)
- [Procedures and Maintenance Plans](../40-maintenance/040-procedures-and-maintenance-plans.md)
