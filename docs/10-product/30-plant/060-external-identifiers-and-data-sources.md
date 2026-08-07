# External identifiers and Data Sources

External mappings connect Waiotech's canonical plant identities to source-scoped external identities. Data Sources identify external systems that may provide accepted Process Readings or evidence without becoming Waiotech's canonical Plant Model.

## A Data Source

A Data Source represents one governed external source or source namespace relevant to plant data.

It preserves:

- immutable identity;
- Tenant;
- name;
- source kind or description where needed;
- active or retired state;
- source namespace semantics;
- authorized Integration Principals where machine submission is enabled.

Examples may include one SCADA system, historian namespace, laboratory feed, or another explicit source.

Data Source is not an Integration Principal. The source identifies where data comes from; the Integration Principal identifies the machine Actor authorized to submit it.


## Data Source lifecycle

Data Source uses:

```text
active → retired
```

Retirement prevents new ordinary machine submissions or new active mappings while preserving historical source identity, mappings, credentials history required by security evidence, and accepted Reading provenance.

A source namespace must not be silently redefined after accepted evidence exists. If an external system or namespace changes meaning materially, Waiotech creates a new Data Source identity.

## External identifier mappings

A governed external mapping associates a source-scoped external identifier with one supported canonical Plant Model object.

Supported mapping targets include:

- Functional Location;
- Asset;
- Process Unit;
- Process Stream;
- Measurement Point.

The mapping preserves source, external identifier, canonical target, effective start, retirement time where applicable, creating or correcting Actor, and reason where required. Mapping changes are effective-dated; they do not rewrite what an external identifier resolved to in retained historical evidence.

Within one Tenant and source namespace, one retained external identifier must not ambiguously identify several canonical targets at the same effective time.

## Never replace canonical identity with external identity

External identifiers:

- do not become canonical Waiotech identity;
- must not silently reassign to another retained object;
- may be retired or corrected through explicit history-preserving actions;
- may resolve lookup and integration input to canonical identity where Product Authority permits it.

Published Maintenance Plan coverage and accepted operational records preserve canonical Waiotech target identity even when an external identifier was used for lookup.

## Measurement Source Mapping

A Measurement Source Mapping is the governed relationship that allows one Data Source signal or key to provide machine Readings for one Measurement Point.

It preserves:

- Measurement Point;
- Data Source;
- external source key or tag;
- effective start and retirement;
- machine-ingress enablement;
- source-specific unit, quality, or sequence interpretation required by the accepted contract;
- creating or correcting Actor and reason where required.

A machine Reading submission must resolve through one active Measurement Source Mapping and an authorized Integration Principal.

A Measurement Point may preserve several historical source mappings, but Alpha permits at most one active machine-ingress Measurement Source Mapping at one effective instant. Changing machine source ends the prior effective mapping and establishes the new mapping without rewriting earlier Reading provenance.

Human Reading entry remains independent of the active machine source where the Measurement Point permits manual entry. Waiotech must not choose among competing active machine sources by arrival order.

The source key may change while Measurement Point identity remains stable. Source changes preserve historical mappings instead of rewriting prior Reading provenance.

Measurement Source Mapping is distinct from a general External Identifier Mapping. A lookup alias does not automatically grant machine-ingress semantics.

## Preserve external evidence without becoming the external system

Waiotech may retain selected accepted values, source identifiers, snapshots, and source links required to explain operational history.

It does not claim authority over the complete source-system history merely because a mapping exists.

## Related documents

- [Measurement Points](050-measurement-points.md)
- [Integration Principals](../20-identity-and-access/040-integration-principals.md)
- [Product boundaries](../10-foundations/030-product-boundaries.md)
