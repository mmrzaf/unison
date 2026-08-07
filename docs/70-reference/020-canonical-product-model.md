# Canonical product model

This reference consolidates the authoritative Waiotech V1 product model. Owning Product Authority remains controlling when a summary is incomplete.

## Product surfaces

```text
Waiotech
├── Server and workers
├── Tenant Dashboard
├── Platform Admin
├── Android Work App
├── Public Website
└── Documentation and Help Center
```

One Tenant represents one wastewater plant.

## Top-level Tenant model

```text
Tenant
├── IAM and policy
├── Plant Model
├── Process Operations
├── Maintenance
├── Inventory
├── Reliability
├── Evidence and communications
└── Tenant lifecycle and portability
```

## Plant Model

```text
Plant Model
├── Functional Location
│   └── child Functional Location   effective containment history where required
│
├── Asset
│   └── child Asset            physical composition with effective history
│
├── Asset Installation
│   ├── directly installed root Asset
│   └── Functional Location
│       composed child Assets derive installed context through Asset composition
│
├── Asset Classification
├── Operational Criticality    Functional Location context
│
├── Process Unit
│   └── child Process Unit     effective process-decomposition history where required
│
├── Process Stream
│   ├── governed process medium
│   ├── upstream Process Unit or plant boundary
│   └── downstream Process Unit or plant boundary
│
├── Functional Location serves Process Unit
│
├── Measurement Point
│   ├── measured quantity
│   ├── canonical unit
│   ├── subject
│   │   ├── Functional Location
│   │   ├── Asset
│   │   ├── Process Unit
│   │   └── Process Stream
│   ├── optional instrument Asset
│   └── Measurement Source Mappings
│
├── Data Source
├── Measurement Source Mapping
│   ├── effective Data Source signal → Measurement Point relationship
│   └── at most one active machine-ingress mapping per Measurement Point
└── External Identifier Mapping
```

Functional Location, Asset, Process Unit, Process Stream, and Measurement Point are distinct identities. Waiotech does not use one universal Plant Object aggregate.

Each has a required name and may have an optional plant code unique case-insensitively within the Tenant and that entity category. Code history remains separate from immutable identity.

## Process Operations

```text
Process Operations
├── Process Reading
│   ├── Measurement Point
│   ├── reported value and unit
│   ├── canonical normalized value where required
│   ├── effective time
│   ├── recorded time
│   ├── Actor
│   ├── provenance / Data Source
│   ├── quality
│   └── additive correction
│
├── Operational Observation
│   ├── exactly one primary Process Unit / Process Stream / Functional Location / Asset
│   ├── narrative evidence
│   └── related Readings / Measurement Points / plant context / attachments
│
├── Process Routine
│   └── immutable Process Routine Revision
│       └── Reading / Observation collection entries
│
├── Process Round
│   ├── scheduled occurrence or ad-hoc execution
│   └── links to canonical Readings / Observations
│
├── Process Condition
│   ├── Process Unit or Process Stream
│   ├── active / monitoring / resolved
│   ├── routine / elevated / urgent / critical attention
│   ├── owner Team
│   ├── optional responsible User
│   ├── evidence
│   └── resolution
│
├── Operational Action
│   ├── exactly one primary Process Unit or Process Stream
│   └── optional Process Condition
├── Outcome Assessment
│   ├── exactly one Operational Action
│   └── improved / no_meaningful_change / worsened / inconclusive
└── Process Material Usage
```

Human Reading entry is first-class. Machine submission uses Integration Principal and Data Source authority but creates the same Process Reading concept.

## Maintenance

```text
Maintenance
├── Work Request
│   └── Triage Decision
│
├── Finding
│   └── explicit disposition
│
├── Procedure
│   └── Procedure Revision
│
├── Maintenance Plan
│   └── Maintenance Plan Revision
│       ├── Functional Location or Asset target kind
│       ├── immutable resolved targets
│       ├── calendar or Measurement Point trigger
│       └── per-target trigger Measurement Point binding when applicable
│
├── Scheduled Work
│
└── Work Order
    ├── Work Target
    │   ├── Functional Location
    │   └── Asset
    ├── Tasks
    ├── Work Requirements
    ├── responsibility
    ├── Readiness projections
    ├── Maintenance Material Requirement / Usage
    ├── execution evidence
    ├── completion submission
    ├── Verification Decisions
    └── closeout
```

