# Canonical terminology

Canonical terminology is used consistently across Product Authority, Engineering Authority, Experience Contracts, APIs, user interfaces, Reports, help, and generated references. A familiar plant synonym may appear as explanatory copy, but it must not create alternate product meaning.

## Tenant

A **Tenant** is one independently governed wastewater plant and Waiotech's hard operational, security, configuration, and data-isolation boundary.

Waiotech has no subordinate Site concept.

Tenant is not a billing account or generic SaaS organization concept.

## Plant Model

The **Plant Model** is Waiotech's canonical structural understanding of one Tenant plant.

It contains distinct connected concepts:

- Functional Location;
- Asset;
- Asset installation;
- Asset Classification;
- Operational Criticality;
- Process Unit;
- Process Stream;
- Measurement Point;
- Data Source and external mappings.

Plant Model is not one universal hierarchy and not a generic Plant Object graph.

## Plant code

A **Plant code** is an optional human-facing operational identifier on a Functional Location, Asset, Process Unit, Process Stream, or Measurement Point. It is unique case-insensitively within the Tenant and that entity category.

Code changes are explicit and history-preserving. A used code is not silently reassigned to a different retained entity in the same category.

## Functional Location

A **Functional Location** is a stable installed position, structure, or maintainable place whose identity may continue while physical Assets are installed, removed, or replaced.

Functional Location hierarchy expresses stable installed containment. Hierarchy changes preserve effective history where retained evidence depends on prior containment.

Use Functional Location for the stable plant position. Do not use the generic capitalized term `Location` as a competing canonical entity.

## Asset

An **Asset** is an individually identified physical object or assembly whose own lifecycle, maintenance, reliability, or installation history matters.

Asset parentage means physical composition only. Composition changes preserve effective history, and a composed child derives installed Functional Location through its containing Asset rather than carrying a competing direct installation.

`Unit`, `Equipment`, and `Component` are not canonical structural Asset types. The lowercase words may appear in ordinary language, `Process Unit` is a distinct canonical concept, and `unit` may refer to a measurement unit where context is clear.

## Asset installation

**Asset installation** is the historical relationship placing one physical Asset at one Functional Location for an effective period.

Installation is not Asset identity and is not represented only by overwriting a current location field.

## Asset Classification

**Asset Classification** describes what a physical Asset technically is through governed taxonomy.

Classification does not determine physical hierarchy, Functional Location, or Process Unit membership.

## Operational Criticality

**Operational Criticality** represents the potential plant consequence of losing the stable function at a Functional Location.

Operational Criticality is assigned to Functional Location, not to the replaceable physical Asset. An installed Asset may display the criticality of its Functional Location as context.

Operational Criticality is separate from actual Failure Event consequence, Process Condition attention, Work Order urgency, priority, execution risk, and deferral consequence.

## Process Unit

A **Process Unit** is a stable functional boundary in which a defined part of the wastewater process occurs. Process hierarchy changes preserve effective history where retained evidence depends on prior decomposition.

Examples include screening, aeration train, secondary clarification, sludge dewatering, and disinfection.

Process Unit is separate from Functional Location and Asset.

## Process Stream

A **Process Stream** is a stable directional logical flow of process medium between Process Units or between a Process Unit and the modelled plant boundary.

Stream medium and directional endpoints define stable operational meaning after retained use; materially changing that meaning requires retirement and a new Stream identity.

A Process Stream is not a physical pipe or channel.

## Measurement Point

A **Measurement Point** is the stable semantic identity of an operational quantity that Waiotech understands can be observed on one supported plant subject.

It defines quantity, canonical unit, subject, and optional instrument/source relationships.

Measurement Point is not a Reading and is not an external SCADA tag. Subject and measured quantity define semantic identity after operational use; instrument and machine-source relationships may change with preserved effective history.

## Data Source

A **Data Source** is one governed external source or namespace relevant to plant data.

Data Source identifies where machine evidence comes from. It is not the machine Actor.

