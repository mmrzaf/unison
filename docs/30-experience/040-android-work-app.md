# Android Work App

Android Work App is Waiotech's governed native field surface for individually authenticated plant Users performing Process and Maintenance work near the plant, equipment, materials, and physical evidence. It prioritizes fast human evidence capture, dependable execution, scanning, clear synchronization, and bounded offline authority under intermittent connectivity.

## Make Android a field product rather than mobile administration

Android must optimize for Users who are moving through the plant, working with gloves or limited attention, scanning identifiers, following Tasks or Process Rounds, recording evidence, and operating with unreliable connectivity.

The primary users are technicians, operators, inspectors, field supervisors, and other authorized Users who perform or record plant work.

Android is not the primary surface for planners, Tenant administrators, IAM administrators, Process configuration, report administration, publication, or Platform Admin.

It must not reproduce the complete Tenant Dashboard on a small screen.

## Bind each active installation to one attributable User

Each active Android installation belongs to exactly one User at a time and preserves its own installation identity, credentials, local Tenant partitions, offline packages, encrypted files, journal, synchronization state, and revocation state.

A User may have several separately authorized installations. Offline authority is never shared between them.

A physical device may be reassigned only after the earlier User's authenticated context is ended or administratively revoked and readable Waiotech data is securely cleared or preserved through the governed unresolved-evidence contract.

## Keep Tenant context explicit

Tenant is the plant boundary on Android as everywhere else.

Every cached record, offline package, journal entry, file, scan result, Notification, synchronization operation, and command belongs to an explicit Tenant partition.

Changing Tenant context must not expose, submit, relabel, or clear another Tenant's unresolved field evidence incorrectly. Unsynchronized work remains associated with the Tenant under which it was created until governed synchronization or recovery resolves it.

## Primary Android areas

Canonical mobile navigation is:

```text
My Work
Process
Maintenance
Plant
More
```

`More` may contain Notifications, Sync Issues, profile/device status, contextual help, and other field-support functions that do not deserve permanent primary navigation.

Inventory interactions appear contextually where field custody or material use is authorized. Broad Inventory administration, Maintenance planning, Procedure or Plan publication, Process-model configuration, Data Source and Integration Principal configuration, IAM, policy, Reports, Tenant lifecycle, and Platform Admin remain outside ordinary Android workflows.

## My Work

My Work is the User's bounded cross-domain field queue.

It may include authorized:

- Process Rounds;
- Process Conditions requiring field attention;
- Work Orders;
- field evidence or synchronization obligations;
- other explicitly supported field responsibilities.

The queue prioritizes what genuinely requires the User or one of the User's Teams, due and overdue meaning, current responsibility, offline availability, important blockers, synchronization condition, and the next supported action.

Mobile discovery must remain relevant, authorized, bounded, paged where needed, and Tenant-scoped. Broad read Permission must not automatically turn Android into a Tenant-wide administration browser.

## Process field workflows

Android must support the human Process workflows that are naturally performed in the plant, including:

- navigate or scan to Process Unit, Process Stream, Measurement Point, Functional Location, or Asset context;
- discover, start, continue, and complete authorized Process Rounds;
- move through Round entries sequentially without re-selecting known context;
- record manual Process Readings;
- record Operational Observations;
- inspect recent relevant Readings and open Process Conditions;
- interact with a Process Condition where the action is authorized for mobile use;
- record Operational Actions and Outcome Assessment evidence where appropriate;
- capture photographs and other supported evidence;
- request Maintenance from Process context through the canonical Maintenance contract.

Human Reading entry must preserve Measurement Point, quantity/unit semantics, effective time, User, provenance, quality, correction, and audit meaning identical to Dashboard authority.

Machine-ingestion configuration does not appear in ordinary Android Process workflows.

## Process Round experience

Process Round execution is optimized for rapid, accountable human collection.

The field experience shows:

