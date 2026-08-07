# Completion, verification, and closeout

Completion records execution claims and evidence. Verification evaluates that claim when required, and closeout confirms that required acceptance, follow-up, cost, and evidence obligations are resolved.

## `completed`

Waiotech must treat completion as submission of execution results, not final acceptance.

Completed means the responsible execution side declares field execution finished and submits the work for acceptance.

Completion records:

- actual execution result;
- Task outcomes;
- supporting evidence;
- unresolved limitations;
- restoration condition;
- Findings;
- temporary repair information;
- completion Actor and time.

Completed does not mean independently verified, accepted, or administratively final.

## A Completion Record

Waiotech must preserve every completion attempt rather than overwriting the latest result.

A Completion Record is the immutable evidence of one completion attempt.

It preserves:

- completing Actor;
- completion time;
- Task results;
- execution narrative;
- evidence;
- restoration condition;
- limitations;
- Findings;
- temporary-repair declarations.

A Work Order may have multiple Completion Records when completion is withdrawn or work is returned for rework.

## Support voluntary withdrawal of submitted completion without deleting evidence

Waiotech must support voluntary withdrawal of submitted completion without deleting evidence.

`withdraw_completion` may be used before completion has been accepted.

It:

- requires a reason;
- preserves the prior Completion Record;
- returns the Work Order to `in_progress`;
- allows additional execution or correction.

## Represent rework as another execution cycle on the same Work Order when the objective remains the same

Waiotech must represent rework as another execution cycle on the same Work Order when the objective remains the same.

Rework is represented by `return_for_rework`, moving the Work Order from `completed` to `in_progress`.

It records a Decision containing:

- reason acceptance failed;
- required correction;
- deciding Actor;
- decision time;
- relevant evidence.

Prior Tasks, execution evidence, Completion Records, and Decisions remain preserved.

## The difference between completion withdrawal and rework

Waiotech must keep voluntary withdrawal and negative acceptance as separate actions.

`withdraw_completion` is initiated by the completing side before acceptance to correct or continue the work voluntarily.

`return_for_rework` is initiated by the verifier or closer because submitted completion was not accepted.

Both return the Work Order to `in_progress`, but their authority and decision meaning are different.

## Require verification only through explicit governed rules

Waiotech must require verification only through explicit governed rules.

Verification is required only when established by:

- a Work Requirement;
- Procedure Revision;
- Maintenance Plan Revision;
- execution risk;
- Operational Criticality rule;
- temporary repair;
- authorized Decision;
- other explicit product rule.

Low-control work may be accepted during closeout without a separate verification step.

## Valid verification outcomes

Waiotech must represent verification through explicit Decisions with evidence and rationale.

Valid verification outcomes are:

- accepted;
- accepted with controlled follow-up;
- returned for rework.

Verification is an immutable Decision, not a mutable boolean.

## Permit conditional acceptance only with explicit controlled obligations

Permit conditional acceptance only with explicit controlled obligations.

This outcome requires an explicit unresolved condition that is safe and authorized to remain temporarily.

It must establish:

- limitation or Finding;
- accountable owner;
- linked Work Request or Work Order where needed;
- due date or review condition;
- Decision rationale;
- operating constraints where applicable.

It must not be used to hide incomplete work without accountable follow-up.

## Establish acceptance through verification when required and through governed closeout otherwise

Establish acceptance through verification when required and through governed closeout otherwise.

When separate verification is not required, the authorized `close_work_order` action accepts the submitted Completion Record as part of closeout.

Policy may permit the completing Actor to close low-control work.

Where separation of duties applies, another authorized Actor must perform closeout.

## Preserve separate planning, execution, acceptance, and finalization times

Waiotech must preserve separate planning, execution, acceptance, and finalization times.

A Work Order should distinguish:

- earliest permitted start;
- target due time;
- latest acceptable completion;
- scheduled execution window;
- first actual execution start;
- hold and resume times;
- completion-attempt times;
- final accepted completion time;
- effective close time;
- cancellation time where applicable.

These facts must not be collapsed into one generic due or completion timestamp.

## Keep closeout focused on final control and reconciliation

Waiotech must keep closeout focused on final control and reconciliation.

Closeout performs only final control checks.

It confirms that:

- completion is accepted;
- required verification is established;
- required follow-up is linked and owned;
- temporary limitations are governed;
- material and Reservation consequences are reconciled;
- final outcome classification is recorded;
- no closure-blocking Requirements remain.

Closeout must not require users to repeat the completion narrative.

## `closed`

Waiotech must make closed operationally terminal and preserve historical truth.

Closed means the Work Order has completed its operational lifecycle and all required acceptance and closeout controls are satisfied.

Closed is terminal for operational execution.

New maintenance work uses a linked Work Order. Historical errors are handled through:

- annotations;
- explicit corrections;
- supplemental evidence;
- governed financial or inventory corrections in the owning modules.

## Define cancellation transitions explicitly

Waiotech must define cancellation transitions explicitly.

Cancellation is allowed from:

- draft;
- prepared;
- released;
- in progress;
- on hold.

A completed Work Order cannot be cancelled because execution has already been declared finished. It must instead be accepted, returned for rework, corrected, or closed.

## Work Order cancellation

Waiotech must treat cancellation as terminal cessation, never deletion or erasure.

Cancellation means the accepted maintenance objective will not continue to normal completion under this Work Order.

Cancellation requires:

- reason;
- authorized Actor;
- cancellation time;
- execution and restoration condition at evaluation time;
- consequences for Tasks;
- responsibility resolution;
- material and Reservation reconciliation;
- Scheduled Work disposition where applicable;
- Findings and follow-up handling.

Cancellation after execution begins preserves all actual labor, material, evidence, costs, and completed activity.

