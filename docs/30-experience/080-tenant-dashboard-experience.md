# Tenant Dashboard experience

The Waiotech Dashboard is the professional operating workspace of the plant. It helps users understand plant context, identify what requires attention, act with confidence, and verify outcomes. It must expose operational meaning rather than database structure.

## Product character

The Dashboard must feel:

- calm, clear, and dependable;
- professional without feeling cold;
- operational rather than administrative;
- structured without becoming bureaucratic;
- approachable for users who are not software specialists;
- efficient for expert users who spend hours in the product;
- useful during both routine work and urgent situations.

Warmth comes from clear language, meaningful context, visible people and equipment, useful feedback, confident workflows, and respectful information design rather than decorative effects.

## Organize experience around understand, decide, act, and verify

The dominant interaction model is:

```text
Understand
   ↓
Decide
   ↓
Act
   ↓
Verify
```

The Dashboard must not default to:

```text
List
↓
Record
↓
Generic edit form
↓
Save
```

Database entities and fields remain implementation details unless their product meaning is itself operationally important.

## Four experience modes

### Orient

Orienting surfaces answer:

- What is happening?
- What needs attention?
- What changed?
- What is blocked?
- Who owns the next action?
- What is due or overdue?

Home, My Work, domain overviews, and attention views use this mode.

Orientation must prioritize actionable meaning over passive metrics.

### Work

Operational workspaces help users process repeated records efficiently.

Examples include:

- Process Rounds;
- Process Conditions;
- Work Requests;
- Work Orders;
- Findings;
- Failure Events;
- Replenishment Requests;
- Stocktaking obligations.

High-volume workspaces should use the task-appropriate combination of search, filters, queue or table, master-detail inspection, saved or URL-backed views, and direct available actions.

Users must not be forced through repeated `list → open → back → find previous position` navigation when a master-detail or equivalent workspace can preserve context.

### Understand

An operational detail experience tells the story of one subject or record before exposing all metadata.

A detail page should normally communicate:

1. human operational identity and current state;
2. important context, consequence, and warnings;
3. current responsibility and next meaningful action;
4. operational facts and relationships;
5. related Process, Maintenance, Inventory, and Reliability records where relevant;
6. evidence and attachments;
7. chronological operational history;
8. technical metadata and identifiers.

Technical UUIDs, revision identifiers, source keys, and secondary fields remain available but do not dominate the page.

### Configure

Configuration experiences define the model and rules that operational work uses.

Examples include Functional Location structure, Asset Classification, Asset installation, Process Units and Streams, Measurement Points, Data Sources, Users, Teams, Access Profiles, Maintenance definitions, Inventory configuration, and Tenant policy.

Configuration may use denser registries and forms. Operational work must not inherit configuration-style CRUD merely because both manipulate records.

## Work before data

Screens must be organized around the work users need to complete, not around database tables or server modules.

The interface should answer:

- What needs attention?
- What should I do next?
- Why does this matter?
- What is blocking progress?
- What changed?
- Who is responsible?
- What evidence supports the current interpretation or decision?

## Server-owned business behavior

The Dashboard must not invent Permissions, lifecycle rules, available actions, state transitions, action prerequisites, validation, code generation, taxonomy, or audit behavior.

Server authority is exposed through generated contracts and action availability. The Dashboard makes those contracts understandable and efficient; it does not reproduce them as competing browser business logic.

## Connected operational context

Plant, Process, Maintenance, Inventory, and Reliability must feel connected even though their authority remains separate.

Users should begin related work from the context they are already viewing.

Examples include:

- Process Unit → record Reading or Observation;
- Process Condition → record Operational Action;
- Process Condition → request Maintenance;
- Functional Location → view installed Assets and current work;
- Asset → report Finding or create Work Request;
- Work Order → reserve, issue, or return material;
- Failure Event → inspect Process evidence and maintenance response;
- Inventory Item → inspect relevant usage and availability.

Contextual creation must carry forward known relationships and evidence rather than opening a blank form that asks users to reconstruct them.

## Plant experience

Plant is a connected explorer, not a set of unrelated registries.

Users must be able to navigate distinct but related plant dimensions:

- Functional Location hierarchy;
- Asset physical composition and installation;
- Process Unit hierarchy;
- Process Stream flow;
- Measurement Points.

The interface must not pretend these are one tree.

A selected Functional Location, Asset, Process Unit, Stream, or Measurement Point should reveal useful connected context such as current Process Conditions, relevant Readings, installed Assets, Functional Locations serving the selected Process context, open Maintenance, Failure Events, and material history where authorized.

