# Integration Principals

An Integration Principal is a Tenant-owned machine Actor used for narrowly authorized external automation. It supplements human operation and never inherits ordinary User authority.

## An Integration Principal

An Integration Principal has:

- immutable identity;
- Tenant;
- name and operational description;
- active or revoked state;
- explicit machine Permissions;
- attributable credential history managed by Engineering Authority;
- optional association with one or more governed Data Sources;
- creation, revocation, and correction evidence.

Integration Principal identity is not a User, Membership, Team, service-account impersonation, or platform User.

## Keep machine authority narrow

Machine authority must be allow-only and explicit.

Alpha must support the machine authority required to submit Process Readings for configured Measurement Points through authorized Data Sources. Additional machine-write capabilities require explicit Product Authority in the owning domain.

An Integration Principal must not acquire Maintenance, Inventory, Process Condition, Reliability, or Tenant-administration authority merely because it can submit Readings.

## Bind submitted evidence to source authority

A machine-submitted Process Reading must identify the Integration Principal and the Data Source authority under which the Reading was accepted.

The principal may submit only for Measurement Points whose active Measurement Source Mapping permits that Data Source and whose lifecycle permits new Readings.

Source authorization is re-evaluated for every accepted submission. A previously valid credential or mapping must not grant authority after revocation or retirement.

## Preserve machine attribution and revocation history

Revocation prevents future machine actions but does not rewrite previously accepted evidence.

Historical Readings continue to identify the Integration Principal and Data Source that submitted them.

Credential replacement, secret rotation, and transport authentication mechanics belong to Engineering Authority and must preserve this product identity.

## Related documents

- [Responsibility and actors](030-responsibility-and-actors.md)
- [External identifiers and Data Sources](../30-plant/060-external-identifiers-and-data-sources.md)
- [Process Readings and Observations](../35-process/010-readings-and-observations.md)
