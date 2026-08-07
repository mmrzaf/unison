# Users, Memberships, and Teams

Users, Memberships, and Teams define attributable human participation and responsibility without creating alternate authority paths.

## A User

A User is one platform-wide human identity. The User owns authentication and
account state but does not by itself grant access to any Tenant.

## A Membership

A Membership represents one User's participation in one Tenant. A User may have
Memberships in several Tenants. Every ordinary Tenant action uses exactly one
active Membership in the selected Tenant.

Membership is the exclusive human participation boundary. Waiotech does not use
support sessions, impersonation, shared human identities, or alternate Tenant
actor paths.

A Membership preserves Tenant, User, lifecycle, effective periods, reason,
history, and action attribution. Ending a Membership removes ordinary future
Tenant authority without deleting historical identity or evidence.

## A Team

A Team is a stable Tenant-owned group used for operational responsibility and
human authority assignment. Team Membership records which Memberships
participate and during which effective period.

Access Profile authority is assigned to Teams through explicit, time-bounded
Team Access Profile Assignments. Users and Memberships do not receive Access
Profiles directly.

## Human authorization

Human authorization follows:

```text
User
-> active Membership in selected Tenant
-> effective Team Membership
-> active Team
-> effective Team Access Profile Assignment
-> published Access Profile Revision
-> Access Grants
-> Permission
-> action-specific product guards
```

Responsibility and authorization remain separate. Owning or being assigned work
does not automatically grant Permission, and Permission does not automatically
assign responsibility.

## Browser onboarding boundary

User invitation, Membership establishment, credential setup, expiry, resend,
and recovery require complete governed contracts. No application may invent
those workflows or present local success before the Server accepts them.

## Related documents
- [Permissions and Access Profiles](020-permissions-and-access-profiles.md)
- [Responsibility and actors](030-responsibility-and-actors.md)
- [Authorization architecture](../../20-engineering/40-security/010-authorization-architecture.md)