A simple process-flow view derived from Process Units and Process Streams is part of Plant exploration. A P&ID-oriented or geographic view, when provided, is another projection over the same canonical Plant Model and must not become a separate data authority.

## Process experience

Human Process entry must be fast enough for routine plant operation.

When the user is already in a Process Unit or Measurement Point context, Waiotech should already know the Tenant, subject, Measurement Point, canonical unit, current User, and normal time default. The user should enter only the information that is genuinely unknown.

A Process workspace should prioritize:

- open Conditions;
- Process attention;
- current handling state and responsibility;
- latest relevant Readings and Observations;
- actions already taken;
- next review obligation;
- outcome;
- related Maintenance and Reliability activity.

Machine and human Readings appear in one coherent timeline and trend experience with provenance visible when useful.

Process Rounds provide a fast sequential field or control-room workflow for routine human collection. The Round keeps the operator in context, advances through required Reading and Observation entries, shows reference guidance without manufacturing alarms, and makes missing required entries obvious before completion.

Machine ingestion configuration belongs in Settings, not in ordinary operator workflow.

## Maintenance experience

Maintenance should present why work exists, whether it is ready, what blocks it, what must be done, what was found, what material is needed, what evidence is required, and whether the technical result was verified.

A Work Order must not feel like a long editable database row.

Available domain actions such as assign, release, start, record Finding, issue material, complete, verify, cancel, or close should be explicit and state-aware.

## Inventory experience

Inventory should make custody trustworthy and fast to understand.

Users need to see on-hand, reserved, available, unavailable, required, and replenishment meaning without reconstructing balances from Movement rows.

Posted Movements remain visible as history, but operational pages should lead with current custody meaning and the action the user needs to take.

## Reliability experience

Reliability should tell a failure story:

- what function failed;
- where or which Asset failed;
- process consequence;
- when it occurred and was detected;
- restoration attempts;
- maintenance response;
- material constraints;
- cause assessment;
- recurrence;
- operational recovery.

Reliability analytics must lead back to source Failure Events and evidence rather than becoming disconnected KPI cards.

## Home

Home is a concise server-owned operational orientation surface, not a wall of charts or a duplicate of every module overview.

It should prioritize, according to the User's effective authority:

- actions that genuinely require the User or one of the User's Teams;
- due or overdue Process Rounds assigned to the User or the User's Teams;
- urgent or critical Process Conditions and due monitoring reviews;
- active Failure Events and significant restoration or investigation obligations;
- overdue, urgent, or materially blocked Maintenance work;
- Inventory shortages or replenishment obligations that affect current plant work;
- recent significant operational changes worth understanding;
- direct routes into the normal owning workspace.

Home summaries are produced by server-owned cross-domain read models. The browser must not reconstruct global attention by downloading partial registries or inventing attention rules.

A summary count is shown only when the User may inspect the underlying records and the count is complete for its declared scope. An unavailable metric is omitted rather than rendered as a misleading zero.

Home never executes consequential commands directly. It opens the normal authorized context where the User can understand and perform the action.

## My Work

My Work is the user's cross-domain operational queue.

It may include records assigned to the current User, records owned by the User's Teams, and records exposing an executable decision-bearing action for the User.

The queue preserves each record's owning domain and identity.

Users must be able to filter and search, understand priority and due state, see why an item requires attention, open complete context, perform the next available action, and return without losing filters, sorting, selection, or position.

## Available actions

Available actions are a core Dashboard contract.

An action is displayed only when:

- Server authority returns it as available;
- the Dashboard has a complete registered interaction for it;
- required inputs and known prerequisites are understood.

An action must have clear human wording, appropriate confirmation or input, visible requirements, specific validation feedback, predictable success behavior, and clear handling of changed record state.

Unknown action descriptors are contract defects. They must not be silently ignored.

## Prefer domain actions over generic editing

Operational state changes must be represented primarily through named domain actions.

Examples include:

- assign;
- release;
- start;
- complete;
- verify;
- resolve;
- reopen;
- record Reading;
- record Operational Action;
- install Asset;
- remove Asset;
- transfer stock;
- adjust stock.

Generic edit is appropriate for descriptive or configuration information. It must not conceal lifecycle transitions or consequential actions.

## Forms

Forms are organized around user intent.

They must:

- ask for the minimum required information first;
- never ask for information Waiotech already knows from context;
- use operational defaults where safe;
- group fields into meaningful sections;
- progressively reveal optional detail;
- use direct operational language;
- use proper selectors rather than raw identifiers;
- preserve user input after recoverable failure;
- explain important consequences;
- avoid raw enum and database-field vocabulary.

