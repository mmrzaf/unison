# Tenant and operational scope

A Tenant is one independently governed wastewater plant and Waiotech's highest boundary for operational data, configuration, security, administration, time interpretation, and isolation.

## Use Tenant as the plant boundary

Waiotech must use Tenant as the only top-level operating scope.

A Tenant represents one wastewater plant or one independently governed wastewater operating installation. Waiotech does not model a subordinate Site concept inside a Tenant.

Every Tenant-owned product entity belongs to exactly one Tenant. Ordinary product relationships, authority, operational activity, process data, work, Inventory, reliability evidence, and configuration must not cross Tenant boundaries.

Tenant is not a commercial subscription, billing account, or legal-customer model. Commercial account concepts require separate Product Authority.

## Distinguish operational and learning Tenants explicitly

Every Tenant has exactly one immutable purpose:

- `operational`;
- `learning`.

An operational Tenant represents a real plant. A learning Tenant is a private synthetic plant used only for guided learning and practice.

An operational Tenant cannot become a learning Tenant, and a learning Tenant cannot become an operational Tenant. Operational records are not promoted into learning Tenants and learning records are never promoted into operational authority.

Learning Tenants are excluded from real external integrations, operational communication, cross-Tenant operational reporting, support queues that represent live plant work, and other externally consequential behavior. Guided learning may simulate those experiences without creating real consequences.

## Keep one authoritative Tenant time zone

Tenant owns one authoritative operational time zone.

Business evidence must preserve an unambiguous instant. Operational dates, due states, schedules, occurrence windows, readings, observations, failures, and histories are interpreted and displayed using the Tenant time zone unless a user explicitly selects another display context.

A user display preference must not change authoritative operational time meaning.

## Keep Tenant-wide governance explicit

Tenant remains the ordinary scope for:

- Membership and Team participation;
- Access Profiles and Permissions;
- Tenant Control Policy;
- plant configuration;
- Process, Maintenance, Inventory, and Reliability authority;
- operational Reports and Notifications;
- Tenant export, preservation, and destruction.

Plant structure does not create hidden security scopes. Functional Location, Process Unit, Asset, or Inventory relationships must not grant authority by themselves.

## Related documents

- [Domain and plant model](020-domain-and-plant-model.md)
- [Tenant lifecycle](../70-tenant-lifecycle/010-tenant-lifecycle.md)
- [Learning environment](../../30-experience/070-learning-environment.md)
