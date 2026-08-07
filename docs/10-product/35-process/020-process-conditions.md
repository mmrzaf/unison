# Process Conditions

A Process Condition is the authoritative Process record of a meaningful operational situation that requires active intervention, continued monitoring, or explicit resolution. It is an interpretation of plant evidence, not a copy of every alarm or out-of-range Reading.

## A Process Condition

A Process Condition has one primary operational subject:

- Process Unit; or
- Process Stream.

It may reference supporting:

- Readings;
- Operational Observations;
- Measurement Points;
- Functional Locations;
- Assets;
- attachments and external evidence;
- related Process Conditions;
- Maintenance and Reliability records according to their owning contracts.

It preserves a concise operational description, effective onset or first-known time where available, creation time, Process attention, accountable owner Team, optional responsible User, current handling state, and resolution evidence.

## Create a Condition only when operational meaning exists

A Process Condition should be established when plant staff determine that a situation requires accountable operational handling or monitoring.

A threshold breach, alarm, unusual Reading, or isolated Observation does not automatically become a Process Condition.

Waiotech Alpha does not use a generic alarm-to-condition or anomaly engine as Product Authority. A human may establish a Condition from machine or human evidence.

## Process attention

Every active or monitoring Process Condition has one current attention level:

- `routine`;
- `elevated`;
- `urgent`;
- `critical`.

Attention describes the required operational response intensity and timeliness for this Condition. It does not assert Failure Event consequence, Maintenance urgency, Asset condition, or regulatory severity.

Changing attention is an explicit attributable action that preserves previous value, new value, Actor, time, and reason. Lowering attention requires the same accountability as increasing it.

A critical Process Condition requires immediate visible attention and accountable coordination, but it does not automatically create a Failure Event or Maintenance Work Order; those domains apply their own acceptance criteria.

## Preserve uncertainty instead of forcing diagnosis

A Condition may begin before cause is known.

The Condition description must state the operational situation without requiring premature diagnosis. Cause or explanation may develop through later Observations, Operational Actions, Maintenance findings, or Failure Event investigation.

## The Process Condition lifecycle

Process Condition uses:

```text
active → monitoring → resolved
active → resolved
monitoring → active
resolved → active
```

- **Active:** immediate operational intervention, decision, or coordination remains required.
- **Monitoring:** no immediate intervention is currently required, but continued observation and an explicit review obligation remain.
- **Resolved:** no active Process obligation remains for the recorded condition.

Reopening a resolved Condition preserves the earlier resolution and records new evidence and reason.

## Require accountable responsibility

An active or monitoring Process Condition must have one owner Team. It may also identify one responsible User.

Changing ownership is an explicit attributable action and preserves history.

Monitoring must include an explicit next review time or another Product Authority-defined review condition. Monitoring must not become indefinite silent deferral.

## Resolution

Resolving a Process Condition requires:

- resolution time;
- resolving Actor;
- resolution summary;
- evidence sufficient to explain why the active Process obligation ended;
- disposition of required follow-up.

Resolution may be supported by new Readings, Observations, Operational Actions, Maintenance results, or another governed operational outcome.

A Maintenance Work Order being completed does not automatically resolve a Process Condition. Operations must establish the operational outcome when Process recovery matters.

## Duplicate and related conditions

Waiotech should avoid several open Process Conditions representing the same operational situation.

When duplicate reporting occurs, one Condition remains primary and the duplicate evidence is linked or corrected without inflating operational queues. Distinct simultaneous conditions remain separate even when they share the same Process Unit.

## Related documents

- [Process Readings and Operational Observations](010-readings-and-observations.md)
- [Operational Actions, outcomes, and responsibility](030-actions-outcomes-and-responsibility.md)
- [Cross-domain Process interactions](040-cross-domain-interactions.md)
