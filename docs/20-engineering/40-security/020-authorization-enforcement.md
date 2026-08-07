# Authorization enforcement

Authorization is enforced in the Application layer, fails closed, preserves Actor type and separation of duties, and cannot be replaced by route guards, hidden controls, Workers, stale caches, or platform role names.

## Human Tenant authority

Ordinary human Tenant actions use an authenticated User acting through one active Membership in the selected Tenant.

Effective authority is derived from active Team Memberships, effective Access Profile assignments, published revisions, exact Permissions, Tenant condition, responsibility, and action-specific product guards.

A platform User does not gain Tenant Permission from platform authority. Waiotech does not use support sessions, hidden impersonation, or alternate Tenant Actor paths.

## Machine Tenant authority

A machine request acts through one active Tenant-bound Integration Principal.

Integration Principal authority is deliberately narrower than human Membership authority. The backend evaluates:

- active Integration Principal;
- exact machine Permission;
- Tenant condition;
- authorized Data Source;
- Measurement Source Mapping and lifecycle;
- source-scoped restrictions;
- request authenticity, replay protection, idempotency, and rate limits;
- command-specific validation.

Alpha machine authority exists for explicitly governed Process Reading ingestion and any other action named by Product Authority. It cannot inherit Team Membership, Access Profiles intended for Users, broad Dashboard authority, or generic Tenant mutation capability.

A Data Source identifies provenance. It is not an Actor and cannot authorize a request by itself.

## Platform authority

Platform Admin commands use explicit platform-owned actions and dedicated Application use cases.

A platform command may manage platform facts or invoke a narrow owning-module recovery interface, but it must not enter ordinary Tenant workflows or mutate Tenant records through a generic bypass.

Every privileged platform action records the real Actor, action, reason, scope, correlation, outcome, and affected Tenant where applicable.

## Backend enforcement

Backend Application services evaluate authenticated identity, Actor type, Tenant scope, lifecycle, authority, separation of duties, Decisions, and command-specific guards inside the owning use case.

Routes and clients assist usability only. Every protected command and query is authorized by the backend.

## Asynchronous execution

A Worker preserves the initiating Actor and authority basis when processing work on behalf of a command.

A Waiotech-owned system action uses an explicit product-defined system Actor and exact job contract. Infrastructure trust alone never grants product Permission.

A Worker must not upgrade an Integration Principal, User, or system action into broader authority than the initiating contract permits.

## Separation of duties

The owning command evaluates both authority and independence.

Different Team names do not make the same User independent. Decisions, authorship, execution, verification, and prior Actor relationships are evaluated from preserved facts. Machine Actors do not satisfy a human-independent-verification requirement unless Product Authority explicitly defines such a machine decision.

## Caching and failure

Authority may be cached only with Tenant-scoped keys, explicit authoritative sources, complete invalidation triggers, bounded maximum staleness, and fail-closed behavior.

Invalidation covers applicable User or Membership condition, Team Membership, Team condition, Access Profile assignment and publication, Permission catalogue, browser sessions, Android installation and mobile sessions, offline field packages, Integration Principal condition and credentials, Data Source authorization, and affected source mappings.

High-impact actions and machine-ingress checks may require direct authoritative evaluation.

When required identity, Tenant, authority, source, or security data cannot be established, the action is denied. Missing context, unavailable data, and expired cache never produce a permissive fallback.

## Related documents

- [Responsibility and actors](../../10-product/20-identity-and-access/030-responsibility-and-actors.md)
- [Integration Principals](../../10-product/20-identity-and-access/040-integration-principals.md)
- [Authorization architecture](010-authorization-architecture.md)
- [Authentication, sessions, and secrets](030-authentication-sessions-and-secrets.md)