Complex workflows should use meaningful sections or steps rather than one unstructured wall of controls.

## Operational timelines

Waiotech should present consequential history as a chronological operational story.

Timeline entries may compose source events from several domains while preserving their original authority and identity.

Examples include Readings, Observations, Process Condition actions, Work Request creation, Work Order execution, Inventory usage, Failure Event restoration, verification, and resolution.

Raw audit field changes belong in audit detail where required; ordinary users should see the business event and attributable Actor.

## Readings and trends

Charts and trends exist to answer an operational question.

Useful examples include:

- a Measurement Point trend around an active Process Condition;
- runtime since the last maintenance occurrence;
- process performance before and after an intervention;
- selected Readings around a Failure Event.

The default interface should communicate current meaning first. A trend should not consume the page merely because data exists.

Waiotech must not become a generic dashboard builder.

## Professional information density

Waiotech must optimize for useful information density.

The product should be compact enough for planners, operators, engineers, and warehouse personnel to work efficiently on large displays while remaining clear and touch-usable on tablets and supported mobile widths.

Avoid both extremes:

- dense ungrouped enterprise screens where everything competes for attention;
- decorative SaaS layouts with excessive whitespace and too little operational information.

## Tables and master-detail workspaces

Tables are appropriate where comparison, prioritization, scanning, and bulk operational understanding matter.

The problem is not the table; the problem is a table that only leads to generic record editing.

Where suitable, a table or queue should support selection, contextual detail, visible blockers, available actions, related history, and preserved workspace state.

## Search

Search must operate on human operational identity rather than requiring users to know which database registry to search.

Plant search should resolve canonical codes, names, and supported external identifiers and surface understandable matches across Functional Locations, Assets, Process Units, Measurement Points, and relevant operational records according to Permission.

Search results preserve the owning record type and open its normal authorized context.

## Status and attention

The Dashboard must distinguish:

- lifecycle state;
- handling state;
- priority or urgency;
- due or overdue state;
- responsibility;
- action readiness;
- blockers;
- material availability;
- verification or approval requirements;
- Reading quality;
- Process attention.

Colour may reinforce meaning but text and icons must remain sufficient.

Attention colour is scarce. Neutral states should remain visually neutral so true operational attention remains visible.

## Language and tone

Dashboard language is concise, human, and operational.

Prefer phrases such as:

- “What needs to be done?”
- “Why is this work blocked?”
- “Record dissolved oxygen.”
- “Three required items are unavailable.”
- “The record changed after you opened it.”

Avoid internal implementation terminology, generic technical errors, unexplained codes, excessive administrative wording, and labels copied from database fields.

Persian and English are equal supported languages. Neither is a translated afterthought.

## Responsive, accessible, and bidirectional behavior

All supported Tenant workflows must remain usable at supported desktop, tablet, and mobile widths.

Responsive behavior preserves information, actions, validation, workflow progress, related context, and safe confirmation behavior.

The Dashboard supports keyboard navigation, visible focus, semantic labels, screen-reader-compatible controls, sufficient contrast, reduced reliance on colour, correct RTL and LTR layout, and appropriate icon direction.

RTL behavior is designed into shared layouts and components.

## Errors, conflicts, and uncertain results

Errors explain what failed, what the user can do next, whether input was preserved, whether retry is safe, and whether authoritative data changed elsewhere.

Concurrency conflicts must not appear as generic failure. The Dashboard refreshes authoritative state and helps the user reconsider or safely repeat the action.

For idempotent commands, uncertain retry reuses the same operation identity according to Engineering Authority.

## Performance and feedback

The Dashboard responds immediately to user input while preserving Server authority.

It provides useful loading states, background-refresh indication without unnecessarily clearing current content, immediate submission feedback, duplicate-command protection, specific success confirmation, and updated authoritative state.

Optimistic updates are used only when they cannot misrepresent consequential operational truth.

## Acceptance standard

A Dashboard capability is ready only when:

- Server and generated-client contracts align;
- Tenant isolation is preserved;
- supported actions are complete end to end;
- the primary workflow is understandable without specialist software guidance;
- English and Persian behavior is correct;
- desktop, tablet, and supported mobile behavior is usable;
- loading, empty, validation, conflict, error, refreshing, and success states are handled;
- consequential actions and evidence remain attributable;
- critical workflows have automated tests;
- the result feels like one coherent Waiotech operating product rather than a collection of technical screens.

## Related documents

- [Tenant Dashboard and Platform Admin](030-tenant-dashboard-and-platform-admin.md)
- [Dashboard design conventions](090-dashboard-design-conventions.md)
