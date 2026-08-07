# Documentation, help, and learning engineering

Governed content is rendered consistently across Help Center, contextual help, Android field documentation, and guided missions. Learning-purpose Tenants run in an isolated data plane with synthetic identities, blocked side effects, resettable state, and release-aligned content.

## The governed documentation source

Waiotech must generate all help surfaces from one governed documentation source.

Documentation uses one source-controlled bilingual content system with typed metadata for:

- content identity;
- locale;
- translated counterpart;
- title and summary;
- audience;
- product surface;
- public, authenticated, field, or platform visibility;
- related canonical concepts;
- deep-link targets;
- publication condition;
- applicable product-contract version;
- review owner.

## Prevent documentation drift across surfaces

Waiotech must prevent documentation drift across surfaces.

A guide has one content identity with surface-specific rendering or explicitly related variants. Divergent duplicate content is a build failure unless the variants intentionally address different tasks or audiences.

## Render governed help safely inside Dashboard

Render governed help safely inside Dashboard.

Tenant Dashboard provides searchable server- or build-indexed content, concept and task navigation, related guides, contextual entry, language selection, and secure deep links. Content rendering uses sanitized approved Markdown or MDX output and does not execute arbitrary embedded scripts.

## Publish documentation as a verified versioned product artifact

Waiotech must publish documentation as a verified versioned product artifact.

Help content uses source-controlled review, bilingual validation, product-owner review for behavior and terminology, technical review for links and UI anchors, accessibility review, mission compatibility checks, and release-manifest identity.

A content-only release may publish compatible guidance without rebuilding the backend, but it remains an immutable identified artifact validated against the supported product contract.

## Prevent operational edits from creating unreviewed product guidance

Waiotech must prevent operational edits from creating unreviewed product guidance.

Operational storage may hold search indexes, caches, publication manifests, and progress, but canonical help content comes from reviewed governed source.

## Make help searchable without bypassing content access

Waiotech must make help searchable without bypassing content access.

Search indexes accepted documentation fields such as localized title, summary, headings, canonical terms, synonyms, error codes, roles, actions, and body text. Results respect content visibility and locale, avoid leaking protected content, and provide deterministic ranking signals.

## Decouple contextual guidance from page URL changes

Decouple contextual guidance from page URL changes.

Product routes, forms, actions, lifecycle codes, blocker codes, and error codes map to stable documentation identities. Feature code references documentation identity rather than hard-coded external URLs.

## Use deep links for safe navigation only

Waiotech must use deep links for safe navigation only.

A deep link contains a typed destination and permitted context. Navigation re-evaluates authentication, Tenant, Permission, lifecycle, and subject existence. It cannot contain credentials, protected payloads, or an instruction to execute a command automatically.

## Provide offline field guidance with the same content identity as online help

Waiotech must provide offline field guidance with the same content identity as online help.

Offline field package generation may include or reference an integrity-checked field-documentation bundle containing only applicable accepted articles and media. It is versioned, localized, installation-scoped, and non-authoritative.

## A guided-mission definition technically

Waiotech must use typed missions rather than a generic workflow scripting engine.

A guided mission is a typed source-controlled definition containing:

- mission identity and schema version;
- locale content;
- learning objective;
- test-Tenant seed identity;
- permitted product surfaces;
- typed navigation and observation steps;
- expected authoritative facts;
- completion evaluator;
- reset contract.

Mission definitions cannot contain arbitrary executable scripts or bypass product commands.

## Isolate learning execution at the data-plane boundary while preserving identical application behavior

Isolate learning execution at the data-plane boundary while preserving identical application behavior.

Learning-purpose Tenants run in a dedicated learning data plane using the same verified application release and product contracts as operational execution, with separately scoped:

- PostgreSQL database or database cluster;
- Redis and Worker queues;
- file storage;
- encryption and signing keys;
- runtime and deployment identities;
- provider adapters;
- search indexes;
- observability namespace;
- backup policy.

A deployment may share physical infrastructure only when an ADR proves equivalent credential, data, network, provider, deletion, and incident isolation. Shared tables or storage prefixes alone are insufficient proof.

## Federate learner identity without copying operational credentials or data

Federate learner identity without copying operational credentials or data.

The authenticated platform User receives a short-lived learning-session exchange bound to the same stable User identity, mission, and private test Tenant. The learning plane resolves that identity through a protected mapping without copying password material, operational sessions, broad Membership data, or operational Tenant records.

## Make synthetic counterpart Actors non-authenticating and learning-bound

Waiotech must make synthetic counterpart Actors non-authenticating and learning-bound.

Synthetic learning Users exist only in the learning data plane, carry an immutable synthetic classification, have no login credential or external delivery address, and may be invoked only by typed guided-mission simulation actions. Their identifiers are never accepted by operational APIs or migration paths.

## Enforce one-way contract compatibility without cross-data-plane product access

