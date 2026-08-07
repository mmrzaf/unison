# Work Requests

A Work Request preserves a reported maintenance concern before Waiotech accepts it for execution. Triage clarifies the report and records a Decision to accept, request information, reject, cancel, defer, or identify a duplicate.

## Work Request purpose

Waiotech must use Work Request only for intake and triage of unaccepted maintenance needs.

A Work Request captures a reported candidate maintenance need that has not yet been accepted as work.

It supports:

- reporting;
- evidence submission;
- clarification;
- triage;
- classification;
- duplicate detection;
- acceptance or rejection.

A Work Request is not:

- a lightweight Work Order;
- authorization to execute work;
- an accepted maintenance backlog item;
- a scheduling or material-reservation record.

## Keep Work Request as an optional intake route, not a mandatory precursor to Work Order

Waiotech must keep Work Request as an optional intake route, not a mandatory precursor to Work Order.

Authorized maintenance personnel, Scheduled Work occurrences, Failure Events, Findings, and product-defined accepted-work integrations may create Work Orders directly when the need is already understood and accepted.

Creating a Work Request first would add unnecessary workflow ceremony in those cases.

Direct Work Order creation must still record the applicable origin, trigger, target, classification, creator, accountable Team, and reason.

## Preserve requester claims separately from maintenance assessment

Waiotech must preserve requester claims separately from maintenance assessment.

Waiotech must preserve the report as submitted, including:

- requester identity;
- reported time;
- original description;
- reported observations;
- submitted evidence;
- proposed Asset;
- proposed Functional Location or reported area;
- proposed urgency;
- proposed maintenance class;
- proposed discipline.

These are authoritative as reported facts, even when triage subsequently determines that some proposals were incorrect.

Triage must not overwrite the original report.

## Distinguish reported facts from assessed maintenance facts

Waiotech must distinguish reported facts from assessed maintenance facts.

The following requester-supplied facts are proposals until triage confirms or corrects them:

- Asset;
- Functional Location;
- urgency;
- maintenance class;
- discipline;
- interpretation of the condition;
- requested action.

The observation, description, requester, reported time, and submitted evidence remain authoritative as reported.

## Record triage conclusions without rewriting requester evidence

Waiotech must record triage conclusions without rewriting requester evidence.

Triage may determine:

- confirmed Work Target;
- assessed urgency;
- assessed maintenance class;
- assessed discipline;
- whether more information is required;
- whether the request duplicates an existing need;
- whether the need should become accepted work;
- whether the request should be rejected, cancelled, or deferred;
- which Work Orders represent the accepted need.

Triage conclusions must preserve the reviewer, time, rationale, and any difference from the original report.

## Do not require Asset identification at submission

Waiotech must not require Asset identification at submission.

The reporter may not know the Asset, may identify the wrong Asset, or may report an unidentified item or general symptom.

A proposed Asset may be added during submission or triage, but it is not required to submit the request.

## Allow Functional Location-based Work Requests

Waiotech must allow Functional Location-based Work Requests.

The need may genuinely concern a place rather than an Asset.

Examples include:

- building fabric;
- lighting;
- signage;
- access infrastructure;
- cleanup;
- area inspection.

The Functional Location remains proposed until triage confirms it as the correct target.

## Permit unidentified Work Requests while requiring a governed Work Target before Work Order execution

Permit unidentified Work Requests while requiring a governed Work Target before Work Order execution.

The reporter must provide enough description, evidence, or reported area for triage to understand and investigate the need.

Free text is acceptable for intake. Before accepted work is released or started, the resulting Work Order must satisfy the authoritative Work Target rules.

Waiotech must not force users to create false Assets or unnecessary Functional Locations merely to submit a request.

## The Work Request lifecycle

Waiotech must require review before acceptance, rejection, or duplicate disposition and keep terminal outcomes immutable.

The lifecycle is:

```text
submitted → under_review

under_review → more_information_required
more_information_required → under_review

under_review → deferred
deferred → under_review

under_review → accepted
under_review → rejected
under_review → duplicate

submitted / under_review /
more_information_required / deferred
→ cancelled
```

Terminal states are:

- accepted;
- rejected;
- duplicate;
- cancelled.

More information required and deferred are non-terminal states.

## Make acceptance and resulting Work Order representation one completed product action

Waiotech must make acceptance and resulting Work Order representation one completed product action.

No intermediate accepted state is allowed.

A Work Request becomes accepted only when every accepted part of the maintenance need is already represented by at least one linked Work Order.

No observable product state may contain an accepted Work Request whose accepted need is not represented by Work Order.

## Use `accept_work_request` rather than `accept_and_create_work_order`

Waiotech must use `accept_work_request` rather than `accept_and_create_work_order`.

Use a named `accept_work_request` action.

The action chooses exactly one resolution mode:

- create one or more new Work Orders; or
- link the request to one existing compatible Work Order.

Alpha does not combine existing and newly created Work Orders in one acceptance command. That restriction keeps resolution ownership and atomic evidence unambiguous while still allowing one reported need to be split into several newly created Work Orders.

