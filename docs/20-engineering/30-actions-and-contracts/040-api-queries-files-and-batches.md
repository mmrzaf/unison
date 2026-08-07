# API queries, files, batches, and revisions

Query, search, sorting, pagination, freshness, conditional access, offline synchronization, file transfer, batch commands, cancellation, revisions, catalogues, and localization use bounded explicit contracts rather than generic expression languages.

## Treat reads as governed Application contracts

Waiotech must treat reads as governed Application contracts.

Every query defines Tenant scope, Permission, filters, sorting, pagination, representation, freshness, authoritative sources, and limits. Relationship-scoped views such as the current principal's effective Teams are computed by the owning server module; clients do not infer completeness by loading an arbitrary page and filtering locally.

## Keep query semantics resource-owned and bounded

Waiotech must keep query semantics resource-owned and bounded.

Ordinary resources expose typed allow-listed filters. A Report Type may define a closed typed analytical expression contract, but unrestricted query languages are prohibited.

Multi-value lifecycle filtering uses repeated, closed-enum query parameters such as `states`. Relationship filters remain resource-owned and explicit, such as Work Order responsible Team IDs or Failure Event linked Work Order IDs. A client that needs the complete filtered collection follows the published pagination contract until `has_more` is false; it must not load one arbitrary maximum page and treat it as complete.

## Use deterministic sorting safe for pagination

Waiotech must use deterministic sorting safe for pagination.

Each endpoint allow-lists indexed meaningful sort keys, direction, null order, and immutable identity tie-breaker. Unsupported fields are rejected.

## Use stable continuation semantics

Waiotech must use stable continuation semantics.

Mutable operational collections use opaque integrity-protected cursor pagination bound to Tenant, filters, sort, and last ordering values. Bounded stable collections may use offset pagination when the contract states its limitations.

## Return counts only when justified

Return counts only when justified.

The endpoint declares exact, estimated, or absent count semantics based on cost and disclosure risk.

## Treat search as a protected query contract

Waiotech must treat search as a protected query contract.

Search declares searchable facts, normalization, ranking, Tenant scope, sensitivity, limits, and authorization. A global search cannot reveal unauthorized existence.

## Make consistency part of the API contract

Waiotech must make consistency part of the API contract.

A query is classified as authoritative transactional, derived, cache-backed, or replica-backed lag-tolerant. Commands and Readiness use authoritative facts.

## Use explicit preconditions for stale-write protection

Waiotech must use explicit preconditions for stale-write protection.

Reads may return ETag. Mutable commands require one consistent expected-version mechanism, ordinarily `expected_version` or `If-Match`. Ambiguous duplicate preconditions are rejected.

## Standardize retry identity at the HTTP boundary

Standardize retry identity at the HTTP boundary.

Externally retryable mutations use an `Idempotency-Key` header or exact equivalent. The generated client preserves it for the logical submission. GET does not use mutation receipts.

## Make flows traceable without trusting diagnostic input as authority

Waiotech must make flows traceable without trusting diagnostic input as authority.

The server creates request identity and operation identity. Clients may provide a validated correlation identity. Responses return applicable request, correlation, operation, and replay metadata through documented headers.

## Make client contract identity explicit without treating it as authority

Waiotech must make client contract identity explicit without treating it as authority.

Browser and Android clients send bounded application identity, application version, and contract version through approved headers. These values support telemetry and minimum-version enforcement but grant no authority and cannot replace authentication or Tenant context.

## Expose offline field work through explicit versioned contracts

Waiotech must expose offline field work through explicit versioned contracts.

Synchronization uses typed endpoints for Work Package and Process Package retrieval, operation-envelope submission, receipt retrieval, conflict handling, and Attachment transfer. It does not use a generic entity replication or arbitrary change-feed endpoint.

## Keep transfer mechanics separate from governed file meaning

Waiotech must keep transfer mechanics separate from governed file meaning.

Attachment APIs separate upload initiation, byte transfer, verification, Attachment acceptance, linking, and retrieval. Large bytes do not travel inside ordinary JSON commands.

## Make batch semantics explicit

Waiotech must make batch semantics explicit.

Each batch action has a typed module-owned contract declaring atomic or item-independent behavior and item-level outcomes. A generic action dispatcher is prohibited.

Machine Process Reading batches are item-independent unless the Process contract explicitly defines an atomic source sequence. Each item preserves Measurement Point resolution, source event identity where available, effective time, value, unit, quality, provenance, and duplicate-safe outcome. One invalid Reading must not cause unrelated valid Readings to be falsely reported as accepted or silently discarded. Batch size, payload size, rate, and source scope are bounded.

## Preserve domain vocabulary at the API boundary

Waiotech must preserve domain vocabulary at the API boundary.

Named Product Authority actions are used, such as `cancel`, `retire`, `withdraw`, or `revoke`. HTTP DELETE does not disguise lifecycle.

## Do not expose revision administration as CRUD

Waiotech must not expose revision administration as CRUD.

Stable definitions are the ordinary resources. Published revisions are read-only history and evidence. Editing uses named stable-object actions.

## Reflect catalogue governance in API shape

Reflect catalogue governance in API shape.

Through generated schema values or read-only metadata endpoints. Tenant CRUD is prohibited. Tenant-governed catalogues use explicit domain routes.

## Localize presentation without changing identity

Localize presentation without changing identity.

Standard language negotiation may select presentation labels. Canonical codes and machine contracts remain unchanged. The governed fallback locale applies.

## Related documents
- [API contracts](030-api-contracts.md)
- [OpenAPI and generated clients](050-openapi-and-generated-clients.md)
- [File ingestion and storage](../70-files/010-file-ingestion-and-storage.md)
