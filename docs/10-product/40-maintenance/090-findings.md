# Findings

A Finding records a specific maintainable condition discovered through governed work or inspection that requires explicit maintenance disposition. It preserves discovery context without becoming accepted maintenance work or a Reliability Failure Event.

## A Finding

A Finding is created when a discovered condition is significant enough that it must not disappear inside general notes or execution narrative.

Examples include:

- defect;
- failed inspection criterion;
- maintainable abnormal condition;
- compliance issue requiring maintenance disposition;
- temporary limitation discovered during work;
- improvement opportunity;
- missing or contradictory maintenance information.

Routine observations that need no independent decision remain ordinary execution or inspection evidence.

## Preserve discovery context

A Finding preserves:

- discovering Actor;
- discovery effective time and recorded time where distinct;
- governing Work Order, Task, Procedure step, inspection result, verification activity, Failure Event investigation, or other supported source context;
- affected Functional Location or Asset where applicable;
- concise description;
- supporting evidence;
- classification and first significance assessment where defined.

Later disposition must not rewrite what was originally discovered.

## Keep Finding distinct from Process Condition and Failure Event

A Finding is a Maintenance concept.

- **Process Condition** records an operational situation requiring Process handling or monitoring.
- **Finding** records a maintainable condition requiring maintenance disposition.
- **Failure Event** records actual loss or materially unacceptable degradation of required function.
- **Work Order** records accepted maintenance work.

The same real situation may involve several of these records. Waiotech links them and preserves ownership instead of converting one record into another.

## Finding disposition

Every Finding requiring independent tracking receives one explicit disposition:

- resolved within governing work;
- no action required;
- monitor;
- Work Request created;
- follow-up Work Order created;
- linked to an existing Work Order;
- duplicate;
- invalid or corrected.

Disposition preserves deciding Actor, time, reason, and related evidence or follow-up identity.

A generic `closed` state or bare `accepted` disposition is prohibited because it does not explain how the condition was controlled.

## Resolve within governing work only when scope remains authorized

A Finding may be resolved inside the governing Work Order when the corrective action remains within the authorized objective, scope, controls, Requirements, and competence of that work.

Resolution preserves:

- action taken;
- Task or evidence that resolved the Finding;
- resolving Actor;
- resolution time;
- resulting condition.

When correction materially expands scope, changes the primary objective, or requires separate planning or acceptance, the Finding creates or links follow-up maintenance work instead.

## Follow-up maintenance

A Finding may:

- create a Work Request when triage remains necessary;
- create a Work Order directly when an authorized maintenance Actor already accepts the need;
- link to compatible existing work.

Creating or linking work does not erase the Finding or discovery evidence.

## Defects remain Finding classification

A defect that has not crossed the Failure Event threshold is represented as a Finding with the applicable defect classification.

Waiotech does not use a separate Defect aggregate. Introducing one would require independent Product Authority for identity, lifecycle, ownership, ageing, prioritization, evidence, reporting, and resolution.

## Related documents

- [Completion, verification, and closeout](080-completion-verification-and-closeout.md)
- [Failure Events](../55-reliability/010-failure-events.md)
- [Work Requests](030-work-requests.md)
- [Process Conditions](../35-process/020-process-conditions.md)
