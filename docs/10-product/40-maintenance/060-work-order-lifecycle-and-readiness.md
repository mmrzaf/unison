# Work Order lifecycle and Readiness

A Work Order is the authoritative record of one accepted maintenance objective. Its lifecycle separates preparation, release, execution, completion submission, and closeout, while Readiness remains a derived action-specific evaluation.

## A Work Order

Waiotech must use one canonical Work Order model for all accepted maintenance execution.

A Work Order is the authoritative record of one accepted maintenance objective and its controlled progression from preparation through execution, acceptance, and closeout.

It owns the work-specific context for:

- scope and objective;
- primary Work Target;
- maintenance classification;
- responsibility;
- Tasks;
- applied Procedure content;
- Work Requirements;
- planning and execution;
- completion evidence;
- verification and acceptance;
- Findings and follow-up;
- direct-cost attribution;
- lifecycle history.

## The Work Order lifecycle

Waiotech must use explicit lifecycle states for preparation, authorization, execution, completion submission, and final closeout.

The normal lifecycle is:

```text
draft → prepared → released → in_progress → completed → closed
```

Additional governed transitions are:

```text
in_progress → on_hold → in_progress
completed → in_progress
released → prepared
prepared → draft

draft → cancelled
prepared → cancelled
released → cancelled
in_progress → cancelled
on_hold → cancelled
```

Emergency start may move a Work Order from `draft`, `prepared`, or `released` directly to `in_progress` when minimum emergency requirements are satisfied.

## Use `prepared` for lifecycle state and reserve planned or unplanned for the derived planning route

Waiotech must use `prepared` for lifecycle state and reserve planned or unplanned for the derived planning route.

`Planned` already describes the derived planning route of a Work Order:

- planned;
- unplanned.

Using the same word as a lifecycle state would create two different meanings.

`Prepared` means the Work Order has reached the required planning result and contains the information needed for release evaluation.

## Use `released` as the lifecycle state and evaluate readiness separately for each protected action

Waiotech must use `released` as the lifecycle state and evaluate readiness separately for each protected action.

Release is a historical authorization action. Readiness is a derived condition evaluated from authoritative facts that may change after the evaluation.

A Work Order may remain released while temporarily blocked because:

- an execution window has not opened;
- a permit expired;
- material became unavailable;
- an isolation is incomplete;
- a requirement satisfaction expired;
- authority changed after release.

The release remains part of history even when the Work Order is not ready at evaluation time to start.

## `draft`

Waiotech must use draft as the flexible planning state.

Draft means the accepted maintenance objective exists, but the Work Order has not yet reached the minimum planning standard required for preparation.

Broad controlled editing is allowed in draft, including changes to:

- target;
- scope;
- classification;
- Tasks;
- Procedure;
- responsibility;
- timing;
- Requirements;
- expected evidence.

A draft Work Order cannot begin normal execution.

## Make `prepare_work_order` a named action that validates the minimum planning contract

Waiotech must make `prepare_work_order` a named action that validates the minimum planning contract.

Before preparation is complete, the Work Order must contain:

- one primary Work Target;
- clear objective;
- defined scope boundary;
- maintenance class;
- trigger and origin;
- urgency and priority;
- primary discipline;
- accountable owner Team;
- timing expectations;
- at least one Task;
- applicable Procedure content;
- applicable Work Requirements;
- expected outcome;
- expected evidence;
- known execution-risk and control needs.

The depth of planning may remain proportional to the work, but required facts cannot be replaced by vague narrative.

## Require at least one Task instead of supporting a separate taskless execution path

Waiotech must require at least one Task instead of supporting a separate taskless execution path.

Every Work Order must contain at least one Task before release.

A draft Work Order may temporarily have no Tasks while planning is incomplete.

For simple work that does not require meaningful decomposition, the Work Order may contain one broad Task representing the objective.

Example:

```text
Inspect and correct the reported coupling guard issue
```

## `prepared`

Waiotech must treat prepared as a meaningful validated state rather than a label that survives arbitrary edits.

Prepared means the Work Order satisfies the minimum planning contract and is eligible for release evaluation.

Prepared does not mean:

- authorized for execution;
- ready at evaluation time to start;
- scheduled;
- free from temporary blockers.

Material changes after preparation invalidate that planning result and require an explicit return to draft.

## Preserve the meaning of prepared by requiring explicit lifecycle regression after material change

Waiotech must preserve the meaning of prepared by requiring explicit lifecycle regression after material change.

A material planning change requires `reopen_preparation`, returning the Work Order from `prepared` to `draft`.

Material changes include changes to:

- primary Work Target;
- core objective;
- scope boundary;
- maintenance class;
- execution method;
- required Procedure content;
- execution-risk assessment;
- mandatory controls;
- acceptance criteria;
- accountable responsibility;
- substantial Task structure.

Minor corrections that do not change planning meaning may remain allowed with audit evidence.

## Release

Waiotech must define release as authorization of the prepared execution package, not permanent confirmation that work can start at every subsequent execution attempt.

Release means an authorized Actor approves the prepared execution package for operational use under its defined scope, method, Tasks, controls, and Requirements.

Release confirms that release-blocking conditions are satisfied. It does not guarantee that every condition required to start execution will remain satisfied at execution time.

Release records:

- releasing Actor;
- release time;
- applicable authorization;
- evaluated release readiness;
- material Decisions;
- approved execution package identity.

## Readiness

Waiotech must make readiness action-specific and derived from authoritative facts at evaluation time.

Readiness is the authoritative derived evaluation of whether all Requirements and core conditions blocking a specified action are satisfied at evaluation time.

Readiness is evaluated separately for actions such as:

