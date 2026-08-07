# File ingestion and storage

Attachments use explicit upload sessions, Tenant and subject binding, server-owned storage keys, verified media type and size, streaming transfer, immutable storage, and recoverable acceptance across storage and PostgreSQL boundaries.

## The Attachments module ownership

Waiotech must keep file identity and handling in Attachments and business evidence meaning in the governing module.

Attachments owns accepted file identity, upload sessions, file integrity, classification, safety and quarantine, storage availability, storage ports, transfer contracts, retrieval coordination, file-level audit, and typed Attachment Links. The governing product module owns why the file matters.

## Create Attachment identity only after bytes satisfy the acceptance contract

Waiotech must create Attachment identity only after bytes satisfy the acceptance contract.

An Attachment is the stable identity of one accepted immutable file. It preserves Tenant, filename, declared and verified media type, size, integrity algorithm and digest, uploader or generating process, acceptance time, classification, safety state, availability, immutable storage reference, and governing link.

## Separate transfer state from accepted file identity

Waiotech must separate transfer state from accepted file identity.

It is temporary technical state for receiving and verifying bytes. It does not prove accepted evidence, file availability, or business linkage.

## Use typed links to assign product meaning to files

Waiotech must use typed links to assign product meaning to files.

An Attachment Link connects an Attachment to one typed governed subject and role. It preserves link identity, Tenant, subject, role, governing or additional status, adding Actor, time, lifecycle, and correction relationship.

## Keep evidence roles domain-owned

Waiotech must keep evidence roles domain-owned.

The module owning the linked subject defines permitted roles, evidence effect, authority, correction, and retrieval behavior. Attachments provides mechanism only.

## Prohibit generic file association as a product API

Waiotech must prohibit generic file association as a product API.

Links are created through module-owned typed commands. Internal polymorphic references may exist technically but cannot authorize unrestricted linking.

## The governing subject

Waiotech must never allow an accepted Attachment without governing context.

Every Attachment has exactly one governing subject that determines minimum access, sensitivity, primary meaning, preservation dependency, and evidence context. Acceptance and governing-link creation commit together.

## Permit controlled reuse without access escalation

Permit controlled reuse without access escalation.

One accepted Attachment may be linked to additional governed subjects within the same Tenant when the owning-domain contracts permit reuse. Additional links cannot weaken governing-subject protection and may impose stronger restrictions.

## Keep evidence sufficiency under the owning domain

Waiotech must keep evidence sufficiency under the owning domain.

The governing domain verifies required role, availability, integrity, safety, metadata, and any domain-specific acceptance. Upload or link presence alone is insufficient.

## The canonical ingestion flow

Waiotech must separate transport, verification, storage, and business acceptance.

The flow is:

```text
create upload session
-> transfer temporary bytes
-> complete transfer
-> verify size and digest
-> evaluate media type
-> complete required safety processing
-> finalize immutable storage object
-> commit Attachment and governing Link
-> expose according to accepted state
```

## Bind temporary transfer to one authority and intended use

Waiotech must bind temporary transfer to one authority and intended use.

It preserves Tenant, principal, intended subject and role, filename, declared type and size, limits, transfer method, temporary object, transferred bytes, expiry, completion, and safe failure.

## Create another session when the intended governed context changes

Waiotech must create another session when the intended governed context changes.

## Prevent abandoned upload authority from remaining valid

Waiotech must prevent abandoned upload authority from remaining valid.

Every session has a bounded expiry. After expiry, transfer is rejected, no Attachment is created, and temporary bytes become eligible for controlled cleanup.

## Keep storage addressing private and non-semantic

Waiotech must keep storage addressing private and non-semantic.

The server or storage adapter creates an opaque collision-resistant key independent from filename, product code, or user path. Clients never provide or receive authoritative storage paths.

## Prevent path injection and unsafe download metadata

Waiotech must prevent path injection and unsafe download metadata.

Filenames are untrusted presentation metadata. Path components and control characters are removed, unsafe length and header content are rejected, and the name is never used as a physical path or content-type proof.

## Treat file content as untrusted

Waiotech must treat file content as untrusted.

The contract compares declared type, extension, detected type, and file signature where supported. Mismatch may reject or quarantine. Filename extension alone is insufficient.

## Bound file resource use at every ingestion stage

Bound file resource use at every ingestion stage.

Limits apply before transfer where declared, during streaming, and before acceptance. Aggregate subject or command limits may also apply. Exceeding a limit creates no Attachment.

## Keep file processing memory-bounded

Waiotech must keep file processing memory-bounded.

No file operation may require loading the complete object into memory when the permitted size exceeds a safe bounded allocation. Transfer, hashing, scanning, and retrieval use streaming or bounded buffering.

## Delegate byte transfer without delegating Attachment authority

Delegate byte transfer without delegating Attachment authority.

Where direct object-store transfer is used, Waiotech grants only short-lived authorization bound to one upload session, Tenant, object, operation, size, and content limits where supported. The backend still verifies and accepts the object.

## Choose disposable orphan bytes over false accepted evidence

Choose disposable orphan bytes over false accepted evidence.

If byte transfer succeeds but governed Attachment acceptance does not, no Attachment exists. The unaccepted object remains private and becomes eligible for cleanup after the diagnostic interval.

## Never commit Attachment metadata before immutable bytes exist

Waiotech must never commit Attachment metadata before immutable bytes exist.

No Attachment exists. The finalized unreferenced object is an orphan eligible for conservative cleanup.

## Bind one Attachment identity permanently to one byte sequence

Waiotech must bind one Attachment identity permanently to one byte sequence.

The exact byte sequence cannot be overwritten under the accepted reference. Object versioning, write-once keys, digest verification, or equivalent controls prevent silent mutation and key reuse.

## Define provider conformance by capability

Waiotech must define provider conformance by capability.

The storage port supports temporary upload, streaming, exact finalization, immutable read, metadata, integrity support, bounded delegated transfer where used, cleanup of disposable objects, health, backup, and recovery.

## Related documents
- [Attachments and evidence](../../10-product/60-evidence-and-communications/030-attachments-and-evidence.md)
- [File access, integrity, and lifecycle](020-file-access-integrity-and-lifecycle.md)
- [Generated artifacts and file recovery](030-generated-artifacts-and-recovery.md)
