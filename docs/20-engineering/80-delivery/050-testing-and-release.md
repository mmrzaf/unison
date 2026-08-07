# Testing and software release

Testing verifies Product and Engineering Authority across Domain, Application, PostgreSQL, API, generated contracts, architecture, lifecycle, isolation, idempotency, concurrency, background work, integrations, files, applications, security, performance, recovery, and release activation.

## The primary testing principle

Test contracts at their owning layer and verify their composition.

Each guarantee is tested at the lowest layer that can prove it completely, and cross-layer composition is tested at the boundaries where several guarantees interact. Tests verify authority and do not create product meaning.

## Maintain complementary tests across product, implementation, operation, and recovery

Waiotech must maintain complementary tests across product, implementation, operation, and recovery.

The required layers are static validation, Domain, Application, PostgreSQL, API, generated contract, architecture, Worker and outbox, migration, security, frontend unit and component, frontend integration, accessibility, end-to-end, performance, deployment, backup and recovery, and release-artifact verification.

## Align test ownership with contract ownership

Align test ownership with contract ownership.

The module or engineering concern owning the contract owns its assertions. Shared infrastructure may provide PostgreSQL, Redis, storage, clock, identity, API, browser, and assertion helpers but cannot hide domain expectations.

## Test actual product behavior without mocking the Domain

Test actual product behavior without mocking the Domain.

Domain tests use real aggregates, entities, value objects, and services without framework infrastructure. They verify lifecycle, invariants, Decisions, calculations, evidence, correction, and code interpretation.

## Test use-case behavior at the Application boundary

Test use-case behavior at the Application boundary.

Application tests verify commands and queries, Tenant context, authorization, Domain invocation, transaction orchestration, cross-module consequences, audit, receipts, outbox, results, and errors.

## Use the production database engine for persistence guarantees

Waiotech must use the production database engine for persistence guarantees.

They use PostgreSQL whenever correctness depends on transactions, constraints, locks, isolation, receipts, outbox, Tenant-aware foreign keys, repositories, or migration-created structures.

## Test authoritative persistence on PostgreSQL

Test authoritative persistence on PostgreSQL.

SQLite cannot prove PostgreSQL types, constraints, locking, transactions, concurrency, indexing, or SQL semantics.

## Test the deployable schema

Test the deployable schema.

Test schema comes from the authoritative migration chain or an automatically verified snapshot proven equivalent to it. ORM table creation alone is insufficient.

## Test the public contract through the real API boundary

Test the public contract through the real API boundary.

API tests exercise actual authentication integration, Tenant context, authorization, routes, schemas, unknown-field rejection, idempotency, concurrency, status, errors, headers, information disclosure, and OpenAPI compatibility.

## Treat negative behavior as a first-class contract

Waiotech must treat negative behavior as a first-class contract.

Tests cover absent or invalid authentication, missing or invalid Tenant, cross-Tenant identity, denied Permission, invalid input, unknown field, retired code, stale version, idempotency conflict, unavailable action, unsafe file access, malformed cursor, unsupported query, rate limit, and safe unexpected failure.

## Fail the build on generated drift

Fail the build on generated drift.

They compare OpenAPI, generated clients, catalogues, Permissions, actions, events, localization, documentation, and database reference representations with their canonical sources.

## Make architecture executable

Waiotech must make architecture executable.

They enforce layer dependencies, module privacy, API and Worker invocation of Application contracts, generated client use, application separation, schema-change boundaries, absence of generic engines, catalogue governance, storage ports, and acyclic module dependencies.

## Keep lifecycle behavior fully enumerated

Waiotech must keep lifecycle behavior fully enumerated.

Each transition has successful execution, incompatible-state rejection, missing-authority rejection, missing-requirement rejection, concurrency behavior, audit, and event verification where applicable.

## Verify one authoritative action-specific Readiness evaluation

Waiotech must verify one authoritative action-specific Readiness evaluation.

Tests cover authoritative inputs, every blocker class, expiry, policy, evidence availability, reevaluation after fact changes, and distinction from historical lifecycle authorization.

## Prove isolation even with globally unique identifiers

Prove isolation even with globally unique identifiers.