- prepare;
- release;
- start;
- complete;
- verify;
- close.

There is no single permanent Work Order readiness value.

## A readiness blocker

Return explicit actionable blockers rather than only a ready or not-ready result.

A readiness blocker is a derived explanation of why a requested action is not allowed at evaluation time.

A blocker identifies:

- blocked action;
- unsatisfied Requirement or core condition;
- reason;
- authoritative source;
- responsible party where known;
- required resolution.

A blocker is not independently edited or manually closed.

## Evaluate start Readiness through authoritative online facts or an explicitly bounded offline Work Package

Waiotech must evaluate start Readiness through authoritative online facts or an explicitly bounded offline Work Package.

Release does not create permanent start Readiness.

An online `start_work` or applicable resume action evaluates effective:

- Requirements;
- execution window;
- permits;
- isolations;
- material availability;
- responsibility;
- authority;
- restrictions effective at evaluation time;
- other start-blocking conditions.

An offline start or resume is permitted only through a governed offline Work Package whose issuance contract proves that every required condition is fixed for the package period, represented by an immutable snapshot, or verifiable locally through a defined field input. Synchronization preserves the package basis and evaluates conflicts and revocation explicitly.

## Use `released → prepared` for release withdrawal and `prepared → draft` only through explicit reopening of preparation

Waiotech must use `released → prepared` for release withdrawal and `prepared → draft` only through explicit reopening of preparation.

Release should be withdrawn when a material change means the previously approved execution package is no longer valid.

Examples include changes to:

- primary target;
- core objective;
- method;
- mandatory Procedure content;
- execution risk;
- permit or isolation boundary;
- shutdown scope;
- acceptance criteria;
- required authority.

`withdraw_release` records the reason and returns the Work Order from `released` to `prepared`.

When the required replanning is substantial, a separate `reopen_preparation` action then returns it from `prepared` to `draft`.

A temporary blocker alone does not require release withdrawal.

## Make the primary Work Target immutable after first execution starts

Waiotech must make the primary Work Target immutable after first execution starts.

Before release, the primary target may be corrected through controlled planning changes.

After release but before execution starts, changing the target requires release withdrawal and renewed preparation or release.

After first execution starts, the primary Work Target cannot change because doing so would reinterpret:

- Tasks;
- evidence;
- labor;
- material;
- Findings;
- costs;
- failure relationships;
- maintenance history.

Work concerning a different primary target must use a linked Work Order.

## Require accountable Team responsibility and add named individual responsibility only where operationally required

Waiotech must require accountable Team responsibility and add named individual responsibility only where operationally required.

Not universally.

Every Work Order requiring accountable progression must have an owner Team before preparation.

A named coordinator, lead executor, planner, verifier, or closer is required only where the applicable lifecycle stage, work organization, or policy requires that responsibility.

Crew-based and contractor work may use Team accountability, a coordinator, and separately recorded contributing Executors.

## Support one governed emergency-start path without creating a separate emergency Work Order model

Waiotech must support one governed emergency-start path without creating a separate emergency Work Order model.

Emergency start may occur from:

- draft;
- prepared;
- released.

It moves the Work Order to `in_progress`.

Emergency start is allowed only when immediate action is necessary and normal preparation or release requirements cannot all be completed first.

## Require a rigid emergency minimum, including an executable Task, while allowing explicitly recorded bypass of normal controls

Waiotech must require a rigid emergency minimum, including an executable Task, while allowing explicitly recorded bypass of normal controls.

Emergency start requires at least:

- primary Work Target;
- immediate objective;
- at least one immediate Task, which may be broad when emergency evidence is incomplete;
- emergency reason;
- authorized Actor;
- accountable Team;
- execution coordinator where required;
- known immediate hazards;
- minimum immediate controls;
- record of bypassed normal Requirements;
- required retrospective obligations.

Emergency start must not permit execution against an empty or meaningless draft.

## Preserve the actual exception path and require retrospective control without rewriting history

Waiotech must preserve the actual exception path and require retrospective control without rewriting history.

Emergency start:

- sets the Work Order to `in_progress`;
- establishes planning route as `unplanned`;
- records bypassed Requirements;
- creates retrospective planning and review obligations;
- preserves the emergency justification;
- requires unresolved controls and documentation to be completed according to the emergency workflow.

Release after emergency start must not rewrite the Work Order as planned.

## `in_progress`

Waiotech must use in progress as the active execution state.

In progress means authorized execution has begun and the Work Order is accumulating operational evidence.

During this state:

- Tasks are executed;
- labor and material evidence is recorded;
- Inspection Results and Findings may be created;
- scope variations are governed;
- completion may be submitted when all completion blockers are resolved.

Planning facts cannot be casually rewritten after execution begins.

## `on_hold`

Waiotech must use on hold only for temporary suspension after execution has started.

On hold means execution began but is temporarily suspended with remaining work expected to resume.

Placing work on hold records:

- hold reason;
- Actor and time;
- responsible Team;
- safety and restoration condition at evaluation time;
- resume condition or review time;
- unresolved blockers.

On hold does not erase actual execution, complete Tasks, or imply that the Asset is restored.

## Recheck applicable resume conditions rather than treating hold as a passive pause

Recheck applicable resume conditions rather than treating hold as a passive pause.

`resume_work` returns the Work Order to `in_progress`.

Before resuming, Waiotech evaluates applicable readiness at evaluation time, authority, controls, and expired Requirements.

A prior start does not automatically authorize resumption without reevaluation.

## Related documents
- [Work Requirements and scope control](070-work-requirements-and-scope.md)
- [Completion, verification, and closeout](080-completion-verification-and-closeout.md)
- [Work Requests](030-work-requests.md)
