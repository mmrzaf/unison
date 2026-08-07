# Asset classification and Operational Criticality

Asset Classification describes what a physical Asset is. Operational Criticality describes the consequence of losing the stable function represented by a Functional Location. The concepts must not be conflated.

## Asset Classification

Waiotech must use governed Asset Classification to describe technical Asset meaning without creating mandatory structural Asset types.

Classification may represent equipment family, subtype, manufacturer-oriented technical category, or other governed taxonomy required by Maintenance, Inventory compatibility, Reporting, or Reliability.

Classification facts must be relational and historically interpretable. Free-text labels must not silently become authority for behavior that depends on classification.

Asset Classification does not determine physical parentage or Process Unit membership.

## Keep classification governance explicit

Where a classification uses governed dimensions, allowed values, hierarchy, applicability, retirement, and required assignments must be defined by their catalogue owner.

Published Maintenance Plans or historical Failure Events must preserve canonical Asset identity and required snapshots rather than depending on later reinterpretation of mutable classification labels.

## Operational Criticality belongs to the stable function

Operational Criticality represents the potential plant consequence of losing the function associated with a Functional Location.

Criticality belongs to Functional Location rather than the replaceable physical Asset because the operational consequence normally survives equipment replacement at the same stable position.

An installed Asset may display the Operational Criticality of its effective Functional Location as context. That context must not be rewritten as intrinsic Asset identity.

## Keep criticality separate from current consequence and work risk

Operational Criticality is a relatively stable assessment of potential consequence.

It is distinct from:

- actual consequence recorded by a Failure Event;
- current Process attention;
- Work Order urgency;
- Work Order priority;
- execution risk;
- deferral consequence.

Criticality may influence planning, prioritization, investigation requirements, readiness, escalation, and analysis only where the owning rule explicitly uses it.

## Preserve criticality history where it affects decisions

When a criticality value materially affects a governed decision, Waiotech must preserve the effective assessment or equivalent reproducible evidence used at that time.

Changing current criticality must not rewrite the meaning of earlier decisions.

## Related documents

- [Functional Locations](010-functional-locations.md)
- [Assets and installation](020-assets-and-installation.md)
- [Maintenance classification and control](../40-maintenance/010-maintenance-classification-and-control.md)
- [Failure Events](../55-reliability/010-failure-events.md)
