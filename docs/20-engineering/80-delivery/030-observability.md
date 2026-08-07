# Observability

Observability provides protected, non-authoritative evidence through structured logs, metrics, traces, exception reporting, alerts, runbooks, and surface-specific monitoring without exposing secrets or Tenant data.

## Observability

Diagnose implementation without creating another source of business truth.

Observability is protected technical evidence used to understand Waiotech behavior, health, performance, and failure through structured logs, metrics, traces, exceptions, health, deployment identity, and alerts. It does not replace product evidence or audit.

## Operational security

Waiotech must protect Product Authority guarantees throughout operation.

Operational security protects build, deployment, runtime, support, monitoring, backup, recovery, and incident handling through least privilege, runtime identity, secret management, network boundaries, vulnerability management, containment, and evidence.

## Keep observability non-authoritative while preserving mandatory audit

Waiotech must keep observability non-authoritative while preserving mandatory audit.

External logging, metrics, tracing, or exception-reporting failure does not invalidate a safe command. Required domain evidence and security audit remain durable in PostgreSQL and do not depend on telemetry delivery.

## A structured log

Emit machine-readable bounded diagnostic records.

A structured log uses a stable event name and typed bounded fields such as timestamp, severity, service, runtime role, release identity, safe message, request, correlation, operation, scope, route or work type, outcome, duration, and safe exception class.

## Prevent observability from becoming a sensitive-data replica

Waiotech must prevent observability from becoming a sensitive-data replica.

Passwords, hashes, cookies, tokens, reset credentials, keys, authorization headers, delegated URLs, database credentials, unrestricted request or response bodies, Attachment content, sensitive report or export content, scanner detail, and unnecessary personal information are prohibited.

## Stop sensitive data at the source

Stop sensitive data at the source.

Redaction occurs before emission through typed secret wrappers, header and query filtering, safe exception serialization, payload suppression, size limits, and automated tests. Post-ingestion cleanup is not the primary control.

## Treat telemetry leakage as credential exposure

Waiotech must treat telemetry leakage as credential exposure.

The condition is a security incident: access is restricted, the secret is revoked or rotated, exposed records are handled under the incident contract, the logging path is corrected, and evidence is preserved.

## Preserve diagnosis without creating enumeration and cardinality risk

Waiotech must preserve diagnosis without creating enumeration and cardinality risk.

A safe stable Tenant reference may appear when necessary. Names and business content are minimized. Tenant identifiers are not general metric labels.

## Make one flow reconstructable across synchronous and asynchronous boundaries

Waiotech must make one flow reconstructable across synchronous and asynchronous boundaries.

Requests, operations, receipts, events, durable work, attempts, external messages, audit, logs, and traces preserve applicable request, correlation, operation, causation, and trace identities.

## Protect metric systems from data leakage and cardinality failure

Waiotech must protect metric systems from data leakage and cardinality failure.

Labels are bounded, low-cardinality, stable, non-secret, and non-personal. Record IDs, User IDs, Tenant IDs, filenames, arbitrary URLs, exception messages, and raw error text are prohibited.

## Measure traffic, latency, errors, saturation, and software identity

Measure traffic, latency, errors, saturation, and software identity.

Metrics cover request rate, latency, errors, active work, resource saturation, database pools and locks, Redis latency, storage latency, dependency latency, readiness, restart, and release identity.

## Detect stalled, duplicated, and failing execution

Detect stalled, duplicated, and failing execution.

Metrics cover command attempts, outcomes, replays, conflicts, transaction duration, durable work, queue delay, claims, leases, retries, dead letters, execution duration, and payload incompatibility.

## Observe obligations without replacing governed reporting

Observe obligations without replacing governed reporting.

Metrics cover outbox age, Report Runs, artifact failures, Notification delivery, integration acknowledgements and reconciliation, Attachment processing and integrity, backup, restoration, and migration health. They exclude product content and recipient identity.

## Distributed tracing used for

Diagnose cross-component latency without collecting unrestricted payloads.

Tracing follows technical execution across API, Application, PostgreSQL, outbox, Worker, storage, and approved external providers. Sampling is controlled and biased toward errors or latency where appropriate.

## Apply the same data-minimization rules to traces and logs

Waiotech must apply the same data-minimization rules to traces and logs.

Bodies, secret values, SQL parameters, file content, sensitive report parameters, high-cardinality span names, and unnecessary personal identity.

## Keep business rejection separate from technical defects

Waiotech must keep business rejection separate from technical defects.

Unexpected technical failures belong in exception aggregation. Expected validation, Permission denial, lifecycle rejection, concurrency conflict, and idempotency conflict use typed results and metrics rather than exceptions.

## An alert

Alert on actionable threats rather than raw noise.

