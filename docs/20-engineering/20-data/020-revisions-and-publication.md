# Revisions and publication

Stable governed definitions use one editable working draft and immutable published revisions. Publication, supersession, withdrawal, restoration, concurrency, APIs, and migration preserve exact historical meaning without exposing version-control mechanics to ordinary users.

## Use revisions only for governed definitions whose exact content must remain historically interpretable

Waiotech must use revisions only for governed definitions whose exact content must remain historically interpretable.

Procedure, Maintenance Plan, Access Profile, and Tenant Control Policy use stable definition identities with immutable published revisions. Another concept uses this model only when Product Authority assigns publication and historical interpretation to it.

## The canonical revision structure

Waiotech must separate stable identity from immutable content history.

The structure is:

```text
stable definition
├── optional working draft
├── immutable published revision history
└── one published revision effective at evaluation time
```

The stable definition owns identity and lifecycle. Revisions own exact content.

## Keep definition editing simple and conflict-explicit

Waiotech must keep definition editing simple and conflict-explicit.

One working draft may exist for the definition. Concurrent editors use optimistic concurrency on that draft. A separate branching and merge model is outside Engineering Authority.

## Keep unpublished changes operationally inactive

Waiotech must keep unpublished changes operationally inactive.

It grants no Permission, generates no Scheduled Work, controls no Tenant action, and does not replace the published definition. It is accepted unpublished content only.

## Make draft persistence explicit and non-effective

Waiotech must make draft persistence explicit and non-effective.

`save_changes` validates the editable draft schema, checks concurrency, preserves the editing Actor and recorded time, and updates only the working draft. It does not create a published revision or event asserting publication.

## Review meaning rather than storage-level diffs

Review meaning rather than storage-level diffs.

Review compares the working draft with the published revision effective at evaluation time. It presents semantic additions, removals, changed facts, authority impact, target impact, control impact, and publication blockers.

## Make publication the only route from unpublished content to effective definition

Waiotech must make publication the only route from unpublished content to effective definition.

Publication is one protected idempotent transaction that:

- revalidates draft content;
- checks expected concurrency;
- verifies authority and separation of duties;
- verifies referenced identities;
- computes required impact and target snapshots;
- creates an immutable published revision;
- makes it effective;
- preserves the superseded revision;
- clears or advances the working draft;
- records audit and events.

## Keep published revisions immutable

Waiotech must keep published revisions immutable.

A correction creates new unpublished changes and another publication. Typographical, security, or migration corrections do not authorize in-place mutation of published content.

## Make effective content unambiguous and queryable

Waiotech must make effective content unambiguous and queryable.

The stable definition references the published revision effective at evaluation time through an explicit relation or effective-period contract. Numeric ordering alone does not determine effectivity.

## Freeze the definition basis of accepted operational records

Waiotech must freeze the definition basis of accepted operational records.

Where exact historical meaning matters, the operational record references the applicable immutable revision rather than only the stable definition. Examples include Work Orders referencing Procedure Revisions and Scheduled Work referencing Plan Revisions.

## Keep content history and stable-definition lifecycle separate

Waiotech must keep content history and stable-definition lifecycle separate.

Supersession makes another published revision effective. Withdrawal prevents ordinary new application of the stable definition while preserving all revisions and historical references. Retirement prevents ordinary use according to the Product Authority lifecycle.

## Restore content through forward change

Restore content through forward change.

Restoration copies selected historical content into new unpublished changes. The historical revision remains unchanged and is not reactivated. Review and publication remain mandatory.

## Make deletion of unpublished content explicit without altering effective history

Waiotech must make deletion of unpublished content explicit without altering effective history.

`discard_changes` removes the working draft through a protected idempotent action, preserves audit where required, and leaves published revisions unchanged.

## Prevent two publications from claiming the same predecessor

Waiotech must prevent two publications from claiming the same predecessor.

The publication command locks or compare-and-swaps the stable definition and working draft, verifies the expected published revision, and rejects a stale publication. Only one transaction can establish the next effective revision.

## Keep revision identity available without exposing version-control mechanics

Waiotech must keep revision identity available without exposing version-control mechanics.

A revision may have a stable internal sequence or identifier for evidence and support. User workflows do not depend on manual revision-number selection. Labels are presentation metadata and not authority.

## Preserve truthful publication provenance

Waiotech must preserve truthful publication provenance.

The owning module emits fact events such as `maintenance.maintenance_plan.published` or `iam.access_profile.published`, with envelope schema version. Migration-origin revisions use migration-specific provenance and do not fabricate a historical human publication event.

## Expose stable-object workflows rather than revision CRUD

Waiotech must expose stable-object workflows rather than revision CRUD.

Ordinary APIs address the stable definition and expose named actions such as:

```text
save-changes
review-changes
publish-changes
discard-changes
restore-content
change-history
```

Published revisions are read-only evidence resources.

## Keep user experience aligned with product change obligations

Waiotech must keep user experience aligned with product change obligations.

The user sees published content, unpublished changes, semantic comparison, blockers, impact, publication, discard, history, and restoration. The interface does not expose branching, merging, manual supersession, or direct activation.

## Create truthful canonical history without inventing product actions

Waiotech must create truthful canonical history without inventing product actions.

Migration establishes stable identity, immutable migration-origin revisions, effective content supported by source facts, and a working draft only when unpublished content is evidenced. Migration provenance remains separate from historical author or publisher attribution.

## Related documents
- [Procedures and Maintenance Plans](../../10-product/40-maintenance/040-procedures-and-maintenance-plans.md)
- [Tenant Control Policy](../../10-product/60-policy-and-decisions/010-tenant-control-policy.md)
- [Plan target resolution](030-plan-target-resolution.md)