- Routine and Round identity;
- current entry and progress;
- required versus optional meaning;
- configured Measurement Point or Observation subject;
- concise reference guidance;
- recent useful context where authorized;
- missing required entries;
- offline and synchronization state.

When the Round already defines Measurement Point, subject, unit, Tenant, and User, the app must carry those facts into entry rather than asking the User to select them again.

Completing a Round means required collection work was performed. Android must not present Round completion as proof that the plant is healthy or a Process Condition is resolved.

## Process Condition field interaction

Process Condition handling on Android must preserve the same ownership, attention, lifecycle, action, and evidence meaning as Dashboard.

Mobile may emphasize the field-relevant subset: current operational situation, attention, responsible Team/User, latest evidence, action already taken, next review, and available action.

The app must not reduce Process Conditions to an editable status dropdown or a generic note thread.

## Maintenance field workflows

Android must support the governed Work Order field-execution loop, including:

- assigned and otherwise authorized Work Order discovery;
- Work Target and connected Plant/Process/Failure context;
- exact Procedure Revision and Task instructions;
- execution-relevant Work Requirements;
- hazards, permits, isolations, blockers, and field-visible Readiness;
- start, hold, resume, and other supported execution actions;
- maintenance measurements and observations;
- Findings;
- photographs, Attachments, and other evidence;
- authorized material issue, return, or usage capture;
- completion submission;
- visibility of verification, rework, or follow-up outcome where useful.

Planning results required for safe execution must be visible without moving planning authority into Android.

## Keep planning, approval, administration, and governance outside Android

Android must not provide ordinary workflows for:

- Work Request triage;
- Work Order preparation or release;
- emergency authorization unless a distinct mobile Product contract explicitly permits it;
- Procedure or Maintenance Plan authoring/publication;
- Process Unit, Stream, Measurement Point, Data Source, or machine-ingress configuration;
- Access Profile, Team, Membership, or Tenant Control administration;
- broad Inventory configuration or unrestricted adjustment;
- Stocktaking approval/posting unless separately authorized by Product Authority;
- independent verification requiring another Actor where the field User cannot satisfy separation of duties;
- final administrative closeout where Dashboard context is required;
- Report administration;
- Tenant export/lifecycle;
- Platform Admin.

Displaying planning or governance results does not transfer the authority to change them.

## Plant identification and scanning

Android may search or scan governed identifiers to identify:

- Functional Location;
- Asset;
- Measurement Point;
- Work Order or supported Process/Reliability record;
- Item;
- Stock Location;
- supported external identifiers.

A scan identifies candidate context; it never grants Permission or offline authority.

Unknown, retired, ambiguous, invalid, or cross-Tenant identifiers fail safely and explain the result.

## Offline authority

Disconnected Process or Maintenance actions are permitted only where Offline field-package Product and Engineering Authority explicitly define the exact action, subject, evidence basis, expiry, synchronization, conflict, and safety semantics.

Android local persistence is not an alternate Tenant database and a downloaded record is not disconnected write authority by itself.

## Offline Process authority

Offline Process support must remain deliberately bounded.

A Process Package may authorize execution of an explicitly identified Process Round Revision snapshot, manual Readings, Operational Observations, evidence, or other exact Process actions only for pre-identified subjects and Measurement Points.

It must not grant arbitrary offline creation or mutation of plant configuration, Data Sources, machine mappings, Integration Principals, or unrestricted Process Condition lifecycle authority.

Where a Process action cannot safely be pre-authorized, Android may preserve typed local evidence or a draft and establish the canonical action only after synchronization and fresh Server validation.

## Offline Maintenance authority

A Work Package may authorize only the field actions explicitly issued for one Work Order.

Downloaded Work Order information without a valid applicable Work Package is read context, not disconnected execution authority.

Offline completion submission never implies independent verification, closeout, or another Actor's Decision unless Product Authority separately defines that action.

## Restrict offline material recording to pre-authorized custody and allocation

Offline material recording is limited to explicit package or field-contract authority such as:

