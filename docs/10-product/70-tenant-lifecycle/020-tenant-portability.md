# Tenant portability

Tenant Export and protected Tenant Import are required Waiotech Alpha
capabilities. They use a versioned, integrity-verifiable Waiotech package and
never expose an undocumented database copy.

## Tenant Export

A Tenant Export is a governed asynchronous operation that produces one immutable
Export Artifact for one Tenant, contract version, cutoff, and authorized request.

The lifecycle is:

```text
queued -> running -> completed
queued -> cancelled
running -> failed
running with expired lease -> running retry
```

Cancellation is truthful: a running operation is not labelled cancelled merely
because cancellation was requested.

Every export preserves request Actor, Tenant, reason, contract version, cutoff,
parameters, authority evidence, start and completion times, outcome, artifact
identity, digest, encryption metadata where used, and audit correlation.

## Export content

The Alpha package contract is `waiotech-tenant-export-v1`. It is an explicit,
closed contract generated from the accepted persistence model and reviewed in source.
Every Tenant-scoped table is classified as included or excluded; new tables and new
columns are not exported implicitly.

One package contains:

- `manifest.json` with package identity, source product and Tenant, cutoff, included
  domains, counts, SHA-256 digests, relationship digest, and file inventory;
- `contract.json` with the exact component and field contract and exclusions;
- `tenant.json` and `participants.json`;
- one canonical JSON record file per included component;
- accepted Attachment binaries under stable package paths.

The database snapshot uses one PostgreSQL repeatable-read, read-only transaction and
one transaction timestamp as the cutoff. Record files are deterministic and Attachment
binaries are checked against accepted size, media type, and SHA-256 evidence before and
during packaging. The completed ZIP is written to a deterministic private object
identity, verified again after storage, and only then marked completed.

The contract excludes cached projections, command receipts, internal domain/outbox
events, worker item failures, bootstrap state, live object-storage references, export
worker leases, and request authority internals. It never includes password credentials,
refresh sessions, login-attempt state, CSRF material, signing or encryption keys,
storage credentials, or one-time secrets. Participants contain only portable identity
and account-state facts required to reconcile User references.

## Export access

Platform initiation, inspection, cancellation, and retrieval are separate explicit
Permissions and actions. A Tenant may have at most one queued or running export. Only
a queued export can be cancelled; running work reports its real outcome. Learning
Tenants are excluded from portability.

Retrieval rechecks current durable platform authority, requires recent platform
authentication, a fresh idempotency key, and an explicit reason, then records immutable
retrieval evidence and audit before streaming the verified artifact with `no-store`
headers. Alpha retrieval streams through the authenticated API. A short-lived delegated or one-time download URL is not part of the Alpha portability contract.

Export artifacts are preserved by default. Configurable retention, expiry, encryption,
and physical destruction remain separate governed contracts rather than hidden cleanup.

## Tenant Import

Tenant Import is a protected Platform Admin operation. It accepts only a complete
`waiotech-tenant-export-v1` package. The source ZIP is first written to a private,
digest-derived Tenant-prefixed quarantine identity under the target Tenant ID.
The stable identity makes repeated HTTP delivery safe without creating parallel import
operations or files. Ordinary Tenant sessions cannot access that object.

The lifecycle is:

```text
received -> validating -> ready -> importing -> completed
received|validating|importing -> failed
received|ready -> cancelled
failed|cancelled -> received (explicit retry)
```

Receiving a package requires recent platform authentication, explicit import authority,
an idempotency key, a reason, the source byte count, and the source SHA-256 digest. The
Server enforces compressed-size, expanded-size, record-count, file-count, path, duplicate
entry, symbolic-link, and encrypted-entry limits before trusting package content.

Validation is asynchronous and non-mutating. It requires the exact supported component
set and verifies the manifest, contract, Tenant metadata, participant identities,
canonical JSON digests, record shapes, primary-key uniqueness, Tenant scope, foreign-key
closure, supported Permission codes, relationship digest, Attachment inventory, bytes,
media evidence, and SHA-256 digests. Unknown fields, missing components, extra ZIP
entries, conflicting package identities, and changed source bytes are rejected rather
than guessed.

A ready import starts only through a separate recent-authenticated action. Import always
creates one new `provisioning` Tenant with a deterministic new technical identity. It
never merges into or overwrites an existing Tenant. Source Tenant and record UUIDs are
remapped deterministically under the import operation identity, preserving internal
relationships while preventing collision with live platform records.

Users are reconciled only by exact canonical phone number. An existing matching global
User is reused. A missing participant becomes a disabled global User with no password,
session, platform grant, or other credential. Imported Memberships and Tenant authority
continue to reference the reconciled User. Activation readiness therefore remains
blocked until at least one imported administrator User is enabled and the imported IAM
foundation is otherwise valid.

All accepted database records are written in one protected import transaction. Source
audit facts are preserved as immutable imported audit evidence linked to the import and
target Tenant; they are not inserted into the live command, event, or platform-audit
streams. Accepted Attachment bytes are copied to deterministic target-Tenant object
identities and verified again. Retries reuse those identities instead of creating
parallel files.

Completion leaves the new Tenant in `provisioning`. Activation is a separate protected,
readiness-gated action. A failed import never exposes a partially active Tenant. Any
files prepared before a failed database transaction remain private and unreachable from
ordinary Tenant APIs until a governed retry or the file-lifecycle cleanup contract resolves them.

Passwords, password hashes, sessions, mobile credentials, Android installation secrets,
signing keys, encryption keys, storage credentials, one-time credentials, export worker
state, import worker state, command receipts, and outbox state are never imported.
Repeating the same source digest returns the existing operation; a conflicting package
identity is rejected. A failed or cancelled operation can be explicitly retried. Retry
clears prior validation evidence and runs the complete validation stage again before the
operator can restart import.

## Portability integrity

The package manifest identifies format version, source product version, source
Tenant identity, export cutoff, included domains, record counts, file inventory,
content digests, relationship digest, and required SHA-256 verification. Version 1 records encryption as `none`. Adding package encryption requires an explicit versioned contract and never replaces integrity verification.

The package and its resulting operations remain auditable. Retention and physical
destruction follow the preservation policy without rewriting the fact that an export
or retrieval occurred.

## Excluded behavior

Waiotech does not support ordinary cross-Tenant record movement, merging two
Tenants, importing arbitrary third-party database dumps, or restoring live
security secrets.

## Related documents
- [Tenant lifecycle](010-tenant-lifecycle.md)
- [Preservation and destruction](030-preservation-and-destruction.md)
- [Generated artifacts and recovery](../../20-engineering/70-files/030-generated-artifacts-and-recovery.md)
