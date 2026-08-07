# Android synchronization and release

Android synchronization preserves field evidence while establishing canonical Server authority operation by operation. Synchronization is resumable, duplicate-safe, package-aware, conflict-explicit, and compatible with both Process and Maintenance field work.

## Prevent the mobile journal from becoming an alternate event source

Android journal entries are field operations awaiting or recording synchronization. They are not canonical Domain Events and do not bypass owning Application commands.

After acceptance, the Server emits ordinary audit and Domain Events from the canonical command transaction.

## Use checkpointed resumable synchronization

Synchronization exchanges bounded operation batches and durable checkpoints.

A retry after network loss reuses stable operation identities. The client must be able to resume without replaying accepted operations as new work or discarding unresolved evidence.

## Establish one canonical result per operation

Every synchronized operation establishes or returns one canonical Command Receipt.

The receipt preserves action, idempotency scope, request fingerprint, result, resulting product identities, and typed rejection where applicable. Mobile metadata links the result to package identity and type, installation, local sequence, and reconciliation state without creating another command-outcome authority.

## Keep synchronization inside Application authority

Synchronization resolves the authenticated User and installation, validates the package basis, and invokes the owning Process, Maintenance, Inventory, Attachment, or other explicit Application command.

A synchronization handler must not write domain tables directly or downgrade validation because the action occurred offline.

## Synchronize field files separately from Attachment acceptance

File bytes may transfer before or after the related operation according to the typed evidence contract.

Transfer completion does not establish Attachment acceptance. Canonical Attachment identity, classification, subject link, safety checks, and audit are established only through the file/evidence authority.

## Preserve time evidence

Synchronization preserves at least the field-observed time, local sequence, Server receipt time, and owning-domain effective/recorded time semantics required by the action.

Clock skew is diagnostic evidence. The Server does not silently replace the User's observed time merely because device time differs.

## Typed synchronization outcomes

The protocol distinguishes at least:

- accepted;
- already established;
- rejected with correctable input;
- denied or expired authority;
- authoritative-state conflict;
- needs explicit review;
- retryable infrastructure failure;
- update required.

The Android experience must not present all non-success results as a generic sync error.

## Package revocation and expiry

When the Server knows a package is revoked or expired, no new action is accepted under it unless Product Authority explicitly allows reconciliation of evidence already recorded while authority was valid.

Recorded field evidence is not erased because authority later changed. Its final relationship to canonical state is preserved explicitly.

## Process synchronization

Manual Process Readings and Operational Observations synchronize through canonical Process commands and preserve Measurement Point, Process subject, User, provenance, effective time, quality, reported unit, canonical normalization, and correction semantics.

Process Round operations synchronize against the exact Round and Process Routine Revision snapshot carried by the Process Package. Starting, entry progress, cancellation, and completion invoke canonical Process Round commands. Round entry synchronization establishes or references canonical Reading or Observation identities; it never creates a second copy of the evidence value inside the Round. A completion operation is accepted only when the canonical Round contract can establish that every required entry and required evidence is satisfied. Retry must preserve the deterministic Round occurrence and stable local operation identities so one logical scheduled occurrence cannot become several Rounds.

An offline Process lifecycle or Operational Action command is accepted only when its Process Package explicitly authorized the action and the canonical command confirms that the issuance basis still permits establishment. Otherwise the field evidence remains preserved and the result requires online review.

## Maintenance synchronization

Work Order actions preserve Work Package identity, exact Procedure Revision, Task lineage, Requirements, field observations, Maintenance measurements, Findings, completion evidence, and material evidence according to their owning contracts.

Offline completion submission never performs independent verification or closeout unless Product Authority separately permits that action.

## Inventory synchronization

The device never derives Inventory posting authority from a cached Stock Balance.

A Work Package or explicit field contract contains typed references to reserved, issued, kit-custody, or otherwise authorized material context. Synchronization invokes canonical Inventory commands and preserves reconciliation if authoritative custody prevents the intended posting.

## Scanning

Scanning resolves typed identifiers such as Functional Location, Asset, Measurement Point, Item, Stock Location, or supported record code. A scan identifies context; it does not grant Permission or offline authority.

## Push delivery

Push is a delivery hint for canonical Notification or field refresh. Push payloads contain no unnecessary protected plant data and never become the source of Notification truth.

## App upgrades and local schema

Local Room schema and protected-file migrations preserve unsynchronized operations transactionally or through resumable controlled steps.

Supported app releases must remain able to interpret active package and journal versions. An incompatible version fails safely and retains protected evidence for governed recovery.

## Mobile version overlap

Every request identifies app and mobile-contract version. The release manifest declares supported API, package, journal, and synchronization versions. The Server returns a typed update-required result when safe operation is no longer possible.

## Release and distribution

Android artifacts are signed, identifiable, reproducible according to release policy, and distributed only through approved channels. Release verification includes authentication, package verification, offline operation, synchronization, conflict, upgrade, accessibility, Persian/English, and data-removal behavior.

## Performance and field usability

Android uses bounded startup work, lazy loading, paged lists, compressed field media where evidence rules permit, background synchronization, restrained animation, and no unnecessary network request.

The app remains usable on the supported device class under intermittent connectivity.

## Sign-out, revocation, and evidence preservation

Secure sign-out, device reassignment, administrative installation revocation, or protected reset removes credentials and readable Tenant data according to the security contract.

Voluntary sign-out must not silently discard unsynchronized evidence. The app identifies unresolved journal entries, attempts synchronization when possible, and requires an explicit safe resolution path before destructive local cleanup.

## Related documents

- [Android Work App](../../30-experience/040-android-work-app.md)
- [Offline field packages](../../30-experience/050-offline-field-packages.md)
- [Android installation and offline authority](030-android-installation-and-offline-authority.md)
