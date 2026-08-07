# Scheduled Work

Scheduled Work is the durable identity of one recognized Maintenance Plan occurrence for one published target and one nominal trigger instance. It preserves why planned maintenance became applicable before or alongside Work Order generation without becoming a second execution model.

## A Scheduled Work occurrence

Scheduled Work belongs to:

- one stable Maintenance Plan;
- one Plan Revision effective for recognition;
- one resolved Functional Location or Asset target;
- one nominal calendar or Measurement Point trigger instance.

It records the recognized recurring obligation and its original trigger meaning.

Scheduled Work is not executable maintenance. Responsibility for planning, execution, evidence, completion, verification, and closeout belongs to the resulting Work Order.

## Require a Work Order before execution

A Scheduled Work occurrence becomes represented as accepted maintenance work when its Work Order is generated.

Before Work Order generation, Scheduled Work is a governed recurring obligation awaiting generation or explicit disposition. It cannot itself be started, completed, or used as a substitute execution record.

The relationship between occurrence and Work Order remains preserved permanently.

## Recognize occurrences early enough for planning while preserving original trigger facts

An occurrence may be recognized within the Plan Revision's governed planning or generation horizon.

For calendar work, recognition may occur before the nominal due time.

For Measurement Point work, recognition occurs when accepted Process Reading evidence establishes the governed threshold instance or, where Product Authority explicitly supports it, when a deterministic planning horizon identifies an approaching threshold without changing which threshold remains authoritative.

Recognition time is not the nominal trigger time and must not rewrite the original trigger facts.

## The Scheduled Work lifecycle

Scheduled Work uses explicit reversible deferral and distinct terminal outcomes:

```text
recognized → deferred → recognized
recognized / deferred → generated
recognized / deferred → skipped
recognized / deferred → cancelled
```

- **Recognized:** the occurrence exists and awaits Work Order generation or explicit disposition.
- **Deferred:** review or Work Order generation has been intentionally postponed.
- **Generated:** a Work Order represents the occurrence as accepted maintenance work.
- **Skipped:** the occurrence was valid and applicable but an authorized decision determined it will not be performed.
- **Cancelled:** the occurrence should no longer be treated as an applicable valid obligation for an explicit governed reason.

Generated, skipped, and cancelled are terminal Scheduled Work states.

## Deferral preserves the obligation

Deferral is available only before Work Order generation.

Deferral preserves the original Plan Revision, target, nominal trigger, timing, and due meaning while postponing review or generation.

It records:

- deferral reason;
- one accountable active Team or responsible active User according to the governing responsibility contract;
- explicit review time or review condition;
- deciding Actor;
- decision time;
- additional evidence required by policy.

Deferral does not move the nominal occurrence or make overdue work appear current.

After a Work Order has been generated, postponement belongs to Work Order planning and control rather than Scheduled Work.

## Keep skipped and cancelled distinct

Skipped and cancelled are different terminal decisions.

**Skipped** means the occurrence was valid and applicable, but an authorized decision determined that the maintenance will intentionally not be performed for this occurrence.

Examples may include:

- equivalent maintenance was already performed under another accepted work record;
- the maintenance opportunity is intentionally omitted under an accepted Decision;
- the consequence of skipping this one occurrence is explicitly accepted.

**Cancelled** means the occurrence should not remain an applicable maintenance obligation.

Examples may include:

- duplicate occurrence identity;
- incorrect recognition caused by invalid trigger evidence;
- invalid Plan configuration affecting this occurrence;
- the target ceased to be applicable before the nominal obligation took effect under a governed rule.

Both outcomes preserve deciding Actor, time, reason, evidence, and any related occurrence or work identity required to explain the decision.

A generic close action must not erase this distinction.

## Preserve original calendar trigger and execution-window facts

Calendar Scheduled Work preserves the Plan-defined facts needed to interpret timeliness, including:

- nominal occurrence date or time;
- earliest allowed start where defined;
- due date or time;
- latest acceptable completion where defined;
- recognition time;
- Work Order generation time where applicable;
- deferral and disposition history.

These values retain their original meaning when the occurrence later becomes overdue, is deferred, or is represented by a Work Order.

## Preserve Measurement Point trigger facts

Measurement Point Scheduled Work preserves:

- resolved trigger Measurement Point;
- measured quantity and canonical unit;
- nominal threshold instance or governed threshold-sequence identity;
- accepted Process Reading or deterministic accepted Reading sequence used to recognize the occurrence;
- Reading effective time;
- Reading quality and provenance required for traceability;
- recognition time;
- reset, rollover, replacement, or sequence evidence where relevant;
- optional forecasted calendar date where a governed forecast exists;
- latest acceptable threshold where the Plan defines one;
- Work Order generation time;
- deferral and disposition history.

A forecasted calendar date is informative unless the Plan explicitly assigns it another governed meaning. The authoritative trigger remains the configured Measurement Point threshold and accepted Process Reading evidence.

## Make occurrence identity independent of Plan Revision

Logical occurrence identity is based on:

