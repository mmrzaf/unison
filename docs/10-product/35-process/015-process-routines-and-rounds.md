# Process Routines and Rounds

Process Routines define repeatable human operating rounds through immutable published revisions. Process Rounds are accountable executions of those routines. They make routine human data collection fast and structured without creating a generic form engine or duplicating Process Readings and Operational Observations.

## A Process Routine

A Process Routine is the stable identity of a reusable operating round or log-sheet definition.

Process Routine uses:

```text
inactive → active
active → paused → active
inactive / active / paused → retired
```

An active Routine requires one effective published Process Routine Revision. Only active Routines generate ordinary scheduled Rounds. Pause suspends new scheduled occurrences for the explicit pause interval without cancelling Rounds that already exist. Resume applies prospectively; paused intervals do not create retroactive Round obligations unless an explicit correction establishes that the Routine was paused incorrectly.

Retirement permanently prevents new ordinary Rounds while preserving all published revisions and historical Round evidence.

## A Process Routine Revision

A Process Routine Revision is immutable published content defining the exact human collection method for future Rounds.

A revision may define:

- purpose and concise instructions;
- default responsible Team where applicable;
- ordered collection entries;
- optional calendar recurrence and execution window;
- evidence expectations;
- other explicitly defined human-round behavior.

Process Routine Revision uses:

```text
draft → published → superseded
draft → discarded
```

Publishing creates a new immutable revision and supersedes the previously effective revision for subsequent Round creation. It never edits the definition used by an existing or historical Round.

## Use only explicit collection entry types

Alpha Process Routine entries are exactly:

- **Reading entry** — record one Process Reading for one configured Measurement Point;
- **Observation entry** — record one Operational Observation against one explicit supported Process subject.

An entry defines its subject, instruction, required or optional meaning, and any reference guidance needed by the operator.

A Reading entry may present revision-owned reference guidance such as expected context or a compatible numeric reference range. Such guidance helps the User interpret the entry; it does not automatically create a Process Condition, Failure Event, Maintenance work, or machine alarm.

Waiotech must not turn Process Routine entries into an unrestricted form, formula, script, or workflow builder.

## A Process Round

A Process Round is the accountable execution record for one Process Routine Revision at one nominal occurrence or one authorized ad-hoc start.

It preserves:

- Process Routine and exact Revision;
- nominal occurrence time where scheduled;
- execution window where defined;
- responsible Team and executing User where applicable;
- start and completion times;
- required-entry completion;
- linked Readings and Operational Observations;
- evidence and cancellation reason where applicable.

Readings and Observations created during the Round remain canonical Process-owned evidence. The Round references them; it does not copy their values into a second authority.

## Process Round lifecycle

Process Round uses:

```text
open → in_progress → completed
open → cancelled
in_progress → cancelled
```

- **Open:** the Round exists and execution has not started.
- **In progress:** accountable human execution has started.
- **Completed:** every required entry and required evidence has been accepted.
- **Cancelled:** the Round will not be completed under this occurrence; reason and Actor are required.

Due, overdue, and approaching are derived conditions from the nominal occurrence, execution window, current state, and Tenant time. They are not lifecycle states.

## Keep Round completion separate from operational resolution

Completing a Round means the required collection work was performed. It does not assert that the plant is healthy or that an abnormal condition was resolved.

A Reading or Observation recorded during a Round may support creation of a Process Condition, Work Request, Finding, or Failure Event according to the owning domain criteria. No such record is created automatically merely because a value is outside reference guidance.

## Support scheduled and ad-hoc Rounds

A Process Routine Revision may define deterministic calendar recurrence or may be available only for authorized ad-hoc execution.

Scheduled occurrence identity must remain deterministic and duplicate-safe. Repeated generation, retry, device synchronization, or publication of a later Revision must not create several Rounds for one logical occurrence.

## Preserve context and minimize entry effort

When a Round entry already identifies Measurement Point or operational subject, Waiotech must carry that context into the Reading or Observation interaction. Users must not re-select known Tenant, subject, unit, Routine, or Round context.

The Round experience should support rapid sequential entry while keeping each accepted Reading or Observation individually attributable and historically interpretable.

## Related documents

- [Process Readings and Operational Observations](010-readings-and-observations.md)
- [Process Conditions](020-process-conditions.md)
- [Android Work App](../../30-experience/040-android-work-app.md)
