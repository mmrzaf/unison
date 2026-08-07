# Decisions, waivers, and separation of duties

A Decision is a domain-owned immutable outcome with explicit subject, authority, reason, effect, evidence, and temporal meaning. Waivers and independent review use the same bounded Decision semantics without becoming a universal lifecycle engine.

## A Decision

Waiotech must use Decisions for governed judgment, authorization, acceptance, and exception outcomes.

A Decision is immutable evidence that an authorized Actor reached a governed conclusion about a specific subject.

Examples include:

- Work Request Triage Decision;
- Work Order Verification Decision;
- Work Requirement Waiver Decision;
- Stocktaking Approval Decision;
- Failure Event investigation requirement Decision;
- temporary-repair acceptance Decision.

## Standardize Decision meaning without forcing one universal entity

Standardize Decision meaning without forcing one universal entity.

Product Authority should define shared Decision semantics, but it does not require one universal polymorphic aggregate.

Each domain owns its Decisions.

For example:

- Work Request owns Triage Decisions;
- Work Order owns Verification and Waiver Decisions;
- Inventory owns Stocktaking Approval Decisions;
- Failure Event owns investigation Decisions.

Engineering Authority determines whether shared semantics use one implementation or several domain-owned implementations.

## The minimum Decision contract

Waiotech must use one shared minimum semantic contract for all Decisions.

A Decision should preserve:

- decision type;
- subject;
- outcome;
- deciding Actor;
- decision time;
- recorded time where different;
- authority basis;
- applicable policy revision;
- rationale;
- supporting evidence;
- effective time;
- validity or expiry where applicable;
- prior Decision superseded or revoked where applicable.

Decision-specific contracts may require additional facts.

## Preserve every effective Decision as historical evidence

Waiotech must preserve every effective Decision as historical evidence.

An effective Decision is immutable.

A subsequent action may:

- supersede it;
- revoke it where revocation is meaningful;
- reopen the governed process;
- record a correction.

The original Decision remains visible.

## Decision supersession

Waiotech must use supersession when governed judgment changes.

Supersession means a newer Decision replaces an earlier Decision for interpretation after supersession.

The earlier Decision remains valid for the period in which it applied.

The newer Decision must reference the Decision it supersedes and explain the changed conclusion.

## Decision revocation

Waiotech must use revocation only for Decisions with continuing effect.

Revocation ends the continuing effect of a Decision without claiming the original Decision was incorrect when made.

Examples may include:

- ending a Waiver before expiry;
- revoking an approval after its conditions cease to be valid;
- terminating temporary authority.

Revocation records:

- revoking Actor;
- revocation time;
- reason;
- resulting obligations.

## Decision expiry

Waiotech must preserve time-bounded authority explicitly.

Expiry is the planned end of a Decision’s validity.

After expiry, any Requirement or blocker previously controlled by the Decision becomes applicable again where still relevant.

Expiry does not delete or invalidate the historical Decision.

## Share Decision structure, not one generic outcome catalogue

Share Decision structure, not one generic outcome catalogue.

Each Decision type defines its own governed outcomes.

Examples include:

- Verification: accepted, accepted with controlled follow-up, returned for rework.
- Triage: accepted, rejected, duplicate.
- Waiver: granted, denied.
- Stocktaking approval: approved, returned for recount, rejected.
- Investigation requirement: required, not required.

## Represent approval through Decisions and named actions rather than adding approval states everywhere

Waiotech must represent approval through Decisions and named actions rather than adding approval states everywhere.

Usually no.

Approval is normally a Decision or an authorization action associated with an entity.

Examples include:

- release authorizes a Work Order execution package;
- verification evaluates completion;
- stocktake approval authorizes variance posting;
- waiver permits a Requirement exception.

An `approved` lifecycle state should be added only when the entity itself remains in a durable approved condition over time.

## A waiver

Waiotech must use waiver only for Requirements whose contract explicitly permits the `waived` outcome.

A Waiver Decision places one explicitly waivable Work Requirement in the `waived` outcome for a named blocked action under defined conditions.

