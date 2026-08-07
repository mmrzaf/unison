# Lifecycle summary

Lifecycle states represent durable business conditions of specific entities. Derived meaning such as Readiness, availability, attention, overdue state, effective authority, and overall failure resolution remains separate unless Product Authority explicitly defines a state.

## Lifecycle principle

Use:

- lifecycle states for durable entity condition;
- Decisions for governed judgment;
- dispositions for explicit resolution;
- effective periods for time-bounded relationships;
- immutable records for evidence;
- derived conditions for calculated meaning.

## Tenant

```text
provisioning → active
active → suspended → active
provisioning / active / suspended → deactivated
```

Deactivated is terminal for ordinary operation and does not delete retained data.

## Functional Location

```text
active → retired
```

Retirement preserves all installation, Maintenance, Process, Inventory-reference, and Reliability history while preventing new ordinary current use.

## Asset

```text
inactive → active
active → inactive
active / inactive → decommissioned
```

Asset lifecycle is separate from installation and temporary operating condition. Decommissioned is terminal.

## Asset installation

Asset installation uses effective periods rather than a lifecycle status. Only root physical Assets are directly installed; composed children derive installed Functional Location through effective physical-composition history.

```text
installed_at t1 ───────────── removed_at t2
```

The relationship is immutable historical evidence after it ends. Reinstallation creates another effective period.

## Asset Classification and Operational Criticality

Governed catalogue values generally use:

```text
active → retired
```

Operational Criticality changes preserve effective history where required for historical decisions. It is not an Asset lifecycle.

## Process Unit

```text
active → retired
```

Retirement preserves Process history and requires active child units, Streams, Measurement Points, and open Conditions to be resolved according to their owning rules.

## Process Stream

```text
active → retired
```

Retirement prevents new ordinary Process use while preserving historical Readings, Conditions, and topology evidence.

## Measurement Point

```text
active → retired
```

Retirement prevents new ordinary Readings while preserving prior Readings, Measurement Source Mappings, Maintenance trigger evidence, and Reports.

## Data Source

```text
active → retired
```

Retirement prevents new machine source use but preserves prior mapping and Reading provenance.

## Process Reading

Process Reading is immutable evidence and has no lifecycle.

Correction is additive. A correction may invalidate an earlier Reading and establish a replacement without editing the original.

## Process Routine

```text
inactive → active
active → paused → active
inactive / active / paused → retired
```

Only active Routines generate ordinary scheduled Rounds. Paused intervals do not create retroactive obligations. Retired is terminal.

Process Routine Revision uses:

```text
draft → published → superseded
draft → discarded
```

Published revisions are immutable.

## Process Round

```text
open → in_progress → completed
open / in_progress → cancelled
```

Due and overdue are derived, not lifecycle states. Completion means required collection work was accepted; it does not resolve Process Conditions automatically.

## Operational Observation

Operational Observation is immutable evidence and has no lifecycle. Correction is additive.

## Process Condition

```text
active → monitoring → resolved
active → resolved
monitoring → active
resolved → active
```

- **Active:** immediate intervention, decision, or coordination remains required.
- **Monitoring:** no immediate intervention is required, but explicit continued observation and review remain.
- **Resolved:** no active Process obligation remains.

Reopen preserves prior resolution evidence.

Process attention is a separately governed current condition attribute, not a lifecycle state. Canonical values are `routine`, `elevated`, `urgent`, and `critical`; changes preserve attributable history.

## Operational Action and Outcome Assessment

Operational Action and Outcome Assessment are immutable evidence records, not lifecycle entities.

## Work Request

```text
submitted → under_review
under_review → more_information_required
more_information_required → under_review
under_review → deferred
deferred → under_review
under_review → accepted
under_review → rejected
under_review → duplicate
submitted / under_review / more_information_required / deferred → cancelled
```

Terminal states are accepted, rejected, duplicate, and cancelled.

Acceptance establishes or links the Work Orders representing accepted maintenance need.