Every Tenant-owned resource family is tested against valid identifiers belonging to another Tenant for reads, writes, links, receipts, cursors, files, audit, selectors, processes, reports, and errors.

## Verify platform authority as a separate bounded path

Waiotech must verify platform authority as a separate bounded path.

Tests prove dedicated platform scope, exact platform Permissions, bounded Tenant inspection, separation of duties, real-Actor attribution, no Membership creation, no Tenant impersonation, no ordinary Tenant action, and no infrastructure-access grant.

## Test system actions independently from human IAM

Product-defined internal system actions are tested against their exact Worker job, initiating evidence, Tenant scope, idempotency, audit, retry, failure, and prohibition on reuse as generic automation authority.

## Prove one result for one logical attempt

Prove one result for one logical attempt.

Tests cover first execution, equivalent replay, conflicting reuse, concurrent duplicate, response loss after commit, failure before commit, abandoned claim, retryable failure, rejected replay, uncertainty, and receipt retention boundary.

## Test real races rather than sequential approximations

Test real races rather than sequential approximations.

Tests use actual parallel PostgreSQL transactions for stale versions, simultaneous updates, unique races, row locks, advisory locks, deadlocks, serialization retry, process claims, publication, stock posting, and Plan publication drift.

## Make time deterministic and explicit

Waiotech must make time deterministic and explicit.

Time-dependent behavior uses an injected trusted clock or controlled database boundary. Tests cover effective periods, Tenant timezone, local schedules, daylight-saving transitions where applicable, leases, sessions, report cutoff, and occurred versus recorded time.

## Prevent machine-speed-dependent tests

Waiotech must prevent machine-speed-dependent tests.

Use barriers, condition polling, controlled clock advancement, transaction observation, or explicit event synchronization.

## Keep test determinism without weakening production entropy

Waiotech must keep test determinism without weakening production entropy.

Production uses secure randomness. Tests inject deterministic sources where repeatability is needed and use property or statistical verification where distribution matters.

## Test interruption and recovery as primary behavior

Test interruption and recovery as primary behavior.

Tests cover queue loss, duplicate delivery, Worker death before and after commit, lease expiry and renewal, retries, dead letters, replay, Tenant context, payload schema compatibility, source/outbox atomicity, consumer deduplication, and ordering where required.

## Test files as hostile input and immutable evidence

Test files as hostile input and immutable evidence.

Tests cover upload claims, hostile filenames, streaming, limits, type spoofing, digest, scanner failure, quarantine, governing access, correction, unavailable bytes, storage conformance, Report Artifact, Tenant Export Artifact, partial-generation rejection, and recovery.

## Test migration as transformation of authoritative state

Test migration as transformation of authoritative state.

Tests cover empty installation, upgrade from every supported schema state, populated data, mapping, Tenant constraints, lifecycle, revisions, catalogues, receipts, outbox, files, batching, failure resume, compatibility overlap, and post-migration product behavior.

## Verify every user-facing surface without moving product authority into a client

Waiotech must verify every user-facing surface without moving product authority into a client.

Dashboard and Platform Admin tests cover formatting and view models, generated clients, session and Tenant context, cache invalidation, forms, actions, concurrency, idempotency recovery, files, reports, localization, accessibility, and protected browser journeys.

Public Website tests cover English and Persian routes, directionality, metadata, canonical and locale links, structured data, sitemap, robots directives, redirects, not-found behavior, content parity, accessibility, security headers, and performance budgets.

Android tests cover User and installation binding, authentication, encrypted storage, Room migration, disconnected Work Request drafts, Work Package authenticity and expiry, journal sequencing and correction, scanning, file capture, bounded material use, synchronization retry, duplicate submission, conflicts, revocation, app upgrade, accessibility, and unsynchronized-evidence recovery.

Help and onboarding tests cover content visibility, bilingual parity, contextual links, mission schemas, authoritative completion evaluation, private test-Tenant isolation, side-effect fencing, progress separation, and deterministic reset.

## Treat public discoverability, accessibility, privacy, and performance as release contracts

Waiotech must treat public discoverability, accessibility, privacy, and performance as release contracts.