## Measurement Source Mapping

A **Measurement Source Mapping** is the governed effective relationship that maps one Data Source signal or key to one Measurement Point for machine Process Reading ingestion.

It is distinct from a general External Identifier Mapping and does not replace Measurement Point identity. Alpha permits at most one active machine-ingress mapping per Measurement Point at one effective instant.

## External identifier mapping

An **external identifier mapping** associates a source-scoped external identifier with one canonical supported Plant Model object while preserving canonical Waiotech identity.

## Process Reading

A **Process Reading** is immutable structured Process evidence that one value was observed for one Measurement Point.

It preserves reported and canonical unit meaning, effective time, recorded time, Actor, source/provenance, and quality.

Use `Reading` in Process context where unambiguous. Do not use `meter reading` as a separate Maintenance authority.

## Reading quality

Canonical Reading quality values are:

- `good`;
- `uncertain`;
- `bad`;
- `unknown`.

Quality describes confidence in the value, not whether the value is operationally desirable.

## Operational Observation

An **Operational Observation** is immutable qualitative or contextual Process evidence about plant operation.

Observation is distinct from Process Reading and Process Condition.

## Process Routine

A **Process Routine** is the stable identity of a reusable human operating round or log-sheet definition.

A **Process Routine Revision** is immutable published content defining ordered Reading and Observation collection entries and optional calendar recurrence. One effective published revision governs subsequent Round creation for an active Routine.

## Process Round

A **Process Round** is the accountable execution record for one Process Routine Revision at one scheduled occurrence or authorized ad-hoc start. Readings and Observations created through it remain their own canonical Process evidence.

## Process Condition

A **Process Condition** is a meaningful operational situation concerning one Process Unit or Process Stream that requires active handling, continued monitoring, or explicit resolution.

A Process Condition is not every alarm, abnormal Reading, defect, Finding, or Failure Event.

## Process attention

**Process attention** describes the required operational response intensity and timeliness for one active or monitoring Process Condition.

Canonical values are `routine`, `elevated`, `urgent`, and `critical`.

Process attention is distinct from Failure Event consequence, Maintenance urgency, Maintenance priority, Operational Criticality, and Reading quality.

## Operational Action

An **Operational Action** is immutable evidence of what Operations deliberately changed or did in response to plant context or a Process Condition.

It is not physical maintenance work merely because equipment participates.

## Outcome Assessment

An **Outcome Assessment** is immutable Process evidence of the observed operational result attributable to one Operational Action.

Canonical values are `improved`, `no_meaningful_change`, `worsened`, and `inconclusive`.

Outcome Assessment is separate from Process Condition resolution.

## Integration Principal

An **Integration Principal** is a Tenant-owned machine Actor with narrowly granted external automation authority.

It is not a User, Membership, Team, platform User, or Data Source.

## Actor

An **Actor** is the attributable identity under which a consequential accepted action occurred.

Canonical Actor categories include human User, Integration Principal, exact internal system Actor, and platform User acting through their separate authority paths.

## Work Request

A **Work Request** is an unaccepted proposed maintenance need subject to accountable triage.

It may originate from a person, Process Condition, Finding, Failure Event, external evidence, or another governed workflow.

## Accepted maintenance need

An **accepted maintenance need** is represented by one or more Work Orders. There is no second generic accepted-maintenance aggregate.

## Work Order

A **Work Order** is the authoritative record of accepted maintenance work and its planning, execution, completion, and maintenance verification.

## Work Target

A **Work Target** is the one primary canonical subject of a Work Order before release or emergency start.

Supported Work Target types are:

- Functional Location;
- Asset.

## Finding

A **Finding** is an independently identifiable maintainable condition discovered through Maintenance, inspection, completion, verification, or another governed evidence context and requiring explicit Maintenance disposition.

Finding is not automatically a Failure Event or Work Order.

## Failure Event

A **Failure Event** is the authoritative Reliability record of actual loss or materially unacceptable degradation of required function.

