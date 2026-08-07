# Restoration, cause, and recurrence

Failure Event restoration, investigation, Cause Assessments, duplicate handling, and recurrence preserve the difference between returning function, understanding why failure occurred, and learning whether the event repeats.

## Temporary restoration

Temporary restoration means acceptable function is available under an explicit temporary limitation while permanent resolution remains outstanding.

It must preserve:

- restoration time;
- restoring Work Order or other authorized basis;
- restoration evidence;
- remaining limitation;
- operating constraints;
- accountable owner;
- inspection, review, or expiry obligation;
- permanent follow-up.

Temporary restoration does not erase the Failure Event or represent permanent resolution.

A renewed failure returns the operational condition to `active_failure` and preserves the entire temporary-operation interval.

## Terminal operational resolution

A Failure Event may become `operationally_resolved` when the active operational obligation ends through an exact outcome such as:

- required function restored;
- physical Asset replaced;
- target decommissioned or retired from required service;
- another authorized terminal resolution.

Replacement or decommissioning must not be falsely described as restoration.

## Investigation proportionality

Investigation may be required because of consequence, recurrence, Operational Criticality, safety or environmental significance, compliance, repeated temporary repair, management Decision, or another explicit policy rule.

The decision that investigation is required or not required preserves deciding Actor, time, rationale, and governing rule where applicable.

New evidence may escalate a prior `not_required` conclusion to required investigation without rewriting the earlier decision.

A concluded investigation may be reopened to `in_progress` when material new evidence requires further investigation. Reopening preserves the prior conclusion, reopening Actor, time, reason, and new evidence; it does not edit the earlier investigation history.

## Cause Assessments

A Cause Assessment is immutable evidence of one accepted reliability conclusion about why the Failure Event occurred.

It may preserve:

- failure mode;
- assessed primary cause;
- contributing factors;
- confidence;
- method;
- assessor;
- evidence;
- assessment time;
- explicit supersession of an earlier assessment.

Waiotech must not force false certainty. Valid conclusions include confirmed, probable, multiple contributing causes, inconclusive, or no cause identified after proportionate investigation.

## Keep mode, cause, and consequence distinct

- **Failure mode:** how required function was lost or degraded.
- **Cause:** why the failure occurred.
- **Consequence:** what operational effect resulted.

Narrative may supplement these facts but must not collapse their meaning.

## Duplicate evidence

When several reports describe the same failure occurrence, Waiotech preserves source evidence without counting several Failure Events.

One event may be designated primary and another candidate linked as duplicate with source, Actor, time, evidence, and duplicate decision preserved.

Duplicate determination is based on the same occurrence, not merely the same target or failure mode.

## Recurrence relationships

A new functional failure after operational resolution normally creates a new Failure Event.

A Failure Event may link to prior events using explicit recurrence meaning such as:

- repeat failure;
- continuation of unresolved failure;
- common-cause event;
- related distinct occurrence.

The relationship preserves rationale, deciding Actor, and decision time.

## Closure

A Failure Event is considered resolved only when:

- the operational condition is `operationally_resolved`;
- required investigation is concluded or explicitly not required;
- mandatory follow-up is represented by governed records with accountable ownership;
- unresolved temporary limitations are explicitly controlled;
- no Reliability closure blocker remains.

Linked follow-up Work Orders do not always need to be closed unless the governing rule requires them before Reliability resolution.

## Related documents

- [Failure Events](010-failure-events.md)
- [Reliability analysis](030-reliability-analysis.md)
