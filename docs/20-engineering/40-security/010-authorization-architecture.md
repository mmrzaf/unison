# Authorization architecture

Authorization uses explicit human, Integration Principal, internal-system, and platform paths. Each path remains bounded to its own Product Authority responsibility and cannot imply another path.

## IAM ownership

IAM owns User, Membership, Team, Team Membership, Permission, Access Profile, Access Profile Revision, Access Grant, Team Access Profile Assignment, Integration Principal, browser session, Android installation identity, mobile session, invitation, password verification, account recovery, and machine credential identity required by Product Authority.

Platform authority remains a separate explicit path.

## Canonical human Tenant authority

```text
User
→ active Membership in selected Tenant
→ effective Team Membership
→ active Team
→ effective Team Access Profile Assignment
→ active Access Profile
→ published Access Profile Revision
→ Access Grants
→ Permission
→ module-owned action guards
```

There is no Role entity, direct User Access Profile assignment, or direct Membership Access Profile assignment.

Permissions are Tenant-wide capabilities. Functional Location, Process Unit, Asset, Inventory, or responsibility relationships do not create hidden resource-scope authority.

## Canonical Integration Principal authority

```text
Integration Principal
→ active machine identity in one Tenant
→ active machine credential
→ explicit machine Permission
→ Data Source authorization where required
→ module-owned source and subject guards
```

An Integration Principal is not a User and has no Membership or Team authority path.

For Alpha Process Reading ingress, authorization must verify at least:

- active Tenant and principal;
- machine Permission for Reading submission;
- active Data Source authority;
- active Measurement Point;
- active Measurement Source Mapping between Data Source and Measurement Point;
- input and unit contract;
- any source-specific limits.

Machine authority must not imply Process Condition mutation, Maintenance actions, Inventory posting, Reliability Decisions, or Tenant administration unless separate Product Authority explicitly grants such a machine action.

## Canonical platform authority

```text
platform User
→ authenticated platform session
→ explicit platform Permission
→ platform-owned Application command
```

A platform command never becomes ordinary Tenant work and never derives Tenant Permission from platform authority.

When platform action affects one Tenant, Tenant scope, Actor, reason, operation identity, before/after evidence, and outcome remain explicit and audited.

## Permission design

Permissions are atomic, module-qualified, allow-only capability identities.

They do not encode lifecycle state, responsibility, data-source mapping, separation-of-duty expressions, or arbitrary resource filters. Those guards remain with the owning action.

## Assignment and history

Human authority is assigned through Team Access Profile Assignments with effective periods and revocation evidence. Several effective assignments compose by union of allowed Permissions.

Integration Principal authority uses its own explicit machine grant path and never shares Team assignment semantics.

Every evaluation uses durable effective facts. Ended or revoked grants remain history but cannot authorize future action.

## Internal system actions

A Waiotech-owned Worker may use only one explicit product-defined internal-system action for the exact job being performed.

Infrastructure identity alone grants no Product Permission and must not become a generic system superuser.

## Related documents

- [Integration Principals](../../10-product/20-identity-and-access/040-integration-principals.md)
- [Authorization enforcement](020-authorization-enforcement.md)
- [Authentication, sessions, and secrets](030-authentication-sessions-and-secrets.md)