Waiotech must enforce one-way contract compatibility without cross-data-plane product access.

It may use public version and compatibility metadata, but it cannot query or mutate operational Tenant, User, Membership, Attachment, report, audit, or integration data.

## Provision practice through canonical Tenant isolation and explicit learner authority

Provision practice through canonical Tenant isolation and explicit learner authority.

The onboarding service creates or resets a learner-scoped Tenant with canonical `learning` purpose. It establishes synthetic data through canonical Application commands or a verified seed mechanism that produces equivalent authoritative records.

The learner receives explicit test-Tenant Membership and authority required by the mission.

## Keep onboarding practice private per User

Waiotech must keep onboarding practice private per User.

A private test Tenant belongs to one learner. A separately defined instructor-led training contract would require explicit participant isolation and is not implied by guided missions.

## Teach against canonical product execution

Teach against canonical product execution.

Dashboard and Android invoke the same APIs, Application commands, Domain rules, lifecycle, Permission evaluation, Readiness, revisions, persistence constraints, and UI components used by operational Tenants.

The implementation does not branch into simplified training rules.

## Make training isolation a server-side invariant

Waiotech must make training isolation a server-side invariant.

The canonical `learning` purpose is enforced at durable delivery boundaries. Notification providers, push delivery, billing, public export delivery, and other external effects use non-delivering training adapters or explicit simulated outcomes.

A side-effect fence is enforced in the backend, not only hidden in the UI.

## Keep synthetic learning activity outside operational interpretation

Waiotech must keep synthetic learning activity outside operational interpretation.

Operational searches, reports, billing, support queues, integration scans, and global operational metrics exclude test-purpose Tenants unless a protected platform diagnostic explicitly requests them.

## Make training data safe and unmistakably synthetic

Waiotech must make training data safe and unmistakably synthetic.

Synthetic seed definitions use stable test-only identities and visible labels. They contain no copied operational personal information, credentials, secrets, file evidence, or organization-specific data.

## Keep learning state independent from simulated business state

Waiotech must keep learning state independent from simulated business state.

A separate onboarding module stores User, mission identity, mission-definition version, resume step, completed checkpoints, completion, assessment result where permitted, and reset choices. It does not derive completion solely from mutable test-Tenant records.

## Base guided-mission completion on product outcomes

Base guided-mission completion on product outcomes.

The mission evaluator reads authoritative test-Tenant facts through typed queries and compares them with the mission's expected contract. UI clicks alone do not prove completion.

## Make reset deterministic, Tenant-bounded, and independently verifiable

Waiotech must make reset deterministic, Tenant-bounded, and independently verifiable.

Reset uses a versioned deterministic seed manifest. It fences mission activity, cancels synthetic processes, removes or supersedes synthetic records according to the reset contract, cleans generated test Attachments and artifacts, restores seed data, and verifies expected state.

It never addresses another Tenant and does not alter onboarding progress unless a separate progress-reset command is accepted.

## Execute learning reset through a purpose-checked, locked, idempotent, and verified backend process

Waiotech must execute learning reset through a purpose-checked, locked, idempotent, and verified backend process.

Reset is a named idempotent backend process that:

- authenticates the learner or governed support Actor;
- verifies `learning` purpose;
- acquires a Tenant-scoped reset lock;
- fences mission commands and Workers;
- records the reset request and seed version;
- removes or replaces only the learning Tenant's synthetic state and files;
- recreates the deterministic seed;
- verifies expected state and side-effect fences;
- records the terminal result;
- leaves onboarding progress unchanged unless a separate command requests progress reset.

An operational Tenant identifier is rejected before destructive work begins.

## Keep training reset separate from recovery infrastructure

Waiotech must keep training reset separate from recovery infrastructure.

Test reset is a product-level synthetic seed operation, not database restoration or Tenant Import.

## Release documentation and onboarding as verified product contracts

Release documentation and onboarding as verified product contracts.

A release manifest identifies compatible documentation and mission content revisions. A guide or mission that references absent routes, actions, codes, or UI anchors fails validation. Public Website, Dashboard, and Android builds consume only compatible accepted content.

## Combine structural and linguistic verification for English and Persian parity

Combine structural and linguistic verification for English and Persian parity.

Automated checks compare required counterparts, metadata, headings, canonical terms, deep links, mission steps, safety text, error-code coverage, and visibility. Human language review verifies meaning, readability, Persian directionality, and terminology.

## Treat documentation content as untrusted until validated and published

Waiotech must treat documentation content as untrusted until validated and published.

Content rendering sanitizes HTML, validates links and media, prohibits embedded secrets and executable scripts, applies content security policy, and enforces visibility before search indexing or retrieval. Public content must not link to protected raw assets.

## Related documents
- [Documentation and Help Center](../../30-experience/060-documentation-and-help.md)
- [Learning environment](../../30-experience/070-learning-environment.md)
- [Public Website engineering](020-public-website.md)
