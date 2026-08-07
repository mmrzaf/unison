# File access, integrity, and lifecycle

Accepted file bytes remain private, immutable, integrity-protected, subject-authorized, quarantine-aware, correction-based, and preserved according to governing evidence rules.

## Keep storage replaceable without changing product evidence

Waiotech must keep storage replaceable without changing product evidence.

Changing the storage mechanism requires an ADR and migration that preserves every Attachment identity, byte sequence, digest, link, classification, availability meaning, access rule, and recovery guarantee.

## Keep accepted file storage private

Waiotech must keep accepted file storage private.

Retrieval occurs through backend authorization or bounded delegated read authority.

## Permit bounded storage delegation without bypassing governing-subject access

Permit bounded storage delegation without bypassing governing-subject access.

They are short-lived, read-only, bound to one exact immutable object, issued after authorization, excluded from logs and persistent storage, and treated as temporary secrets. Classification may prohibit them.

## Re-evaluate file authority at retrieval time

Re-evaluate file authority at retrieval time.

Every retrieval evaluates principal, Tenant, governing-subject access, applicable Permission, classification, safety, integrity, storage availability, and artifact sensitivity.

## Freeze bytes, not access rights

Waiotech must freeze bytes, not access rights.

Uploading does not survive Membership suspension, Permission loss or governing-subject access change.

## Deliver accepted bytes safely and consistently

Waiotech must deliver accepted bytes safely and consistently.

Downloads use streaming, safe content disposition, accepted filename, verified type or safe fallback, byte size, sensitivity-aware cache controls, `nosniff`, and interruption handling. Storage references remain private.

## Treat preview as an active security boundary

Waiotech must treat preview as an active security boundary.

Download is the safe default. Inline rendering requires an allow-listed media type, integrity and safety checks, sandboxing, content security controls, size limits, and no credential-bearing embedded requests.

## Make accepted bytes cryptographically identifiable

Waiotech must make accepted bytes cryptographically identifiable.

Attachment metadata stores approved cryptographic algorithm and digest, accepted size, exact object identity, and verification evidence. SHA-256 is the minimum unless a stronger security standard applies.

## Establish integrity in a trusted boundary

Establish integrity in a trusted boundary.

The backend or trusted adapter computes it and validates the result. Client-provided digest may assist transport but is not sole authority.

## Verify at trust and recovery boundaries

Waiotech must verify at trust and recovery boundaries.

Before acceptance, after storage migration, during restoration verification, upon suspected corruption, and when a sensitive retrieval contract requires it. Equivalent provider integrity controls may avoid rehashing every read.

## Treat mismatch as an evidence-integrity incident

Waiotech must treat mismatch as an evidence-integrity incident.

Normal use and download stop. The Attachment enters integrity-failure quarantine or unavailability, remains historically visible, triggers operations alert, and may be restored only with bytes exactly matching the accepted digest.

## Quarantine

Restrict unresolved or unsafe files without deleting history.

Quarantine means safety, integrity, content type, or processing result does not permit ordinary use. The Attachment remains visible but cannot satisfy evidence or normal retrieval.

## Make safety disposition explicit and auditable

Waiotech must make safety disposition explicit and auditable.

An idempotent protected action preserves reviewer or system process, reason, evidence, time, prior state, and outcome: release, retain quarantine, or reject from normal use.

## Storage unavailability

Waiotech must preserve meaning while reporting technical loss truthfully.

It means Attachment identity and links exist but accepted bytes cannot be retrieved. It is distinct from quarantine. Historical existence remains visible, but the file cannot satisfy retrievable evidence.

## Correct by addition, never overwrite

Correct by addition, never overwrite.

Different content creates another Attachment and typed link with correction or supersession reason, Actor, and time. The original remains immutable.

## Prevent metadata edits from rewriting evidence

Waiotech must prevent metadata edits from rewriting evidence.

Minor non-evidential presentation correction may use a protected action. Material changes to governing subject, role, attribution, classification, filename with evidential meaning, type, size, digest, or identity require additive correction.

## Keep file relationships append-oriented

Waiotech must keep file relationships append-oriented.

They may be superseded, revoked for new reliance, or corrected while preserving history.

## Limit deletion to disposable technical data and explicitly disposable learning-Tenant state

Waiotech must limit deletion to disposable technical data and explicitly disposable learning-Tenant state.

Automatic deletion is permitted for expired unaccepted uploads, abandoned sessions, failed temporary generation output, rebuildable previews, and verified orphan objects.

Accepted Attachments belonging to a learning-purpose test Tenant may be deleted only through its deterministic reset contract because the learning-environment contract classifies the complete synthetic business state as disposable. Governed operational Attachment destruction requires a separate Product Authority and legal contract.

## Avoid deleting bytes during transient database or transaction delay

Avoid deleting bytes during transient database or transaction delay.

An orphan has no Attachment, active upload, or active generation reference and has exceeded the diagnostic interval. Detection reconciles PostgreSQL and storage conservatively before deletion.

## Keep logical evidence identities separate from storage optimization

Waiotech must keep logical evidence identities separate from storage optimization.

Only when logical Attachment identities, Tenant isolation, classification, access, audit, and retention remain independent. Cross-Tenant deduplication requires a security ADR proving no existence side channel.

## Related documents
- [File ingestion and storage](010-file-ingestion-and-storage.md)
- [Generated artifacts and file recovery](030-generated-artifacts-and-recovery.md)
- [Authentication, sessions, and secrets](../40-security/030-authentication-sessions-and-secrets.md)