An alert is an actionable signal identifying affected responsibility, severity, condition, release, environment, safe diagnostics, runbook, and escalation. Individual expected errors do not each create an alert.

## Alert on risks to truth, security, and recoverability

Alert on risks to truth, security, and recoverability.

Alerting covers Tenant isolation, authentication and authorization failure, integrity mismatch, durable obligation risk, outbox and Worker recovery, database and storage capacity, backup and restore failure, integration uncertainty, secret exposure, deployment incompatibility, and monitoring-path failure.

## Connect detection to safe response

Connect detection to safe response.

Recurring actionable alerts reference protected runbooks defining verification, containment, recovery, escalation, evidence, and prohibited actions.

## Monitor public reliability and discoverability without expanding data collection

Monitor public reliability and discoverability without expanding data collection.

Monitoring covers availability, response status, redirect failure, certificate validity, deployment identity, page-template performance, asset errors, sitemap and robots availability, public-form failure, and privacy-safe aggregate traffic. It excludes cross-surface identity tracking and protected Tenant information.

## Make field synchronization diagnosable without copying field evidence into telemetry

Waiotech must make field synchronization diagnosable without copying field evidence into telemetry.

Protected telemetry covers application version, installation-safe identity, authentication failure class, Work Package issuance and verification, local journal count, synchronization delay, accepted, rejected, and reconciliation-required operations, file-transfer failure, revocation delivery, crash class, and update-required results.

Telemetry excludes Work Order narrative, measurements, photographs, file content, credentials, and unrestricted local records.

## Alert on mobile conditions that threaten authority, evidence, or recoverability

Alert on mobile conditions that threaten authority, evidence, or recoverability.

Alerts cover abnormal package-signing failure, package-verification failure, widespread synchronization delay, elevated reconciliation or rejection, loss of mobile authentication, incompatible app-version concentration, push-registration failure affecting required field communication, and evidence-transfer backlog threatening package or local-storage limits.

## Detect any failure that could make learning unsafe, misleading, or operationally visible

Detect any failure that could make learning unsafe, misleading, or operationally visible.

Monitoring covers private test-Tenant provisioning, seed verification, mission completion evaluation, reset, side-effect-fence violations, real-provider delivery attempts, cross-purpose query attempts, documentation compatibility, and onboarding-progress persistence.

## Preserve operational metric meaning by excluding synthetic learning activity

Waiotech must preserve operational metric meaning by excluding synthetic learning activity.

Learning-purpose activity is separated by default. A protected engineering dashboard may inspect learning metrics explicitly, but operational service and business metrics do not silently include synthetic activity.

## An incident

Waiotech must use one accountable incident process for material operational and security failures.

An incident threatens or causes unacceptable availability, integrity, confidentiality, Tenant isolation, authority enforcement, recoverability, or external consistency. It preserves identity, timeline, affected scope, containment, evidence, recovery, reconciliation, and closure basis.

## Containment

Waiotech must use explicit reversible controls to contain risk.

Containment limits harm through readiness removal, credential or session revocation, write fencing, Worker pause, external-delivery pause, quarantine, endpoint restriction, or maintenance mode. It does not rewrite product history.

## Preserve evidence and product correction semantics during incidents

Waiotech must preserve evidence and product correction semantics during incidents.

Only through a protected correction or recovery procedure with authorization, recovery point, exact statements, audit, verification, and reconciliation. Urgency does not create a hidden authority path.

## Treat telemetry platforms as protected data systems

Waiotech must treat telemetry platforms as protected data systems.

Individually authenticated engineering, security, audit, or support operators receive least privilege, environment scope, Tenant-sensitive restrictions, strong authentication, expiry, audit, and revocation. Shared accounts are prohibited.

## Keep infrastructure diagnosis separate from product authority

Waiotech must keep infrastructure diagnosis separate from product authority.

Diagnostic access does not authorize Tenant actions, file retrieval through product contracts, Decisions, IAM changes, or ordinary commands.

## Bound Tenant diagnosis by purpose and least access

Bound Tenant diagnosis by purpose and least access.

An authorized operator may filter by safe Tenant reference for an explicit support or incident purpose, with unrelated data excluded, access audited, and exported diagnostics minimized.

## Keep operational telemetry retention separate from product retention

Waiotech must keep operational telemetry retention separate from product retention.

An Operations Standard defines retention for logs, metrics, traces, alerts, and incident evidence based on diagnostic need, security, privacy, cost, and obligations. It does not authorize deletion of product audit or governed evidence.

## Related documents
- [Operational security](040-operational-security.md)
- [Background work](../50-background-processing/010-background-work.md)
- [Audit, events, and transactional outbox](../30-actions-and-contracts/020-audit-events-and-outbox.md)