- material already issued to the Work Order;
- material reserved for the Work Order;
- a controlled User kit or custody location authorized for offline use;
- another product-defined bounded allocation.

The app must never treat a downloaded Stock Balance as authority for unrestricted offline issue, transfer, return, or adjustment.

## Preserve field evidence without falsifying Inventory posting

A User may record intended or observed material use under the applicable field contract, but Android must not invent a successful Inventory Movement, create negative stock without authority, silently substitute another Stock Location, or erase the field evidence when central custody validation rejects the intended posting.

Synchronization establishes the canonical Inventory result and preserves any reconciliation obligation.

## Support disconnected maintenance-need capture as a draft

Android may preserve an encrypted local Work Request draft while disconnected even when no Work Package grants Maintenance command authority, because the draft does not yet create a Work Request.

The draft may preserve candidate Functional Location or Asset, Process or Work Order context, narrative, occurred time, evidence, and stable local operation identity.

The User deliberately submits it when canonical synchronization is possible. Rejection preserves the draft and explains required correction.

## Separate local file capture from accepted Attachment identity

Android may capture protected local photos/files and link them to pending journal operations.

A local file is not yet an accepted Attachment.

Synchronization transfers and establishes canonical file identity, integrity, classification, safety/quarantine behavior, subject relationship, and audit through the Attachment contract.

Local evidence must remain attributable and must not be silently discarded after uncertain synchronization.

## Keep mobile communication separate from workflow authority

Android may receive field-relevant Notifications and minimal push hints.

Opening, reading, dismissing, or receiving a Notification never executes the related Process, Maintenance, Inventory, Reliability, or governance action. Sensitive details are retrieved through ordinary authenticated authority.

## Protect local field data

Local plant data must be:

- User-scoped;
- installation-scoped;
- Tenant-partitioned;
- encrypted through platform-backed protection;
- minimized to field need;
- bounded by package/session/retention rules;
- revocable where centrally enforceable;
- non-authoritative except for preserved local evidence awaiting canonical synchronization.

Readable Tenant data must not remain available after secure logout, User reassignment, required local destruction, or acknowledged installation revocation according to Engineering Authority.

## Contain device loss

Installation revocation, credential/session revocation, encryption, package expiry, bounded local retention, and central preservation of already-synchronized evidence limit the consequence of a lost device.

A disconnected device cannot be guaranteed to receive revocation immediately. Offline package scope and expiry therefore form part of the security boundary.

## Optimize for physical work

Android must prioritize:

- fast startup;
- clear current subject and responsibility;
- obvious connected/offline/synchronizing condition;
- large practical touch targets;
- strong contrast;
- minimal typing;
- fast camera and scanning access;
- predictable Task/Round sequence;
- useful recent context without dense dashboards;
- visible local-unsynced evidence;
- recoverable failure and conflict handling;
- usability in bright, noisy, moving field environments;
- accessibility;
- complete Persian and English behavior.

Decorative animation, broad analytics, and complex nested navigation must not interfere with execution.

## Provide focused field help

Android provides contextual help for the field workflows it supports, including Process Rounds, Readings, Observations, Process Condition interaction, Work Order execution, Tasks, measurements, Attachments, scanning, offline packages, synchronization, conflict handling, material recording, and completion submission.

Help required to execute a downloaded package safely may be included in the package or otherwise available offline under the package contract.

Android must not copy the complete administrative Help Center into the app.

## Related documents

- [Offline field packages](050-offline-field-packages.md)
- [Process Routines and Rounds](../10-product/35-process/015-process-routines-and-rounds.md)
- [Process Readings and Operational Observations](../10-product/35-process/010-readings-and-observations.md)
- [Android installation and offline authority](../20-engineering/60-applications/030-android-installation-and-offline-authority.md)
- [Work Order lifecycle and Readiness](../10-product/40-maintenance/060-work-order-lifecycle-and-readiness.md)
