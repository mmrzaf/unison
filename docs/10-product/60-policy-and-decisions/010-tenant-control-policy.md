# Tenant Control Policy

Tenant Control Policy selects and configures product-defined controls for one Tenant through immutable published revisions. It cannot weaken Product Authority or create an unrestricted rule engine.

## Policy

Waiotech must use policy only for configurable product-defined controls.

Policy means governed Tenant configuration that selects values for product-defined controls.

Policy may determine matters such as:

- when verification is required;
- whether separation of duties applies;
- approval thresholds;
- permitted tolerances;
- evidence requirements;
- escalation timing;
- exception conditions.

Policy does not create arbitrary business behavior, Permissions, workflow states, scripts, or domain rules.

## Do not support user-defined rules, scripts, expressions, or generic workflow configuration

Waiotech must not support user-defined rules, scripts, expressions, or generic workflow configuration.

A generic rule engine would require arbitrary conditions, scopes, precedence, conflict resolution, historical evaluation, and cross-domain references.

That would introduce unnecessary complexity and make product behavior difficult to understand and enforce consistently.

## A Tenant Control Policy

Waiotech must use one Tenant Control Policy per Tenant.

Tenant Control Policy is the stable identity of the Tenant’s governed control configuration.

Each Tenant has one Tenant Control Policy.

Its effective published revision contains values for the product-defined controls defined by Waiotech Product Authority.

Examples may include:

- verification required for specified Operational Criticality levels;
- independent approval required for protected Access Profile changes;
- maximum Work Request deferral period;
- whether the same Actor may approve and post a low-risk Stocktaking Run;
- whether defined temporary-repair conditions require independent verification.

## Keep the control catalogue product-owned and closed

Waiotech must keep the control catalogue product-owned and closed.

Waiotech defines the available controls, their meaning, value types, permitted ranges, dependencies, and operational effects.

Tenant administrators choose permitted values.

Tenants cannot create new control types or change the meaning of existing controls.

## A Tenant Control Policy Revision

Waiotech must use immutable published revisions for historically significant control configuration.

A Tenant Control Policy Revision contains one exact version of all configured Tenant control values.

A revision preserves:

- control values;
- created by and created at;
- published by and published at;
- effective time;
- change reason;
- required approval evidence where applicable.

Its lifecycle is:

```text
draft → published → superseded
draft → discarded
```

Draft revisions have no operational effect. Published revisions are immutable.

## Preserve policy history through immutable revisions

Waiotech must preserve policy history through immutable revisions.

Policy settings may change while historical actions and Decisions must retain the exact controls that governed them.

Without revisions, configuration changes effective after an action would alter the apparent basis of previous:

- releases;
- starts;
- waivers;
- verifications;
- approvals;
- stock adjustments;
- platform recovery Decisions.

## Present policy configuration as controlled edit, impact review, and publication backed by immutable revisions

Waiotech must present policy configuration as controlled edit, impact review, and publication backed by immutable revisions.

An authorized user edits the stable Tenant Control Policy, reviews the affected controls and their operational impact, and publishes the changes.

Waiotech manages the underlying Tenant Control Policy Revision automatically.

The user does not manage revision numbers, branches, merge operations, or supersession directly. Change history and the exact policy that governed a historical action remain available for evidence and interpretation.

## Keep the policy identity permanent and version only its configuration

Waiotech must keep the policy identity permanent and version only its configuration.

Each Tenant always has one stable Tenant Control Policy identity.

Only its revisions change. The policy identity is not activated, paused, or retired.

A product-provided default revision may apply until the Tenant publishes another revision.

## Keep policy Tenant-wide and add narrower typed behavior only through explicit product controls

Waiotech must keep policy Tenant-wide and add narrower typed behavior only through explicit product controls.

Tenant Control Policy applies within one Tenant.

The canonical model does not support generic policy scope by:

- Asset;
- Functional Location;
- Work Order;
- Stock Location;
- Team;
- explicit subject;
- arbitrary classification;
- user-defined expression.

A specific product control may include typed parameters such as an Operational Criticality threshold or monetary value threshold where that parameter is part of the control’s defined meaning.

## Allow only explicitly designed control parameters

Waiotech must allow a control parameter only when that dimension is explicitly defined by the specific product control.

For example:

```text
Verification requirement:
- never
- for critical Functional Locations
- for high and critical Functional Locations
- always
```

This is a typed product setting, not a generic scope or expression system.

## The precedence between product rules and Tenant policy

Waiotech must use explicit product ownership instead of generic policy merging.

Precedence is simple:

1. Mandatory product rules always apply.
2. Tenant policy selects values only within product-permitted bounds.
3. Procedures, Plans, Work Orders, and Decisions may add stricter Requirements.
4. They cannot silently remove mandatory Requirements.
5. A waivable Requirement may be bypassed only through a valid Waiver Decision.

There is no generic “more specific rule wins” or configurable allow-versus-deny precedence model.

## Make non-configurable product guarantees non-waivable through policy

Waiotech must make non-configurable product guarantees non-waivable through policy.

Mandatory product rules protect product integrity, security, evidence, safety, and historical meaning.

A control may permit Tenant choice only within the bounds defined by the product.

## Permit additive control while prohibiting silent weakening

Permit additive control while prohibiting silent weakening.

A Work Order, Procedure Revision, Plan Revision, or authorized Decision may add stricter Requirements where operational context requires them.

They must not silently remove mandatory product, policy, Procedure, or Plan Requirements.

## Waiotech is not a generic workflow or policy engine

Waiotech is not a generic workflow or policy engine.

Tenant administrators may configure product-defined controls and parameters.

They cannot create:

- arbitrary conditions;
- scripts;
- expressions;
- custom workflow actions;
- custom Permission logic;
- generic approval rules;
- arbitrary resource scopes.

New control types require product design and an authoritative product contract.

## Apply controls effective at evaluation time to new action evaluations

Waiotech must apply controls effective at evaluation time to new action evaluations.

New evaluations use the effective Tenant Control Policy Revision.

Protected actions such as release, start, verification, closeout, waiver, or approval must evaluate the controls effective at evaluation time.

## Preserve historical control context

Waiotech must preserve historical control context.

Historical actions and Decisions retain the policy revision that governed them.

A policy change effective after the historical action must not rewrite whether a historical action was valid under the controls effective at that time.

## Define existing-work impact separately for every control

Waiotech must define existing-work impact separately for every control.

Each product-defined control must declare its effect on existing Work Orders.

A policy change may:

- affect only newly created Work Orders;
- add a blocker effective at evaluation time to release, start, verification, or closeout;
- require review of released work;
- require release withdrawal when the approved execution basis is invalidated;
- have no effect on in-progress or historical work.

A blanket migration rule must not be assumed.

## Distinguish invalidated approval from action readiness at evaluation time

Waiotech must distinguish invalidated approval from action readiness at evaluation time.

Release withdrawal is required only when the policy change materially invalidates the previously approved execution package.

A newly applicable temporary start blocker may prevent execution without invalidating release.

## Configurable retention is outside this Product Authority and requires a dedicated Product Authority contract

Configurable retention is outside this Product Authority and requires a dedicated Product Authority contract.

Retention affects legal obligations, evidence classes, privacy, correction, deletion, anonymization, integrations, and backup behavior.

A simple configurable retention period would not adequately define those consequences.

## Related documents
- [Decisions, waivers, and separation of duties](020-decisions-waivers-and-separation-of-duties.md)
- [Revisions and publication](../../20-engineering/20-data/020-revisions-and-publication.md)
