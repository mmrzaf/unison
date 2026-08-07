# Operational security

Runtime identities, database roles, networks, transport, browser controls, secrets, dependencies, time, shell access, break-glass actions, monitoring, denial-of-service controls, environment separation, drift detection, and user-facing failures are explicitly governed.

## Separate operational authority by responsibility

Waiotech must separate operational authority by responsibility.

API, Worker, Public Website delivery, migration, backup, restore, deployment, monitoring collection, file processing, onboarding control, learning-data-plane services, and other separated responsibilities use distinct technical identities and least privileges. Android application installations use User-bound mobile identity rather than infrastructure credentials.

## Prohibit schema and superuser authority in application processes

Waiotech must prohibit schema and superuser authority in application processes.

API and Worker use role-specific application privileges. Migration and recovery privileges are isolated and unavailable to ordinary runtime.

## Keep infrastructure outside the public trust boundary

Waiotech must keep infrastructure outside the public trust boundary.

Ingress exposes only Public Website, approved Dashboard and Platform Admin delivery, and approved API surfaces. Android Work App reaches only the approved API and public distribution endpoints. The learning data plane uses separate ingress and cannot reach operational product APIs or data stores. PostgreSQL, Redis, private storage, monitoring, backup, and administrative interfaces remain private. Network policy is deny-by-default and permits only required paths.

## Encrypt sensitive transport and authenticate endpoints

Encrypt sensitive transport and authenticate endpoints.

Authenticated encryption protects Public Website, browser applications, Android API traffic, learning-session exchange, integrations, telemetry forwarding, backup transfer, secret retrieval, and administration across trust boundaries.

## Use browser platform controls as layered defense

Waiotech must use browser platform controls as layered defense.

Serving components apply content security, frame restrictions, MIME-sniff prevention, referrer policy, secure transport, permissions policy, cache controls, and cross-origin protections as applicable.

## Disable unsafe debug behavior in protected environments

Disable unsafe debug behavior in protected environments.

Interactive consoles, stack traces, source, configuration, environment variables, SQL, and internal routes remain unavailable to untrusted users.

## Separate secret lifecycle from software artifacts

Waiotech must separate secret lifecycle from software artifacts.

Secrets are injected or retrieved through protected channels, scoped to one identity, excluded from command-line exposure and logs, and rotatable without rebuilding images. Rotation defines overlap, activation, revocation, reload, verification, and audit.

## Treat third-party code as part of the security boundary

Waiotech must treat third-party code as part of the security boundary.

Server, browser, Public Website, Android, build, and operational dependencies use manifests, lock files, verified sources, scanning, software bills of materials, reproducible builds, licence controls where applicable, update testing, and removal of unused components.

## Require explicit time-bounded risk acceptance

Waiotech must require explicit time-bounded risk acceptance.

Only through a bounded deviation recording vulnerability, exposure, compensating controls, owner, expiry, and verification. Product Authority guarantees cannot be waived.

## Preserve ordering, expiry, and evidence time integrity

Waiotech must preserve ordering, expiry, and evidence time integrity.

Application hosts, PostgreSQL, Redis, logging, and monitoring use controlled synchronization and drift monitoring. Security expiry, effective authority, leases, and recorded time use trusted backend clocks.

## Prevent operational tools from creating hidden product paths

Waiotech must prevent operational tools from creating hidden product paths.

Controlled CLI tools use attributable identity, explicit environment and scope, Application contracts where possible, reasons, audit, and secret redaction. Shell access is exceptional and cannot become ordinary product administration.

## Break-glass access

Waiotech must make emergency authority explicit and accountable.

Break-glass is exceptional time-bounded infrastructure authority used when ordinary recovery paths cannot protect the system. It requires named identity, strong authentication, purpose, scope, approval where feasible, complete audit, credential revocation, and review.

## Detect attempts to bypass identity, isolation, evidence, and recovery controls

Detect attempts to bypass identity, isolation, evidence, and recovery controls.

Monitoring detects authentication abuse, revoked credential use, suspicious support activity, cross-Tenant attempts, denial spikes, download anomalies, secret access anomalies, superuser use, unauthorized migration, integrity mismatch, signature failure, and backup or telemetry access anomalies.

## Do not create hidden automated authority

Waiotech must not create hidden automated authority.

Only through an explicit security-response contract defining trigger, false-positive handling, scope, duration, restoration authority, and audit. Otherwise it alerts authorized responders.

## Fail safely under resource pressure

Fail safely under resource pressure.

Ingress rate limits, authentication limits, request and upload limits, query and pagination bounds, Worker concurrency, Tenant fairness, timeouts, and safe circuit breakers bound resource consumption. Overload never becomes silent acceptance.

## Prevent one environment from affecting another

Waiotech must prevent one environment from affecting another.

Development, test, rehearsal, and protected operation use separate credentials, databases, Redis, storage, external destinations, secrets, deployment identities, and observability access.

## Detect unreviewed operational change

Detect unreviewed operational change.

Active service configuration, secret references, network policy, database roles, storage policy, monitoring rules, release identity, and migration head are compared with approved configuration sources.

## Separate supportable user information from infrastructure disclosure

Waiotech must separate supportable user information from infrastructure disclosure.

Users receive stable safe error code, message, correlation reference, operation identity where applicable, and retry guidance. Technical detail remains in protected diagnostics.

## Make operational controls executable and rehearsed

Waiotech must make operational controls executable and rehearsed.

Tests cover log structure, redaction, correlation, bounded metrics, tracing, alerts, monitoring failure, runtime privileges, private networks, TLS, security headers, debug absence, secret rotation, break-glass audit, environment separation, and incident exercises.

## Related documents
- [Authentication, sessions, and secrets](../40-security/030-authentication-sessions-and-secrets.md)
- [Observability](030-observability.md)
- [Testing and software release](050-testing-and-release.md)
