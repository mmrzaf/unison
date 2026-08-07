# Failure Events

A Failure Event is the authoritative Reliability record of an actual loss or materially unacceptable degradation of required plant function. It records what failed and its operational consequence; Maintenance records the accepted physical work performed in response.

## A Failure Event

A Failure Event has exactly one primary failure target:

- Functional Location; or
- Asset.

Use Functional Location when the stable installed function failed and the responsible physical object is unknown, secondary, or replaceable without changing the failed operational position.

Use Asset when one identified physical Asset itself lost or materially degraded its required function and that physical identity matters to reliability history.

The event preserves:

- failed or degraded required function;
- occurrence or effective failure time where known;
- detection time where different;
- failure mode where known;
- operational consequence;
- operational condition;
- investigation condition;
- restoration and terminal-resolution evidence;
- related Process evidence;
- supporting Findings;
- linked Work Requests and Work Orders;
- Cause Assessments;
- recurrence and duplicate relationships;
- required follow-up.

## Reserve Failure Event for actual functional failure

A Failure Event should be created when required function is lost or materially unacceptable, including when:

- the stable installed function cannot be provided;
- an Asset cannot perform its required function;
- performance is outside an accepted functional limit to a materially unacceptable degree;
- the failure causes a material operational interruption or consequence.

A Failure Event should not be created merely because there is:

- a defect;
- an alarm;
- an abnormal Reading;
- a Process Condition;
- a Finding;
- a failed inspection result;
- a corrective Work Order.

When required function remains acceptable, Process Condition, Finding, or Work Request may be appropriate instead.

## Permit direct creation and evidence-based creation

A Failure Event may be created directly when failure is evident. It may also be established from a Process Condition, Finding, operator report, or other preserved evidence when the failure threshold is met.

No precursor record is mandatory.

## Keep Process Condition, Finding, Failure Event, and Work Order distinct

- Process Condition records a meaningful operational situation requiring Process handling or monitoring.
- Finding records a maintainable condition discovered in Maintenance or inspection context and requiring maintenance disposition.
- Failure Event records actual functional failure and consequence.
- Work Order records accepted maintenance work.

One real-world situation may legitimately involve all four records. Waiotech connects them instead of collapsing them.

## Use separate operational and investigation conditions

Failure Event does not use one lifecycle status.

It has independent operational and investigation state dimensions because plant function may be restored while investigation remains open.

The operational condition is:

```text
active_failure → temporarily_restored → operationally_resolved
active_failure → operationally_resolved
temporarily_restored → active_failure
```

The investigation condition is:

```text
not_required → required
required → in_progress → concluded
concluded → in_progress
```

An overall open or resolved view may be derived but must not become independently edited truth.

## Preserve authoritative failure timing

Failure occurrence, detection, maintenance execution, temporary restoration, renewed failure, full restoration, and terminal operational resolution are different facts.

Reliability timing must not be inferred solely from Work Order start and completion times.

## Related documents

- [Restoration, cause, and recurrence](020-restoration-cause-and-recurrence.md)
- [Findings](../40-maintenance/090-findings.md)
- [Process Conditions](../35-process/020-process-conditions.md)
