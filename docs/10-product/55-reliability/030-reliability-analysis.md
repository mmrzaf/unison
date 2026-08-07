# Reliability analysis

Reliability analysis derives learning from authoritative Plant Model, Process, Maintenance, Inventory, and Failure Event evidence. Derived measures never replace or rewrite the source facts from which they are calculated.

## Derive reliability rather than maintaining duplicate truth

Waiotech may derive measures such as:

- failure recurrence;
- mean time between failures where the data semantics support it;
- mean time to restoration;
- downtime;
- repeat failure rate;
- dominant failure modes;
- cause patterns by Asset Classification;
- temporary-restoration frequency;
- failure consequence trends;
- Process Conditions or Reading patterns associated with failures;
- maintenance response and verification patterns;
- restoration delay associated with unavailable material;
- failure concentration by Functional Location or Asset.

Derived analytics must identify their time window, population, source rules, and evaluation time sufficiently to support correct interpretation.

## Connect operating condition to failure and recovery

Reliability views may relate Process evidence before, during, and after a Failure Event.

Examples include:

- selected Readings before failure;
- open Process Conditions at detection;
- operating consequence;
- maintenance response;
- Readings after return to service;
- whether Process recovery followed technical repair.

The full historian need not be copied into Waiotech for these relationships to be meaningful.

## Preserve the stable-position and physical-Asset distinction

Analysis must distinguish:

- repeated failure of the same Functional Location across several installed Assets;
- repeated failure of one physical Asset across several installations;
- failure of a child Asset within a larger assembly;
- repeated failure affecting one Process Unit through different physical causes.

This distinction is a principal reason Functional Location and Asset identities are separate.

## Do not present weak data as precise reliability truth

A metric must not imply confidence that the underlying evidence does not support.

Missing failure occurrence time, unknown restoration, uncertain Reading quality, incomplete installation history, or insufficient observation windows must remain visible in interpretation where they materially affect a result.

## Related documents

- [Failure Events](010-failure-events.md)
- [Assets and installation](../30-plant/020-assets-and-installation.md)
- [Process Readings and Operational Observations](../35-process/010-readings-and-observations.md)