## Procedure

Stable Procedure identity:

```text
active → retired
```

Procedure Revision:

```text
draft → published → superseded
draft → discarded
published → withdrawn
```

Published revisions are immutable.

## Maintenance Plan

```text
inactive → active
active → paused → active
inactive / active / paused → retired
```

## Maintenance Plan Revision

```text
draft → published → superseded
draft → discarded
```

Published revisions are immutable.

## Scheduled Work

```text
recognized → deferred → recognized
recognized / deferred → generated
recognized / deferred → skipped
recognized / deferred → cancelled
```

Generated, skipped, and cancelled are terminal. Overdue and missed are derived.

## Work Order

```text
draft → prepared → released → in_progress → completed → closed
prepared → draft
released → prepared
in_progress → on_hold → in_progress
completed → in_progress
draft / prepared / released / in_progress / on_hold → cancelled
```

`completed → in_progress` occurs through distinct `withdraw_completion` or `return_for_rework` actions.

A governed emergency-start action may move `draft`, `prepared`, or `released` directly to `in_progress` under the defined emergency contract.

## Work Order Readiness

Readiness is derived for a named action and is not a Work Order lifecycle state.

## Finding

Finding uses explicit disposition rather than a broad lifecycle.

Canonical dispositions include:

- resolved within governing work;
- no action required;
- monitor;
- Work Request created;
- Work Order created or linked;
- duplicate;
- invalid or corrected.

## Failure Event operational condition

```text
active_failure → temporarily_restored → operationally_resolved
active_failure → operationally_resolved
temporarily_restored → active_failure
```

## Failure Event investigation condition

```text
not_required → required
required → in_progress → concluded
concluded → in_progress
```

New evidence may change `not_required → required`. A concluded investigation may be explicitly reopened while preserving the prior conclusion.

Overall Failure Event open/resolved is a derived projection over operational condition, investigation, follow-up, and blockers.

## Cause Assessment

Cause Assessment is immutable evidence. A later assessment may explicitly supersede an earlier assessment without editing it.

## Team

```text
active → retired
```

## Integration Principal

```text
active → revoked
```

Revocation is terminal for that machine identity. New credentials do not change principal identity; a new machine identity requires a new Integration Principal.

## Access Profile

Stable Access Profile identity:

```text
active → retired
```

Access Profile Revision:

```text
draft → published → superseded
draft → discarded
```

## Tenant Control Policy Revision

```text
draft → published → superseded
draft → discarded
```

## Stock Location

```text
active → retired
```

Retirement requires active custody obligations to be resolved.

## Reservation

```text
active → fulfilled
active → released
active → expired
active → cancelled
```

Partial consumption changes remaining quantity without another lifecycle state.

## Replenishment Request

```text
active → fulfilled
active → cancelled
```

Partial fulfillment is derived from linked receipts.

## Stocktaking Run

```text
counting → submitted → approved → posted
submitted → returned_for_recount → counting
counting / submitted / returned_for_recount → cancelled
```

## Report Run

```text
queued → running → completed
queued → cancelled
queued / running → failed
running → cancelled
```

## Tenant Export

```text
queued → running → completed
queued → cancelled
queued / running → failed
running → cancelled
```

## Notification

```text
unread → read
```

Related business action belongs to the subject, not Notification lifecycle.

## Work Requirement outcome

```text
unsatisfied
satisfied
waived
not_applicable
expired
```

These are control outcomes, not an independent workflow lifecycle.

## Derived conditions

Do not store these as independently editable lifecycle truth:

- Work Order Readiness;
- overdue Work Order;
- missed Scheduled Work;
- Stock Balance;
- available stock;
- effective Permissions;
- effective Access Profile Revision;
- Failure Event overall open/resolved projection;
- Report metrics;
- reliability analytics;
- maintenance compliance projections.

## Related documents

- [Canonical terminology](010-canonical-terminology.md)
- [Canonical product model](020-canonical-product-model.md)