The release gate verifies Astro static generation, bilingual content completeness, no accidental private content, canonical and `hreflang` correctness, sitemap and robots output, structured-data validity, redirects, accessibility, security headers, media optimization, JavaScript and asset budgets, and measured page performance on representative templates.

## Verify offline field execution under conflict, interruption, and hostile input

Waiotech must verify offline field execution under conflict, interruption, and hostile input.

Tests cover loss before and after local commit, duplicate synchronization, out-of-order delivery, expired and revoked package, altered package, wrong User or installation, wrong Tenant, clock anomaly, Work Order cancellation, release withdrawal, Procedure change, another Actor completion, material conflict, file-transfer interruption, app termination, device restart, app upgrade, token revocation, and server-version incompatibility.

## Prove learning isolation through negative and side-effect tests

Prove learning isolation through negative and side-effect tests.

Tests prove that guided missions cannot access or affect operational Tenant data, unrelated Users, real email or push, billing, operational Reports, search, exports, platform queues, or external files. Reset is tested against wrong-Tenant identifiers and concurrent mission activity.

## Combine automated and manual accessibility verification

Combine automated and manual accessibility verification.

Keyboard completion, focus order, labels, announcements, cognitive clarity, and screen-reader workflows require human interaction testing.

## Test security across application and operational boundaries

Test security across application and operational boundaries.

Tests cover authentication, fixation, CSRF, CORS, cookies, authorization, Tenant isolation, support, credentials, enumeration, rate limits, redaction, file attacks, injection, XSS, redirects, headers, signatures, dependencies, runtime privileges, backup, and observability access.

## Apply expert review to security boundaries

Waiotech must apply expert review to security boundaries.

It is required for changes affecting authentication, authorization, Tenant isolation, cryptography, credentials, files, integrations, support, audit, backup, recovery, privileged operation, or network exposure.

## Make declared performance limits executable

Waiotech must make declared performance limits executable.

Performance tests cover declared latency, transaction duration, query behavior, pools, Worker throughput, outbox recovery, selector resolution, Plan publication, reports, exports, files, Notifications, integrations, and frontend responsiveness.

## Keep environment-specific numbers scoped while requiring measurable guarantees

Waiotech must keep environment-specific numbers scoped while requiring measurable guarantees.

Numerical objectives belong in Experience Contracts, Operations Standards, or scoped ADRs. Mandatory limits identified there become release checks.

## Verify truthful behavior under infrastructure failure

Waiotech must verify truthful behavior under infrastructure failure.

Isolated resilience tests cover PostgreSQL, Redis, Worker, storage, scanner, external timeout, partial acknowledgement, network interruption, capacity pressure, lease expiry, telemetry outage, and backup-component failure.

## Do not accept backup-job success as recovery proof

Waiotech must not accept backup-job success as recovery proof.

A complete isolated restore rehearsal verifies database, files, integrity, schema, authentication, Tenant isolation, Workers, outbox, processes, integrations, projections, and service activation.

## Keep tests isolated from protected operational data

Waiotech must keep tests isolated from protected operational data.

Test data is synthetic, purpose-specific, deterministic where required, Tenant-separated, secret-free, and safe for logs and screenshots. Operational data requires a protected handling procedure.

## Make tests independent and reproducible

Waiotech must make tests independent and reproducible.

Each test establishes prerequisites. Parallel execution isolates databases or schemas, Redis keys, storage objects, stubs, ports, browser sessions, and Tenant identities.

## A test double

Mock mechanisms, not authoritative behavior.

A fake, stub, spy, mock, or emulator replaces an outbound mechanism. It must preserve the behavior relevant to the contract. Product rules are not mocked away.

## Prevent easier test semantics than production

Waiotech must prevent easier test semantics than production.

When Application tests rely on a test double for a defined port, the same contract test suite verifies the test double and the production adapter.

## A flaky test

Waiotech must treat flakiness as a release-quality defect.

A flaky test passes or fails without a relevant contract or implementation change. It is a test defect or evidence of a race. Rerunning until pass does not erase the failure.

## Prohibit suppression of required release tests

Waiotech must not suppress a required release test.

A failing or unavailable required test blocks the release until the test environment or implementation is corrected. A test may be replaced only in the same change by stronger executable verification of the same guarantee.

