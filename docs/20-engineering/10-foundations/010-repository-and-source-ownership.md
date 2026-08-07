# Repository and source ownership

The repository contains one coordinated Waiotech implementation with explicit deployables, module ownership, dependency direction, generated outputs, scripts, configuration examples, and review responsibility.

## The repository

Waiotech must treat the repository as implementation evidence, not as an alternate source of product meaning.

The repository is the source-controlled realization of Product Authority and Engineering Authority. It contains application source, migrations, generated sources, tests, documentation, deployment definitions, and verification tooling required to build one identifiable Waiotech release.

## Use a coordinated monorepository unless a reviewed replacement preserves every contract boundary

Waiotech must use a coordinated monorepository unless a reviewed replacement preserves every contract boundary.

Waiotech uses one coordinated repository containing:

```text
server/
dashboard/
admin/
website/
android/
docs/
deploy/
scripts/
tests/
```

The repository may use additional bounded directories when ownership remains explicit. Splitting repositories requires an ADR proving preserved release identity, generated-contract parity, security, and recoverability.

## Retain the typed Python and PostgreSQL platform unless an ADR proves an equivalent replacement

Waiotech must retain the typed Python and PostgreSQL platform unless an ADR proves an equivalent replacement.

The approved backend platform is Python 3.11 or a compatible supported Python release, FastAPI, Pydantic, async SQLAlchemy, asyncpg, Alembic, PostgreSQL, Redis, and ARQ. Typer may provide controlled CLI entry points. Approved security and observability libraries remain replaceable behind explicit engineering boundaries.

## Use explicit stable platforms suited to browser administration, public content, and native field work

Waiotech must use explicit stable platforms suited to browser administration, public content, and native field work.

Tenant Dashboard and Platform Admin use the approved TypeScript and React stack. Public Website uses Astro and TypeScript with static-first content generation. Android Work App uses native Kotlin, stable Jetpack Compose and AndroidX components, Room, WorkManager, CameraX, Android Keystore, and generated API contracts.

Every surface uses accessible platform-appropriate components without transferring product behavior into a UI framework.

## Make module and layer ownership visible in source structure

Waiotech must make module and layer ownership visible in source structure.

Backend source is organized by product module and layer. A module owns its Domain, Application, ports, infrastructure adapters, API registration, events, catalogues, migrations, and tests. Shared technical packages provide narrow primitives and cannot own product behavior.

Principal backend module ownership must remain visible for `plant`, `process`, `maintenance`, `inventory`, `reliability`, `iam`, `policy`, evidence/communications, and portability. Compatibility import aliases that preserve competing product-module vocabularies are prohibited.

## Align surface source with its responsibility without merging authority or runtime platforms

Align surface source with its responsibility without merging authority or runtime platforms.

Dashboard and Platform Admin separate application composition, module-aligned features, shared technical UI, generated contracts, and tests. Public Website separates Astro application composition, bilingual governed content, SEO generation, public forms, and tests. Android separates Compose UI, mobile use cases, local persistence, synchronization, platform adapters, generated contracts, and tests.

Each surface remains a distinct build and release tree.

## Shared code contents

Share stable mechanisms and keep product meaning module-owned.

Shared code may contain technical primitives such as identifiers, clocks, transaction interfaces, error envelopes, logging context, HTTP transport helpers, accessibility primitives, formatting, and test infrastructure.

Shared code must not contain a generic lifecycle engine, universal selector, universal policy evaluator, unowned business repository, generic reason catalogue, or cross-domain entity service.

## Keep dependency direction explicit and mechanically verified

Waiotech must keep dependency direction explicit and mechanically verified.

Domain depends only on language-level and domain-owned types. Application depends on Domain and ports. Infrastructure implements ports. API and Worker entry points invoke Application contracts. Frontends depend on generated API contracts and presentation libraries. Architecture tests reject reverse and cross-module private dependencies.

## Permit committed generated artifacts only with mandatory drift verification

Permit committed generated artifacts only with mandatory drift verification.

Generated source required for deterministic builds may be committed when the release process verifies parity. Generated runtime references may also be produced during build. Every generated file identifies its source and regeneration command.

## Use disposition labels only to state the required canonical outcome

Waiotech must use disposition labels only to state the required canonical outcome.

Static architectural assessment may classify a component as:

- Retain;
- Refactor;
- Replace;
- Retire.

These labels describe required architectural disposition, not project status. A normative document does not include completion percentages, dates, assignees, or delivery-cycle state.

## Keep source snapshots and remediation tracking outside Engineering Authority

Waiotech must keep source snapshots and remediation tracking outside Engineering Authority.

A repository assessment is protected engineering evidence bound to a source commit or release identity. It may record findings, controlling authority, observed implementation, severity, and required disposition. It is separate from the timeless authority document.

## Treat scripts as governed engineering entry points

Waiotech must treat scripts as governed engineering entry points.

Repository scripts must have one documented purpose, safe argument handling, explicit environment selection, controlled credentials, idempotent behavior where applicable, and tests for high-impact operations. A script must not become a hidden authorization or migration path.

## Keep configuration technical, validated, and secret-free in source control

Waiotech must keep configuration technical, validated, and secret-free in source control.

Example configuration contains no real secret and states which values are required, optional, security-sensitive, or environment-specific. Defaults must be safe. Product behavior cannot be hidden in deployment variables.

## Make third-party software identifiable and reproducible

Waiotech must make third-party software identifiable and reproducible.

Runtime and development dependencies are declared, resolved through lock files, scanned, and reproducibly installed. Broad unbounded version ranges are prohibited for release resolution. Dependency updates receive the same tests as affected source changes.

## Make ownership explicit without embedding individual staffing status in authority

Waiotech must make ownership explicit without embedding individual staffing status in authority.

Every module, shared package, migration area, deployment component, and authority document has an owning review group. High-impact changes require review by the applicable product, security, data, or operations owner.

## Prevent repository archaeology from becoming dual authority

Waiotech must prevent repository archaeology from becoming dual authority.

Obsolete canonical paths are removed in the change that establishes their replacement. Retained historical migrations and evidence remain immutable but are not executable alternate product models, import targets, or alternate authorities.

## Make repository conformance executable

Waiotech must make repository conformance executable.

Checks include formatting, linting, strict typing, architecture rules, generated parity, secret scanning, dependency scanning, migration graph validation, frontend API discipline, documentation generation, container builds, and mandatory tests.

## Related documents
- [System architecture](020-system-architecture.md)
- [Canonical engineering model](030-canonical-engineering-model.md)
- [Authority and precedence](../../00-governance/010-authority-and-precedence.md)
