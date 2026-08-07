# Authentication, sessions, and secrets

Authentication establishes attributable identity and protected credentials. Authorization, Tenant scope, lifecycle, and action-specific guards are evaluated separately.

## Browser sessions

Browser access uses short-lived access credentials held only in memory and
rotating refresh credentials in secure backend-managed cookies. JavaScript-
readable persistent storage must not contain access tokens, refresh credentials,
passwords, recovery secrets, or CSRF secrets.

Authentication establishes User and session identity. Tenant selection and
per-request authorization are separate. A session never receives Tenant
Permission merely because a Tenant identifier is present.

Mutations use same-origin or explicitly governed credentialed CORS, anti-CSRF
protection, idempotency where required, and strict origin validation.

## Platform sessions

Platform Admin uses an explicit platform session and platform authority. Platform
refresh credentials use a separate backend-managed cookie and platform access
tokens carry an explicit platform surface without a Tenant claim. Tenant and
platform refresh families cannot be exchanged.

The session resolves current durable platform Permissions on each protected request.
It does not create hidden Tenant context, impersonate a Tenant User, or imply Tenant
Permission. User, Tenant lifecycle, and Tenant Export commands use this platform
session. Export artifact retrieval additionally requires that the current platform
refresh family was authenticated within the configured sensitive-action window; it
uses a distinct retrieval Permission, fresh idempotency key, explicit reason, and
audited retrieval evidence. High-impact platform recovery, protected export retrieval, credential reset, and equivalent sensitive actions require recent password re-authentication within the configured sensitive-action window. Alpha does not require a second authentication factor; adding one may strengthen the mechanism without changing the product authority path.

## Integration Principal authentication

Integration Principals use a machine authentication mechanism distinct from browser, Android, and platform User sessions.

Machine credentials are bound to one Integration Principal and one Tenant. Authentication resolves only machine identity; authorization separately evaluates active principal state, explicit machine Permission, Data Source authority, Measurement Source Mapping, and owning-module guards.

Machine credentials must support rotation and revocation without changing Integration Principal identity. Secret material is stored only as protected verifier material or through an approved secret-management mechanism and is never returned after the one-time credential-establishment response where applicable.

A credential replay, rotation, or compromise response must not rewrite previously accepted Reading provenance.

Machine endpoints use replay resistance, rate limits, idempotency or source-event identity where required, and transport protections appropriate to automated ingestion.

## Android sessions and installations

Android uses system-browser authorization code with PKCE, short-lived access
credentials, rotating protected refresh credentials, server-side revocation,
and Android Keystore-backed local protection. It never stores a password.

Each installation has a stable server-recognized identity bound to one User while
active. Installation identity is distinct from physical hardware identifiers and
browser sessions. User reassignment requires revocation and secure clearing; it
must not transfer credentials, offline field packages, local evidence, or synchronization
state to another User.

Mobile credentials remain subordinate to current User, Membership, Tenant,
installation, offline field package, and action authority. Offline acceptance cannot be
inferred from possession of a credential.

## Passwords and recovery

Waiotech stores approved non-recoverable password verification material, never
recoverable passwords. Hash parameters are versioned and upgraded only after
successful verification.

Account recovery is a protected credential lifecycle with non-enumerating public
responses, bounded attempts, expiry, one-time use, session revocation, audit, and
safe delivery. The complete delivery and operator contract must exist before an
application exposes it.

Admin User creation and password reset generate a server-created temporary password
with bounded expiry. It is returned only by the first successful command execution,
never persisted in command receipts or audit evidence, and cannot be recovered on an
idempotent replay. All existing sessions are revoked on reset. The User must replace
the temporary password before Dashboard or Admin work can continue; successful
replacement revokes the temporary session as well. Alpha uses one-time operator handoff as the governed delivery contract. The temporary password is displayed exactly once to the authorized platform Actor performing the command, must be handed to the User through an organization-approved channel outside Waiotech, and is never recoverable from Waiotech afterward. Waiotech does not claim to secure the external handoff channel.

## Session invalidation

User disablement, Membership end, Tenant lifecycle, credential compromise,
password recovery, platform account changes, Android installation revocation, Integration Principal revocation or credential rotation,
and mobile-session revocation invalidate all affected sessions and cached
authority paths. Uncertainty fails closed.

## Secret handling

Credentials, hashes, cookies, tokens, signing keys, encryption keys, delegated
URLs, storage credentials, and database credentials remain outside source code,
frontend bundles, logs, traces, metrics, error messages, generated documentation,
and release manifests.

## Audit

Audit covers authentication, recovery, User and Membership lifecycle, Team
Membership changes, Access Profile publication and assignment, session
revocation, Android installation and mobile-session changes, Integration Principal and machine-credential changes, protected platform
recovery, and security-policy changes.

## Related documents
- [Authorization architecture](010-authorization-architecture.md)
- [Authorization enforcement](020-authorization-enforcement.md)
- [Android installation and offline authority](../60-applications/030-android-installation-and-offline-authority.md)
