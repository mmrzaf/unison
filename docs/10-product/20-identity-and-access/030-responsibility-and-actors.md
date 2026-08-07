# Responsibility and actors

Waiotech distinguishes identity, authority, operational responsibility, and attribution. Human Users and machine Integration Principals are both Actors, but their authority paths and permitted responsibilities remain explicit.

## Users and Memberships

A User is a person identity. A Membership places that User inside one Tenant.

Human operational actions require an active Membership in the effective Tenant and the Permissions and domain guards required by the action.

## Teams and responsibility

A Team is the ordinary durable unit for operational ownership and responsibility.

Domains may define explicitly named responsibility relationships such as owner Team, responsible User, planner, verifier, stock custodian, Process Condition owner, or investigator. A relationship records business responsibility; it does not grant Permission by itself.

Team membership likewise does not make every Team responsibility an authorization rule. Authorization remains derived through the IAM grant model.

## Actor attribution

Every consequential accepted action must preserve an attributable Actor.

Canonical Actor categories are:

- human User acting through an active Membership;
- Integration Principal acting through its machine authority;
- product-defined internal system Actor for an exact Waiotech-owned job;
- platform User acting through a platform-owned command.

The Actor category, effective Tenant, command or accepted event, time, reason where required, and causation must remain distinguishable in audit evidence.

A machine or system Actor must never be presented as though a human performed the action.

## Keep responsibility separate from source provenance

A Process Reading may be entered by a User or submitted by an Integration Principal. Its recording Actor does not by itself establish operational responsibility for a resulting Process Condition.

A Process Condition has explicit accountable responsibility independent of who first recorded evidence.

Likewise, a Work Order requester, assignee, executor, verifier, and approving Actor may be different people or Teams according to their owning Maintenance rules.

## Platform operation

Platform authority remains separate from Tenant authority.

A platform User may perform only explicitly platform-owned commands. Platform support cannot silently become an ordinary Tenant Actor and cannot impersonate a Tenant User.

## Separation of duties

Where Product Authority requires independent approval, verification, or review, the action must evaluate the actual Actor and relevant prior Actors. Team membership, machine identity, or platform authority must not bypass separation-of-duty rules.

## Related documents

- [Users, Memberships, and Teams](010-users-memberships-and-teams.md)
- [Permissions and Access Profiles](020-permissions-and-access-profiles.md)
- [Integration Principals](040-integration-principals.md)