## Combine quantitative coverage with explicit critical-path tests

Combine quantitative coverage with explicit critical-path tests.

Coverage detects untested code but does not prove correctness. Repository thresholds may apply to statements, branches, critical packages, and changed code. Mandatory scenarios remain required regardless of percentage.

## Require direct evidence for high-impact guarantees

Waiotech must require direct evidence for high-impact guarantees.

They include Tenant isolation, IAM, account recovery, Work Request acceptance, Work Order lifecycle, emergency execution, Plan publication and drift, Scheduled Work duplicate prevention, Inventory posting, reservations, Stocktaking, Access Profile publication, idempotency, transactions, outbox, Worker recovery, Attachments, reports, Tenant Export, migration, and restoration.

## Reject detectable defects before runtime tests

Waiotech must reject detectable defects before runtime tests.

Formatting, linting, strict backend and frontend typing, import boundaries, unsafe pattern checks, generated drift, secrets, dependencies, migration graph, API schemas, localization, and builds are mandatory.

## Treat code, schema, contracts, security, and docs as one release surface

Waiotech must treat code, schema, contracts, security, and docs as one release surface.

CI verifies static checks, all applicable test layers, PostgreSQL integration, generated artifacts, migrations, security, accessibility automation, frontend and container builds, dependency provenance, documentation, and release-manifest consistency.

## Optimize feedback without skipping relevant verification

Optimize feedback without skipping relevant verification.

Only when dependency analysis proves the omitted tests cannot be affected. Protected release branches satisfy the complete gate for the change class.

## Require human review and executable verification

Waiotech must require human review and executable verification.

Review evaluates authority alignment, design, security, migration, test sufficiency, and operations. Tests provide repeatable evidence.

## A release candidate

Waiotech must evaluate exact server, web, content, and Android artifacts rather than an unfixed branch.

It is one immutable coordinated server-and-web artifact set with image digests, Public Website output, migration set, manifest, OpenAPI, generated clients and registries, documentation and mission identities, software bill of materials, test evidence, and security evidence. Each Android release candidate is one signed immutable application artifact with its own contract-version manifest and evidence.

## The software-release gate

Waiotech must not deploy an artifact set whose contracts or recovery path are unverified.

The gate requires Product and Engineering Authority alignment, mandatory CI, migration rehearsal, generated parity, security verification and review, container verification, deployment rehearsal, critical end-to-end journeys, affected performance checks, recovery verification where required, rollback assessment, manifest verification, and attributable approval.

## Preserve artifact continuity from build to operation

Waiotech must preserve artifact continuity from build to operation.

Reproducible build comparison is permitted, but deployment promotes the exact artifact that passed the gate.

## Reverify recovery when its contract changes

Reverify recovery when its contract changes.

Changes to schema, storage adapter, backup format, encryption, Attachment representation, artifact format, restoration tooling, deployment topology, or durable event and job storage require recovery verification.

## Prohibit release-gate bypass

Waiotech must not bypass the release gate.

A missing or failed required check blocks activation. Emergency urgency may reduce unrelated scope, but it does not convert an unverified artifact into an accepted release.

## An emergency release

Accelerate response without removing release accountability.

It is an accelerated immutable release used to contain or correct an active incident. It still requires attributable source, focused review, applicable tests, security checks, migration and recovery assessment, release evidence, and activation verification.

## Make release acceptance reconstructable

Waiotech must make release acceptance reconstructable.

Evidence includes release and source identity, artifact digests, migration head, manifest, CI results, tests, migration, security, performance, restoration, approvals, rollback evidence, and activation result.

## Do not operate an unverified deployment

Waiotech must not operate an unverified deployment.

The deployment is not accepted. Unsafe services leave readiness and Engineering uses a verified non-destructive rollback, roll-forward correction, or recovery under the deployment standard.

## Correct existing effects as well as recurrence risk

Correct existing effects as well as recurrence risk.

Engineering separately performs implementation correction, data correction, audit, external reconciliation, and regression verification. A test addition alone is insufficient.

## Related documents
- [Deployment and migrations](010-deployment-and-migrations.md)
- [Backup and recovery](020-backup-and-recovery.md)
