# Operational Actions, outcomes, and responsibility

Operational Actions record what Operations deliberately did in response to plant context or a Process Condition. Outcome Assessments record what happened afterward. Both preserve chronological meaning without turning Process into Maintenance work or a generic workflow engine.

## An Operational Action

An Operational Action is immutable evidence of an intentional operations action.

It has exactly one primary Process Unit or Process Stream context and may belong to one Process Condition when the action is part of handling that Condition. Routine intentional operating changes may be recorded without creating a Condition when no accountable abnormal-condition obligation exists.

It preserves:

- Actor;
- effective time;
- recorded time;
- concise description of what was changed or done;
- primary Process Unit or Process Stream;
- relevant Assets, Functional Locations, Measurement Points, or settings where useful;
- reason or objective;
- attachments or supporting evidence.

Examples include changing equipment duty, redistributing flow, changing a process setting, adjusting a dose, starting parallel equipment, changing temporary configuration, or increasing monitoring.

Operational Action does not become a Work Order merely because equipment is involved. Physical inspection, repair, replacement, and maintainable work belong to Maintenance.

## An Outcome Assessment

An Outcome Assessment is immutable evidence of the observed result attributable to one Operational Action.

Canonical outcome values are:

- `improved`;
- `no_meaningful_change`;
- `worsened`;
- `inconclusive`.

The assessment preserves the assessed Operational Action, Actor, time, rationale, and supporting Readings or Observations. An Operational Action may have several chronological Outcome Assessments when its effect develops over time.

An outcome is not the same as Process Condition resolution. A Condition may improve but remain active or monitoring.

## Preserve chronology rather than editing the story

Operational Actions and Outcome Assessments are append-only evidence. A later action or assessment supplements prior history.

Corrections identify and explain earlier incorrect evidence instead of silently rewriting it.

## Keep operational responsibility explicit

The Process Condition owner Team remains responsible for the active operational obligation until explicit reassignment or resolution.

Recording an Operational Action does not automatically transfer responsibility to the person who performed it.

## Treat handover as continuity over authoritative records

Waiotech does not use a separate generic Handover aggregate in Alpha.

Shift and team handover experiences are projections over:

- open Process Conditions;
- owner Team and responsible User;
- current handling state;
- latest Observations and Readings;
- Operational Actions already taken;
- next review obligations;
- related Maintenance or Reliability work.

Handover must not duplicate or detach these facts into an alternate log.

## Related documents

- [Process Conditions](020-process-conditions.md)
- [Tenant Dashboard experience](../../30-experience/080-tenant-dashboard-experience.md)