The action records the Triage Decision and establishes all required Work Order links before the Work Request enters `accepted`.

## Allow acceptance into existing Work Orders

Waiotech must allow acceptance into existing Work Orders.

A reviewer may link the request to one existing Work Order when that Work Order already represents the need. The Work Order must belong to the same Tenant, have the same confirmed target, be visible to the Actor, be non-terminal, and not already contain the same Work Request link.

This avoids duplicate work when:

- several people report the same condition;
- the need was already accepted through another route;
- the request belongs to a larger intervention already being managed.

## Allow one Work Request to be accepted into multiple Work Orders

Waiotech must allow one Work Request to be accepted into multiple Work Orders.

One Work Request may contain several independently managed maintenance objectives requiring separate:

- Work Targets;
- disciplines;
- planning;
- ownership;
- execution;
- scheduling;
- acceptance;
- costing;
- closeout.

Each accepted part of the request must be represented by a linked Work Order.

## Allow several Work Requests to support one Work Order without merging away their reporting history

Waiotech must allow several Work Requests to support one Work Order without merging away their reporting history.

Several reports may describe the same maintenance need or contribute additional evidence to one accepted intervention.

Each Work Request retains its own:

- requester;
- original description;
- reported time;
- evidence;
- triage outcome.

## Replace `converted` with `accepted`

Waiotech must replace `converted` with `accepted`.

The term is inaccurate when the request is linked to existing work or split into several Work Orders.

Use `accepted` as the terminal state.

Accepted has a strict meaning: the accepted maintenance need is represented by linked Work Orders.

## A Triage Decision

Waiotech must preserve explicit decision evidence for material triage outcomes.

A Triage Decision records an authorized conclusion about a Work Request.

It should preserve:

- outcome;
- decision maker;
- decision time;
- rationale;
- assessed urgency;
- assessed maintenance class;
- assessed discipline;
- confirmed target where applicable;
- Work Order links for acceptance;
- duplicate link for duplicate disposition.

Acceptance, rejection, and duplicate classification require a Triage Decision.

Reviewer-driven cancellation should also preserve a decision. Requester withdrawal may be recorded as a cancellation action.

## Keep `more_information_required` as an active, accountable waiting state

Waiotech must keep `more_information_required` as an active, accountable waiting state.

This state means triage cannot yet determine the correct disposition and has requested specific additional information.

It must record:

- information requested;
- responsible person or party;
- requested by;
- requested time;
- optional response deadline;
- received response and responder.

When sufficient information is received, the request returns to `under_review`.

The reviewer may also resume review without the requested response when other evidence becomes sufficient.

## Require explicit resolution of unanswered information requests

Waiotech must require explicit resolution of unanswered information requests.

The request must not remain indefinitely hidden in `more_information_required`.

An authorized reviewer may:

- proceed using available evidence;
- defer it with an explicit review condition;
- reject it because the need cannot be substantiated;
- cancel it when the report is no longer valid.

The chosen action must record its rationale.

## Keep controlled deferral and prohibit indefinite deferral without a review trigger

Waiotech must keep controlled deferral and prohibit indefinite deferral without a review trigger.

Deferred means assessment of the unaccepted need has been intentionally postponed until a defined time or condition.

Deferral must record:

- reason;
- responsible Team or Membership;
- review date or explicit review condition;
- decided by;
- decision time.

The request must return to active review when the date or condition is met.

## Do not use Work Request states to manage accepted backlog

Waiotech must not use Work Request states to manage accepted backlog.

Work Request deferral postpones the decision about whether the reported need should become accepted work.

Once the need is accepted, scheduling, postponement, deferral consequence, and execution timing belong to the Work Order.

## Keep duplicate terminal while preserving and linking the original report

Waiotech must keep duplicate terminal while preserving and linking the original report.

A duplicate disposition means another record already represents substantially the same reported maintenance need.

The duplicate Work Request remains preserved with its original report and evidence.

It must link to:

- the primary Work Request while the need remains under triage; or
- the Work Order that already represents the accepted need.

## Use maintenance-need equivalence, not simple target matching, for duplicate determination

Waiotech must use maintenance-need equivalence, not simple target matching, for duplicate determination.

Two requests are duplicates when they report substantially the same condition or maintenance need, not merely because they concern the same Asset or Functional Location.

Triage should consider:

- symptom or condition;
- target;
- reported time;
- operational consequence;
- existing accepted work;
- whether separate action or acceptance outcomes are required.

Additional evidence from a duplicate request should remain available to the primary request or linked Work Order.

## Keep rejection and cancellation as separate terminal outcomes

Waiotech must keep rejection and cancellation as separate terminal outcomes.

**Rejection** means an authorized reviewer evaluated the reported need and determined that it should not become maintenance work.

Examples include:

- condition is acceptable;
- no maintenance intervention is required;
- report is unsupported after review;
- the matter belongs outside maintenance.

**Cancellation** means the request was withdrawn, entered in error, superseded before assessment, or otherwise became invalid without a negative maintenance determination.

Examples include:

- requester withdraws an incorrect report;
- the wrong subject was reported;
- the condition no longer exists before review and no investigation is required.