## Keep Work Order and Scheduled Work lifecycles distinct

Waiotech must keep Work Order and Scheduled Work lifecycles distinct.

Cancelling a generated Work Order does not silently cancel or skip its Scheduled Work occurrence.

The originating occurrence must receive its own explicit governed disposition according to why the Work Order was cancelled.

Possible outcomes include:

- regenerate replacement work;
- mark the occurrence skipped;
- cancel the occurrence;
- retain the relationship to the cancelled Work Order and create a linked replacement.

## Editable in each lifecycle state

Tie editability to lifecycle meaning and prohibit silent historical rewriting.

### Draft

Broad controlled editing is allowed.

### Prepared

Only non-material corrections are allowed. Material planning changes require `reopen_preparation`.

### Released

Only non-material edits are allowed. Material changes require `withdraw_release`.

### In progress

Execution evidence and permitted job-specific additions are recorded. Material planning changes require governed variation, pause, renewed authorization, or linked work.

### On hold

Evidence about the hold and resolution may be added. Scope changes remain governed as variations.

### Completed

Submitted evidence is reviewable. Changes require completion withdrawal, correction, or return for rework.

### Closed or cancelled

Only annotation, supplemental evidence, and explicit correction actions are allowed.

## Use Work Request for unaccepted follow-up and Work Order for accepted follow-up

Waiotech must use Work Request for unaccepted follow-up and Work Order for accepted follow-up.

A Finding, limitation, or unresolved condition may create:

- a Work Request when triage is still needed;
- a Work Order when an authorized maintenance Actor has already accepted the need;
- a link to existing work when the need is already represented.

Follow-up must preserve its source relationship to the originating Work Order and evidence.

## Govern temporary repair through completion limitations and follow-up without a separate top-level entity

Waiotech must govern temporary repair through completion limitations and follow-up without a separate top-level entity.

Temporary repair must be explicitly declared in completion evidence.

It records:

- temporary restoration achieved;
- remaining limitation;
- operating constraints;
- accountable owner;
- inspection, review, or expiry condition;
- permanent follow-up need;
- linked follow-up Work Order where required.

The Work Order cannot close unless:

- permanent follow-up is linked and owned; or
- an authorized Decision accepts the result as permanent.

## Use Work Order as the operational cost aggregation context, not as the source ledger for labor, stock, usage, services, or accounting

Waiotech must use Work Order as the operational cost aggregation context, not as the source ledger for labor, stock, usage, services, or accounting.

A Work Order aggregates direct-cost evidence attributed to the maintenance work.

This may include:

- labor evidence;
- material cost evidence;
- external service or contractor cost evidence;
- other direct costs.

The underlying source records remain authoritative for their own facts:

- labor evidence owns recorded effort, rate basis, and labor-cost facts;
- posted Inventory Movements own stock custody and quantity consequences;
- Material Usage owns what was consumed, installed, applied, or otherwise used by the work;
- service and contractor cost evidence owns external direct-charge facts.

The Work Order owns the relationship of those costs to the work and presents historical cost snapshots.

## Preserve historical direct-cost meaning through source-owned snapshots and governed corrections

Waiotech must preserve historical direct-cost meaning through source-owned snapshots and governed corrections.

Historical work should retain the applicable labor rates, material valuations, service costs, and other cost facts used when the cost evidence was recorded or finalized.

Changing rates or valuations effective after the historical record must not silently rewrite historical Work Order cost.

Corrections require explicit actions in the authoritative source records.

## The minimum Work Order model

Waiotech must keep Work Order as the complete controlled execution aggregate without duplicating authority owned by IAM, Inventory, Procedures, or financial systems.

The minimum model contains:

- immutable identity;
- Tenant;
- lifecycle state;
- primary Work Target;
- objective and scope;
- maintenance classification;
- urgency and priority;
- planning route;
- discipline;
- owner Team and applicable individual responsibilities;
- Tasks;
- applied Procedure snapshots;
- Work Requirements;
- readiness evaluations and blockers;
- release evidence;
- execution and hold evidence;
- scope variations;
- Completion Records;
- Verification Decisions;
- Findings and follow-up;
- cancellation or closeout evidence;
- direct-cost attribution;
- preserved action history.

## Use inspection-class Work Orders rather than a separate Inspection aggregate

Waiotech must use inspection-class Work Orders rather than a separate Inspection aggregate.

Normal maintenance inspections are represented by Work Orders with maintenance class `inspection`.

A separate Inspection aggregate would duplicate planning, scheduling, execution, evidence, verification, and closeout already owned by Work Order.

A separate Inspection aggregate is outside Waiotech Product Authority. Introducing it requires a Product Authority amendment defining independent inspection identity, lifecycle, authority, evidence, and maintenance relationships.

## An Inspection Result

Waiotech must use structured Inspection Results beneath Work Order execution.

An Inspection Result is structured evidence produced while executing an inspection Work Order, Task, or Procedure step.

It may record:

- pass, fail, or not applicable;
- measured value and unit;
- selected condition;
- observation;
- acceptance limits;
- supporting evidence.

An Inspection Result belongs to the execution context that produced it.

## Separate inspection execution completion from inspection outcome

Waiotech must separate inspection execution completion from inspection outcome.

Completing an inspection Work Order means the required inspection was performed and recorded correctly. It does not mean the Asset passed the inspection.

Unacceptable results must produce explicit Findings or Failure Events and the required follow-up.

## Related documents
- [Work Order lifecycle and Readiness](060-work-order-lifecycle-and-readiness.md)
- [Findings](090-findings.md)
- [Maintenance materials, tools, services, and cost](../50-inventory/040-maintenance-materials-tools-services-and-cost.md)
