# Android installation and offline authority

Android Work App uses native Kotlin architecture, protected local storage, installation identity, mobile authentication, Tenant context, server-issued offline field packages, and one append-only local operation journal. Offline authority is package-specific and never turns the device into an alternate Tenant authority.

## Make mobile participation attributable without unstable hardware identity

Each app installation receives an opaque Server-recognized installation identity bound to one User at a time.

Hardware serial, IMEI, advertising identifier, phone number, or another unstable/device-global identifier must not become product identity or authorization authority.

Installation identity participates in authentication, package issuance, synchronization, revocation, audit, and support diagnostics.

## Prohibit shared active mobile application state

One active Android application state belongs to one User and one Tenant context.

Changing User or Tenant requires the governed authentication and local-data transition contract. Credentials, packages, journals, cached Tenant data, and protected files must never become shared between Users.

## Use mobile-native delegated authentication

Android uses system-browser authorization with authorization-code flow and PKCE against the approved identity service, short-lived access credentials, protected refresh-credential rotation, Server-side revocation, and Android Keystore-backed protection.

Biometrics may unlock protected local credentials or encrypted local data. Biometrics are not independent Server authority.

## Enforce Tenant isolation locally and during synchronization

Tenant identity is explicit in navigation, downloaded data, package envelopes, journals, query ownership, files, Notifications, and synchronization requests.

Tenant switch cancels in-flight Tenant work, clears or securely transitions caches according to the recovery contract, and cannot expose records from another Tenant context.

## Minimize local data

Android stores only the plant context, Process context, Work Orders, evidence, material references, offline packages, and support data required for its field responsibilities.

It does not replicate the full Tenant database or broad configuration catalogues without a field-use requirement.

## Offline field package types

The canonical package types are:

- `work`: exactly one Work Order and its bounded execution authority;
- `process`: a bounded set of Process subjects, Measurement Points, optional active Conditions, and explicitly permitted field-evidence actions.

The shared technical envelope contains:

- package identity and type;
- schema version;
- User;
- installation;
- Tenant;
- exact subject identities and authoritative versions where applicable;
- permitted action codes;
- evidence contract;
- issuance and expiry;
- package digest;
- Server authenticity proof.

Package-specific payload is typed and independently interpretable. Arbitrary extension JSON must not become business authority.

## Work Package payload

A Work Package additionally preserves the exact Work Order basis, concurrency version, Procedure Revision, Task and Requirement snapshot, material allocation references where needed, and field execution contract.

## Process Package payload

A Process Package additionally preserves the exact included Process Units or Streams, any included Process Round identity and deterministic occurrence identity, the exact immutable Process Routine Revision entry snapshot needed for that Round, Measurement Points available for manual entry, applicable active Process Conditions, bounded context, and Process action contract.

For an included Process Round, the package snapshot is sufficient to establish entry order, required-entry meaning, configured Measurement Points or Observation subjects, reference guidance, evidence requirements, and completion eligibility without consulting mutable Routine configuration. The resulting Readings and Observations remain canonical Process records rather than copied Round values.

A Process Package may authorize only the actions that Product and Experience Authority permit offline. Lifecycle actions that require fresh ownership, duplicate, or current-state evaluation remain online-only unless the package contains a sufficient explicit preauthorization contract.

## Offline Work Request drafts

Android may preserve an encrypted local Work Request draft independent of package authority.

A draft is not a Domain record, audit record, or accepted maintenance need. It is submitted only through explicit User action after connectivity returns, using a stable operation identity and the canonical Work Request command.

## Make issued authority durable and auditable

PostgreSQL preserves package issuance identity, type, User, installation, Tenant, subjects, contract version, digest, issuance, expiry, revocation, synchronization summary, and audit.

The exact package payload or a reproducible immutable snapshot is retained according to the evidence contract.

## Fail closed on package verification

Android verifies Server authenticity, package integrity, type, version, User, installation, Tenant, and expiry before allowing another offline action.

Invalid, altered, unknown-version, wrong-User, wrong-installation, wrong-Tenant, revoked-known, or expired packages cannot authorize another operation.

## Preserve local evidence in one ordered journal

Offline Process and Maintenance operations share one encrypted append-only technical journal while preserving package type and owning domain action.

Each entry contains stable local operation identity, package identity and type, action schema version, local sequence, typed input, evidence references, observed times, correction relationship, and synchronization state.

Sharing the journal mechanism does not make Process and Maintenance actions one generic business event model.

## Use additive correction

Synced evidence is never mutated locally. A correction references the affected entry and provides the typed corrected value, replacement, or withdrawal reason permitted by the owning domain.

Unsubmitted drafts may be edited because they have not yet been represented as accepted field evidence.

## Make offline operations duplicate-safe

The app generates collision-resistant operation identities scoped by installation and package. Identity generation does not depend on connectivity or device time.

Synchronization maps each operation into the same owning Application command used by connected execution and establishes one canonical Command Receipt.

## Preserve causal order without trusting the device clock

Each package journal uses a monotonic local sequence and explicit causation where needed. Device timestamps remain evidence but do not replace Server acceptance time or owning-domain effective-time validation.

## Make offline eligibility explicit

Package generation classifies required inputs as:

- immutable referenced fact;
- package snapshot;
- locally verifiable field input;
- Server-dynamic online-only fact;
- reconciliation-sensitive exception explicitly permitted by Product Authority.

The Server refuses to issue an offline action when a required input has no safe classification.

## Key rotation

Package authenticity identifies the verification-key version.

Android retains the bounded trusted verification set required for valid unexpired packages. Key rotation preserves verification or revokes affected packages explicitly. Compromised keys trigger package revocation and incident response.

## Related documents

- [Android Work App](../../30-experience/040-android-work-app.md)
- [Offline field packages](../../30-experience/050-offline-field-packages.md)
- [Android synchronization and release](040-android-synchronization-and-release.md)