- stable Maintenance Plan;
- resolved target;
- nominal trigger instance.

For calendar work, the trigger instance is the governed nominal occurrence sequence or date.

For Measurement Point work, the trigger instance is the governed threshold sequence or threshold identity.

The Plan Revision is preserved for traceability but does not permit another occurrence to be created for the same logical Plan, target, and trigger instance merely because a new revision was published.

## Require duplicate-safe recognition and Work Order generation

Re-evaluating the same Plan, target, and nominal trigger instance must return the existing Scheduled Work.

Repeated generation commands must return or reference the Work Order already representing the occurrence when one exists.

Retries, repeated machine Readings, duplicate manual recognition attempts, synchronization retries, or Worker retries must not create duplicate Scheduled Work or Work Orders.

A later correction that invalidates trigger evidence requires explicit governed reconciliation. It must not silently delete a Scheduled Work or Work Order that has already entered operational history.

## Apply Plan changes prospectively

Publishing a new Plan Revision does not rewrite Scheduled Work or Work Orders that already exist.

Existing occurrences preserve:

- original Plan Revision;
- original resolved target;
- original trigger identity and facts;
- Procedure Revision basis where applicable;
- disposition and Work Order relationship.

Occurrences recognized under the new effective policy use the new Plan Revision without duplicating an occurrence whose stable identity was already established.

If a Plan change makes an existing ungenerated occurrence operationally inconsistent with the new policy, the occurrence is handled through explicit generation, deferral, skip, cancellation, or another defined reconciliation action rather than silent migration.

## Preserve obligations reached during Plan pause

Pausing a Maintenance Plan suspends ordinary generation behavior according to the Plan contract.

Existing Scheduled Work remains visible and governed.

Calendar occurrences or Measurement Point thresholds reached during the pause must not silently disappear. On resume, Waiotech evaluates preserved trigger history and establishes the applicable missed obligations so each can be generated, deferred, skipped, cancelled, or otherwise explicitly reconciled according to Product Authority.

A pause action may intentionally include an explicit Decision about defined occurrences. It must not imply that all obligations reached during the pause vanished.

## Make Plan retirement prospective and history-preserving

Retiring a Maintenance Plan prevents ordinary new occurrences for post-retirement trigger instances.

Retirement does not delete or rewrite:

- prior Plan Revisions;
- historical Scheduled Work;
- existing ungenerated Scheduled Work;
- generated Work Orders;
- Procedure snapshots;
- trigger evidence;
- completed maintenance evidence.

Existing Scheduled Work and Work Orders continue through their own governed lifecycles and dispositions.

## Derive missed and overdue meaning from original facts

Missed and overdue are derived conditions, not manually assigned lifecycle states.

They are evaluated from:

- current evaluation time or accepted Measurement Point evidence;
- original nominal trigger facts;
- execution window or latest acceptable completion/threshold where defined;
- Scheduled Work state;
- resulting Work Order state where one exists.

Missed work remains visible. Waiotech must not silently cancel it, merge it into the next occurrence, regenerate it under a new nominal identity, or move its original due facts forward merely to make the queue appear current.

## Keep recurring occurrences independently traceable

Each nominal trigger instance has its own durable Scheduled Work identity.

A later recurring occurrence does not remove or merge an earlier incomplete occurrence.

Where one Work Order legitimately satisfies several occurrences under an explicitly governed reconciliation contract, every original Scheduled Work identity and its disposition remains preserved. A shared Work Order must not erase the individual obligations.

## Keep the recurring-maintenance concepts distinct

The minimum authority chain is:

```text
Procedure
└── Procedure Revision
    └── Work Order-owned Task / Requirement / evidence snapshots

Maintenance Plan
└── Maintenance Plan Revision
    ├── exact Procedure Revision where applicable
    ├── exact trigger rule
    ├── exact resolved targets and trigger bindings
    └── generation defaults

Maintenance Plan Revision
└── Scheduled Work per target and nominal trigger instance
    └── Work Order
```

The distinctions are:

- Procedure owns reusable method identity.
- Procedure Revision owns one exact method version.
- Maintenance Plan owns stable recurring intent.
- Plan Revision owns one exact recurring policy.
- Scheduled Work owns one recognized occurrence identity.
- Work Order owns accepted executable maintenance work.

## Treat Plan-generated maintenance as accepted only through Work Order

A published Maintenance Plan authorizes governed recognition of recurring obligations under its effective rules. It does not itself make every possible occurrence accepted executable maintenance.

The recurring need becomes accepted maintenance work when a Scheduled Work occurrence generates or is explicitly linked to its Work Order under the owning contract.

Scheduled Work remains preserved because occurrence identity, due meaning, deferral, skip/cancellation decision, and recurring compliance cannot be reconstructed reliably from the Work Order alone.

## Related documents

- [Procedures and Maintenance Plans](040-procedures-and-maintenance-plans.md)
- [Work Order lifecycle and Readiness](060-work-order-lifecycle-and-readiness.md)
- [Process Readings and Operational Observations](../35-process/010-readings-and-observations.md)
