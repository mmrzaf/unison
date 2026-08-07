# Permissions and Access Profiles

Human authorization follows one deterministic path through Team Membership, Team Access Profile Assignment, published Access Profile Revisions, Access Grants, and stable Permission codes.

## Prohibit direct User-to-Access Profile and Membership-to-Access Profile relationships

Waiotech must prohibit direct User-to-Access Profile and Membership-to-Access Profile relationships.

Direct assignments would create a second human authorization path and make access harder to review and govern.

All human access must flow through Teams. A person who needs different access must join an appropriate Team or the Tenant must create a separate Team with the required Access Profiles.

## Remove Role from normative vocabulary

Waiotech must remove Role from normative vocabulary.

Role is too ambiguous because it can mean a job title, organizational position, responsibility, or permission collection.

Use:

- **Access Profile** for a named collection of Access Grants;
- **Team position or job title** for organizational description;
- **Responsibility type** for operational responsibility.

Application logic must not check Access Profile names, Team names, or job titles.

## A Permission

Waiotech must authorize protected actions through stable Permission codes, never through Team or Access Profile names.

A Permission is a stable code representing one protected product capability.

Examples include:

- `plant.asset.read`;
- `plant.asset.create`;
- `process.reading.record`;
- `process.round.execute`;
- `process.condition.resolve`;
- `inventory.stock.read`;
- `inventory.movement.issue`;
- `maintenance.work_order.release`.

Permissions should be atomic enough to evaluate clearly and stable enough to remain meaningful across implementation changes.

Product modules define their protected Permissions. IAM maintains the catalogue and evaluates the authority granted through them.

## An Access Grant

Waiotech must keep Access Grants atomic, allow-only, and Tenant-wide.

An Access Grant includes one Permission in an Access Profile Revision.

The canonical model is allow-only. An Access Grant does not contain deny rules, arbitrary conditions, priorities, expressions, or domain-specific subjects.

The effective Tenant comes from the acting Membership and Team context.

## Permissions apply Tenant-wide within the effective Tenant

Permissions apply Tenant-wide within the effective Tenant. Generic fine-grained IAM scope is outside Waiotech Product Authority.

Generic scope designs such as resource-specific selectors, untyped free-form structures, or expression languages would couple IAM to every product domain and create inconsistent enforcement.

Requirements such as allowing access only to mechanical Assets affect related work, reporting, exports, classifications, and information disclosure. They require a complete scoped-authorization design rather than a generic field.

IAM should not reference Functional Locations, Assets, Process Units, Stock Locations, Work Orders, or other domain entities.

## An Access Profile

Waiotech must use Access Profiles as reusable named collections of exact Permissions.

An Access Profile is a stable named authority policy containing an exact collection of Access Grants through its effective published revision.

Examples may include:

- Inventory Storekeeper;
- Inventory Supervisor;
- Maintenance Technician;
- Maintenance Planner;
- Plant Administrator;
- Process Operator;
- Process Supervisor;
- Read-only Auditor.

The name explains the intended use but grants no authority by itself. Authorization is determined only by the Access Grants in the effective revision.

## Replace generic Access Assignment with Team Access Profile Assignment for human authorization

Waiotech must replace generic Access Assignment with Team Access Profile Assignment for human authorization.

Use a narrow **Team Access Profile Assignment**.

It binds:

- one Team;
- one Access Profile;
- effective start;
- optional effective end;
- assigned by;
- assignment reason;
- revocation evidence.

It must not contain arbitrary assignee types, domain subjects, or resource scopes.

Changes preserve history through expiration, revocation, or supersession rather than rewriting previous assignments.

## Keep immutable published Access Profile Revisions with one effective revision at a time

Waiotech must keep immutable published Access Profile Revisions with one effective revision at a time.

Revisions preserve the exact Access Grants that were effective at a particular time without preventing immediate access changes.

An Access Profile is the stable object assigned to Teams. Its published revision contains the effective Access Grants.

When a new revision is published:

- it becomes effective according to its publication rule;
- all Teams assigned to the Access Profile receive the new grants;
- Team assignments do not need to be recreated;
- the previous revision remains immutable for audit and restoration.

This allows administrators to change access once while preserving a reliable history of what authority existed before the change.

## Present Access Profile changes as one simple edit-and-publish workflow backed by immutable revisions

Waiotech must present Access Profile changes as one simple edit-and-publish workflow backed by immutable revisions.

The user edits the stable Access Profile, reviews the Permission changes, and publishes them.

Unpublished changes have no authorization effect.

When changes are published:

- Waiotech creates an immutable underlying Access Profile Revision;
- the new revision becomes effective according to the publication rule;
- the previous effective revision becomes superseded;
- all Teams assigned to the Access Profile receive the new effective grants;
- historical authority remains interpretable.

The product may show change history and effective times, but ordinary users do not manage revision numbers, branches, merges, or supersession actions.

## Represent meaningful access differences through separate Access Profiles, not hidden Team exceptions

Waiotech must represent meaningful access differences through separate Access Profiles, not hidden Team exceptions.

Changing a shared Access Profile affects every Team assigned to it.

When one Team needs different access:

1. copy the existing Access Profile into a new profile;
2. change and publish the new profile;
3. assign the new profile to that Team;
4. remove the previous assignment when appropriate.

Team-specific exceptions must not be embedded inside a shared Access Profile.

## Use templates and grouped Permission selection to simplify profile creation without changing the grant model

Waiotech must use templates and grouped Permission selection to simplify profile creation without changing the grant model.

Keep the underlying Access Grant model exact, but provide structured authoring assistance.

Waiotech should provide product-managed Access Profile templates such as Process Operator, Inventory Storekeeper, or Maintenance Planner. Creating from a template copies its Access Grants into a normal Tenant Access Profile draft.

The Permission catalogue should also support:

- grouping by module and capability;
- selecting or clearing a whole group;
- search;
- showing selected Permissions only;
- comparison against a template;
- clear descriptions of each Permission.

Templates and catalogue groups are authoring aids. They do not grant authority and are not alternative authorization concepts.

## Support governed Permission dependencies with visible authoring assistance and publication validation

Waiotech must support governed Permission dependencies with visible authoring assistance and publication validation where an action cannot function safely without supporting Permissions.

For example, issuing stock may require visibility of Items, Stock Locations, and stock balances.

When an administrator selects a Permission, Waiotech should show and add its mandatory dependencies. Publishing must reject a profile that omits required dependencies.

Dependencies should remain narrow and explicit. They must not silently add large unrelated capability groups.

## Related documents
- [Users, Memberships, and Teams](010-users-memberships-and-teams.md)
- [Responsibility and external actors](030-responsibility-and-actors.md)
- [Authorization architecture](../../20-engineering/40-security/010-authorization-architecture.md)
