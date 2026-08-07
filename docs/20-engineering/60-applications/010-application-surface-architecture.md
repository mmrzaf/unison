# Application surface architecture

Waiotech uses distinct Public Website, Tenant Dashboard, Platform Admin, and Android Work App surfaces over one authoritative Server. Each surface serves a different interaction context without becoming an alternate product authority.

## Tenant Dashboard

Tenant Dashboard is the complete browser workspace for one Tenant plant.

Its principal feature areas align with user experience rather than route-module names:

```text
Home
My Work
Plant
Process
Maintenance
Inventory
Reliability
Reports
Settings
```

The Dashboard uses generated API contracts, purpose-specific read models, Server-provided action availability, URL-backed workspace state where appropriate, and contextual cross-domain navigation.

It must not become a generic entity CRUD renderer.

## Platform Admin

Platform Admin uses a separately generated client and explicit platform routes.

It performs only platform-owned provisioning, lifecycle, recovery, preservation, and support functions. It must not reuse Tenant Dashboard mutation components to bypass Tenant authority.

## Android Work App

Android is the field surface for governed Process and Maintenance work.

It supports human Process Readings and Observations, bounded Process Condition interaction, Maintenance execution, evidence capture, scanning, and explicitly authorized Inventory interactions. Offline behavior requires governed package authority.

Android does not expose broad configuration, Plan publication, Data Source configuration, Integration Principal administration, or Platform Admin actions.

## Public Website

Public Website remains static-first, bilingual, public-safe, and outside authenticated Tenant operations.

## One Server authority

All application surfaces rely on the same Product Authority and Application commands. Differences in presentation or connectivity do not create alternate lifecycle, authorization, evidence, or correction rules.

## Related documents

- [Browser application contracts](060-browser-application-contracts.md)
- [Tenant Dashboard experience](../../30-experience/080-tenant-dashboard-experience.md)
- [Android Work App](../../30-experience/040-android-work-app.md)