Its primary target is one Functional Location or Asset.

Failure Event is separate from Process Condition, Finding, Work Request, and Work Order.

## Cause Assessment

A **Cause Assessment** is immutable Reliability evidence of one accepted conclusion about why a Failure Event occurred. It may be confirmed, probable, contributory, inconclusive, or no-cause-found according to evidence.

## Procedure

A **Procedure** is the stable identity of a reusable governed maintenance method.

A **Procedure Revision** is immutable published method content.

## Maintenance Plan

A **Maintenance Plan** is the stable identity of governed recurring maintenance intent.

A **Maintenance Plan Revision** is immutable published policy defining exact targets, trigger, method, timing, and generation defaults.

## Measurement Point trigger

A **Measurement Point trigger** is a deterministic Maintenance Plan trigger based on accepted Process Readings and a governed threshold sequence. Each resolved Plan target preserves exactly one compatible trigger Measurement Point bound immutably by the published Plan Revision.

Do not use `meter` as a separate canonical Maintenance source concept.

## Scheduled Work

**Scheduled Work** is the durable identity of one recognized Maintenance Plan occurrence for one resolved target and one nominal trigger instance.

It is not executable maintenance; the resulting Work Order owns execution.

## Readiness

**Readiness** is a derived evaluation of whether one named Work Order action can proceed under current authoritative facts.

Readiness is not a Work Order lifecycle state.

## Prepared and released

**Prepared** means the Work Order has reached the defined planning state required before release evaluation.

**Released** means execution has been authorized according to the governing Maintenance rules.

Neither term is interchangeable with Readiness.

## Priority and urgency

**Urgency** describes time sensitivity or immediacy of response.

**Priority** orders work relative to other work under the product's prioritization rules.

They are distinct.

## Execution risk

**Execution risk** describes hazards and consequences of performing a specific activity in its execution context.

It is distinct from Operational Criticality and deferral consequence.

## Deferral consequence

**Deferral consequence** describes the consequence of delaying a specific maintenance obligation.

## Inventory Item

An **Item** is a Tenant-owned material identity controlled by Inventory.

## Stock Location

A **Stock Location** is an Inventory-controlled custody node.

It is distinct from Functional Location even when it optionally references one for physical placement.

## Inventory Movement

An **Inventory Movement** is an immutable posted custody fact changing tracked stock quantity or condition according to Inventory rules.

## Reservation

A **Reservation** protects a quantity of an Item at a Stock Location for an authorized demand without moving physical stock.

## Material usage

The consuming domain owns why material was used; Inventory owns physical custody.

Canonical usage concepts include Maintenance Material Usage and Process Material Usage where defined.

## Team, Permission, Access Profile, and Access Grant

A **Team** is a Tenant-owned durable group used for responsibility and human authority assignment.

A **Permission** is an atomic allow-only capability identity.

An **Access Profile** is a stable Tenant-owned collection of human authority backed by immutable Access Profile Revisions.

An **Access Grant** is one Permission included in an Access Profile Revision.

A **Team Access Profile Assignment** is the ordinary human path assigning one Access Profile to one Team for an effective period.

Waiotech has no canonical `Role` entity.

## Decision

**Decision** is immutable evidence of one governed judgment owned by its business context. Waiotech does not force all Decisions into one generic polymorphic aggregate.

## Attachment and evidence

An **Attachment** is governed accepted file evidence with immutable accepted bytes, classification, source, subject relationship, and lifecycle according to evidence Product Authority.

## Tenant Dashboard and Platform Admin

**Tenant Dashboard** is the professional operating workspace of one plant.

**Platform Admin** is the separate platform-control application and must not become an alternate Tenant authority path.

## Android Work App

**Android Work App** is the governed field surface for supported Process and Maintenance workflows and bounded offline authority.

## Related documents

- [Canonical product model](020-canonical-product-model.md)
- [Lifecycle summary](030-lifecycle-summary.md)