Work Order is the only representation of accepted maintenance work.

Process Conditions and Failure Events may originate or support Maintenance work but remain source-domain authority.

## Inventory

```text
Inventory
├── Item
│   ├── Item Category
│   ├── Item Policy
│   └── allowed Unit Conversions
│
├── Stock Location
├── Inventory Movement
├── Stock Balance              derived
├── Reservation
├── availability               derived
├── Replenishment Request
├── Stocktaking Run
├── Maintenance Material Usage relationship
└── Process Material Usage relationship
```

Inventory owns physical custody. The consuming domain owns why material was required or used.

## Reliability

```text
Reliability
├── Failure Event
│   ├── primary Functional Location or Asset
│   ├── required function
│   ├── failure mode
│   ├── occurrence and detection timing
│   ├── operational condition
│   ├── investigation condition
│   ├── consequence
│   ├── restoration history
│   ├── Process evidence
│   ├── supporting Findings
│   ├── linked Work Requests / Work Orders
│   ├── Cause Assessments
│   └── recurrence / duplicate relationships
│
└── reliability analytics      derived
```

Failure Event records what failed and its consequence. Work Order records maintenance response. Process Condition records operational handling. They remain distinct.

## IAM

```text
User
└── Membership
    └── Team Membership
        └── Team
            └── Team Access Profile Assignment
                └── Access Profile
                    └── published Access Profile Revision
                        └── Access Grant
                            └── Permission

Integration Principal
├── Tenant
├── explicit machine Permission
├── machine credential history
└── authorized Data Source relationship

platform User
└── explicit platform Permission
```

Human, machine, internal-system, and platform authority paths are separate.

## Policy and Decisions

```text
Tenant Control Policy
└── immutable published Tenant Control Policy Revision

Domain-owned Decisions
├── triage
├── verification
├── waiver
├── stocktaking approval
├── Failure Event investigation requirement
└── other explicitly defined governed judgments
```

Waiotech does not use generic resource-scoped policy or one universal Decision lifecycle.

## Evidence and communications

```text
Attachment
Report Type / Report Run
Notification
Audit access and operational history projections
```

Accepted evidence remains attributable, historically interpretable, and bounded by owning-domain subject relationships.

## Tenant portability

Tenant export and import preserve canonical identities, relationships, source history, installation history, Process evidence, Maintenance, Inventory, Reliability, files, authority evidence, and required integrity metadata according to portability Product Authority.

## Explicitly excluded generic concepts

The canonical model excludes:

- Site inside Tenant;
- Unit / Equipment / Component as mandatory Asset structural types;
- generic Plant Object;
- generic Entity Relationship authority;
- generic Measurement or Event aggregate spanning all domains;
- generic Activity or Workflow engine;
- generic Condition engine;
- arbitrary policy scope;
- generic dashboard builder;
- SCADA control or historian replacement.

## High-level model

```text
                              TENANT
                           one plant
                               │
                         ┌─────┴─────┐
                         │ PLANT MODEL│
                         └─────┬─────┘
                               │
       ┌───────────────────────┼────────────────────────┐
       │                       │                        │
Functional Locations         Assets              Process Model
stable plant positions    physical objects       ├─ Process Units
       │                       │                  ├─ Process Streams
       │                       │                  └─ Measurement Points
       └───────────────┬───────┘
                       │
        ┌──────────────┼──────────────────────────────┐
        │              │                              │
     PROCESS        MAINTENANCE                   INVENTORY
 plant operation    equipment work             material custody
        │              │                              │
        └──────────────┼──────────────────────────────┘
                       │
                  RELIABILITY
          failure, restoration, learning
```

## Related documents

- [Canonical terminology](010-canonical-terminology.md)
- [Lifecycle summary](030-lifecycle-summary.md)
- [Domain and plant model](../10-product/10-foundations/020-domain-and-plant-model.md)