## Use the dedicated duplicate disposition

Waiotech must use the dedicated duplicate disposition.

Duplicate is a distinct outcome because the reported need may be valid even though another record already represents it.

Calling it rejected would incorrectly imply that maintenance determined no work was required.

## Preserve rejection as terminal and represent materially new consideration through a linked Work Request

Waiotech must preserve rejection as terminal and represent materially new consideration through a linked Work Request.

A rejected Work Request remains terminal and its Triage Decision must not be rewritten.

When new evidence materially changes the situation, create a new linked Work Request that references the rejected request and preserves the reason for renewed triage.

Minor factual corrections may be appended through the normal evidence-correction mechanism, but they do not reopen the rejected lifecycle.

## Do not use Work Request cancellation to alter accepted work

Waiotech must not use Work Request cancellation to alter accepted work.

After acceptance, the Work Request is a historical intake record linked to accepted Work Orders.

Any decision to cancel, stop, or replace the accepted work belongs to the Work Order lifecycle.

## Keep Work Request triage responsibility separate from Work Order responsibility

Waiotech must keep Work Request triage responsibility separate from Work Order responsibility.

A Work Request may have one responsible triage Team and, where needed, an assigned reviewer.

Triage ownership is accountability for:

- review;
- clarification;
- escalation;
- timely disposition.

It does not grant authority and does not automatically become ownership of resulting Work Orders.

Each resulting Work Order receives responsibility according to its own authoritative rules.

## Preserve the submitted report and represent subsequent changes as additions or explicit corrections

Waiotech must preserve the submitted report and represent subsequent changes as additions or explicit corrections.

The original submitted report must not be silently rewritten.

The requester may add:

- clarifications;
- corrections;
- additional evidence;
- responses to information requests.

Material corrections should preserve both the original statement and the corrected information.

## Prohibit execution against Work Request

Waiotech must prohibit execution against Work Request.

A Work Request does not authorize:

- labor execution;
- material issue;
- scheduling as accepted work;
- operational verification;
- maintenance closeout.

The need must first be represented by a Work Order.

## Allow Findings to use Work Request when triage is needed, but do not require it universally

Waiotech must allow a Finding to use a Work Request when a discovered condition requires triage before becoming accepted work, but must not require that route universally.

A Finding may also:

- require no action;
- be monitored;
- link directly to existing work;
- support direct Work Order creation when an authorized workflow already accepts the need.

## Do not force Failure Events through Work Request

Waiotech must not force Failure Events through Work Request. A Work Request is used only when the required maintenance response still needs triage.

A Failure Event may instead create or link directly to Work Orders when the maintenance need is already accepted, especially during urgent or emergency response.

## Process-origin Work Requests

A Process Condition may create or support a Work Request when Operations determines that physical maintenance investigation or work is needed.

The Work Request must preserve the originating Process Condition relationship and carry forward relevant plant context and evidence without copying ownership of the Condition. Triage may refine the proposed Functional Location, Asset, urgency, class, or discipline, but it must not rewrite the Process record.

A Process Condition may instead link directly to an existing Work Request or Work Order when the maintenance response already exists.

## Keep intake requirements low while requiring enough information for accountable triage

Waiotech must keep intake requirements low while requiring enough information for accountable triage.

Submission requires enough information to establish a meaningful report:

- requester or reporting Actor;
- reported time;
- description of the observed need or condition;
- Tenant context.

Asset, Functional Location, urgency, maintenance class, discipline, and supporting evidence may be optional at submission.

## The minimum Work Request model

Waiotech must keep Work Request focused on truthful reporting, accountable triage, and explicit disposition.

The minimum Work Request model contains:

- immutable identity;
- Tenant;
- reporting Actor;
- reported time;
- original description and observations;
- submitted evidence;
- optional proposed Asset or Functional Location;
- optional proposed urgency, maintenance class, and discipline;
- lifecycle status;
- triage Team and optional reviewer;
- information requests and responses;
- deferral condition;
- Triage Decisions;
- linked Work Orders;
- duplicate relationship;
- preserved history.

## A Work Request

Waiotech must keep Work Request as the intake concept for unaccepted maintenance needs.

A Work Request represents a proposed maintenance need that has not yet been accepted as executable work.

A Work Request may originate from:

- a person;
- a Finding;
- a Process Condition;
- a Failure Event;
- an external system;
- another governed workflow.

A Work Request may be rejected, marked duplicate, clarified, or accepted into one or more Work Orders.

## Avoid duplicate work by allowing acceptance into existing or multiple Work Orders

Avoid duplicate work by allowing acceptance into existing or multiple Work Orders.

Acceptance may:

- create a new Work Order;
- link the Work Request to an existing Work Order;
- link several duplicate Work Requests to one Work Order;
- split one Work Request into several Work Orders when it contains independently managed objectives.

Every accepted part of the need must be represented by a linked Work Order.

## Related documents
- [Accepted maintenance need](020-accepted-maintenance-need.md)
- [Work Order lifecycle and Readiness](060-work-order-lifecycle-and-readiness.md)
- [Findings](090-findings.md)
