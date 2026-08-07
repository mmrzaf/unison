# Product surfaces

Waiotech provides a Public Website, Tenant Dashboard, Platform Admin, Android Work App, and Documentation and Help Center. Each surface has a distinct audience, trust boundary, and responsibility while exposing one Product Authority.

## One product, distinct interaction surfaces

Waiotech must not reproduce the same application at different screen sizes or create alternate authority paths for different surfaces.

The canonical user-facing surfaces are:

- **Public Website:** public understanding, trust, discovery, and product entry;
- **Tenant Dashboard:** the primary professional operating workspace for the plant;
- **Android Work App:** the governed field workspace for Process and Maintenance work;
- **Platform Admin:** protected platform provisioning, support, preservation, and recovery;
- **Documentation and Help Center:** contextual guidance embedded in authenticated work, with selected public and field guidance where appropriate.

A private learning Tenant may expose real product behavior with synthetic data under the same Product Authority. Learning purpose changes side-effect and reset policy, not domain meaning.

## Tenant Dashboard

Tenant Dashboard owns the complete browser experience for ordinary plant operation and configuration, including:

- Plant exploration and configuration;
- Process Operations;
- Maintenance planning, control, and analysis;
- Inventory operation and administration;
- Reliability work and analysis;
- Reports and operational history;
- Users, Teams, Permissions, policy, Data Sources, Integration Principals, and other Tenant-owned settings;
- Documentation and Help Center.

The Dashboard must feel like an operating workspace, not a collection of database administration screens.

## Android Work App

Android Work App owns focused field interaction where mobility, scanning, evidence capture, and intermittent connectivity matter.

It supports:

- My Work across authorized Process and Maintenance obligations;
- human Process Readings and Operational Observations;
- bounded Process Condition and Operational Action interaction;
- Work Order execution;
- Findings and field evidence;
- contextual Inventory interactions;
- Plant identification and context;
- governed offline authority through explicit field packages.

Broad configuration, publication, planning, access administration, Data Source configuration, Integration Principal configuration, and unrestricted reporting remain Dashboard responsibilities.

## Platform Admin

Platform Admin owns only platform authority.

It may provision and suspend Tenants, perform governed support and preservation actions, inspect platform-owned operational state, and invoke protected recovery contracts. It must not become a privileged plant-operations interface and must not provide a generic bypass around Tenant authority.

## Public Website

Public Website explains Waiotech truthfully in English and Persian and provides public product, trust, legal, support, and product-entry content. It performs no authenticated Tenant operation.

## Documentation and Help Center

Documentation is part of the product experience. Guidance should be reachable from the context where a User needs it and should explain product meaning, decisions, and safe workflows rather than mirror code or database structure.

## Surface boundary rules

Across all surfaces:

- the Server remains authoritative for product state, permissions, lifecycle, validation, and available actions;
- surface-specific convenience must not create alternate domain meaning;
- a User never gains authority because a control is visible;
- field or offline authority is explicit, bounded, attributable, and revocable;
- public information cannot expose Tenant data or protected product authority;
- Platform Admin cannot impersonate an ordinary Tenant Actor;
- English and Persian experiences preserve equivalent meaning.

## Related documents

- [Public Website](020-public-website.md)
- [Tenant Dashboard and Platform Admin](030-tenant-dashboard-and-platform-admin.md)
- [Android Work App](040-android-work-app.md)
- [Offline field packages](050-offline-field-packages.md)
- [Tenant Dashboard experience](080-tenant-dashboard-experience.md)