A waiver does not delete, change, or satisfy the Requirement. It preserves a distinct exception outcome and its continuing conditions.

## Make every waiver narrow, traceable, and time-bounded where appropriate

Waiotech must make every waiver narrow, traceable, and time-bounded where appropriate.

A Waiver Decision must record:

- exact Work Requirement;
- blocked action;
- outcome;
- deciding Actor;
- authority basis;
- reason;
- supporting evidence;
- compensating controls;
- effective time;
- expiry;
- affected Work Order state;
- revocation where applicable.

## Apply expiry prospectively without rewriting valid historical actions

Waiotech must apply expiry prospectively without rewriting valid historical actions.

When a waiver expires, the Work Requirement becomes an active blocker again if it remains applicable and unsatisfied.

Historical actions completed while the waiver was valid remain governed by that valid historical Decision.

## Make non-waivable requirements explicit

Waiotech must make non-waivable requirements explicit.

A Requirement is waivable only when its authoritative contract explicitly permits waiver.

Mandatory product, safety, permit, isolation, security, and integrity rules cannot be waived merely because an Actor has broad authority.

## Do not collapse exceptions into one generic entity

Waiotech must not collapse exceptions into one generic entity.

Different exceptions have different business meaning, authority, lifecycle, and consequences.

Examples include:

- emergency start;
- Work Requirement waiver;
- scope variation;
- temporary repair;
- controlled follow-up;
- platform recovery action.

Each must use its own named domain action and Decision evidence.

## Apply separation of duties only to explicitly protected decisions

Waiotech must apply separation of duties only to explicitly protected decisions.

Separation of duties is required only when an explicit product rule or Tenant control requires independent judgment.

Examples may include:

- verification of controlled work;
- publication of protected Access Profile revisions;
- approval and posting of high-risk Stocktaking Runs;
- high-impact Waiver Decisions.

Routine work must not inherit unnecessary approval chains.

## Define independence per Decision type

Waiotech must define independence per Decision type.

Each protected Decision type must define which prior Actor relationships are disallowed.

Examples include:

- verifier must not be the completing Actor;
- protected Access Profile publisher must not be its sole author;
- Stocktaking approver must not be the counter where independence is required;

Independence is based primarily on Actor identity, not merely different Team names.

## Keep authority and independence as separate requirements

Waiotech must keep authority and independence as separate requirements.

An Actor must satisfy both:

- the Permission required to make the Decision;
- the applicable independence requirement.

Being a different person, senior employee, owner Team member, or manager does not independently grant Decision authority.

## Treat unavailable independence as a real control blocker

Waiotech must treat unavailable independence as a real control blocker.

The protected Decision remains blocked unless its authoritative rule defines an explicit escalation or exception path.

Waiotech must not silently weaken separation of duties because the Tenant lacks available personnel.

Possible governed responses may include:

- escalation to another authorized Team;
- temporary authorized external reviewer;
- a specifically defined emergency Decision;
- postponement until independence is available.

## Define change protection per control rather than using a vague sensitive-policy category

Waiotech must define change protection per control rather than using a vague sensitive-policy category.

Only when the affected product-defined control declares it.

Each control should specify whether changing it requires:

- ordinary publication authority;
- independent approval;
- stronger separation of duties;
- delayed effective time;
- additional evidence.

Examples may include controls affecting:

- verification independence;
- protected Access Profile publication;
- Stocktaking approval separation;
- high-impact waiver authority.

## Keep policy configuration separate from IAM authority

Waiotech must keep policy configuration separate from IAM authority.

IAM owns Permissions, Access Grants, Access Profiles, Team assignments, and authorization evaluation.

Tenant Control Policy may require a particular approval, verification, or separation-of-duty condition, but it cannot create Permissions or alter Access Grants.

## Related documents
- [Tenant Control Policy](010-tenant-control-policy.md)
- [Reason codes](030-reason-codes.md)
- [Work Requirements and scope control](../40-maintenance/070-work-requirements-and-scope.md)
