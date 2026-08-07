# API contracts

Waiotech exposes resource-oriented reads and named action commands through explicit, Tenant-scoped, typed HTTP contracts. Representations, errors, available actions, concurrency, idempotency, and diagnostics remain stable and precise.

## Use resources for identity and named endpoints for product-significant mutations

Waiotech must use resources for identity and named endpoints for product-significant mutations.

Waiotech uses resource-oriented HTTP APIs with named business-action endpoints for protected transitions. Resources express stable concepts; actions express governed commands.

## Align API ownership with product-module ownership

Align API ownership with product-module ownership.

The module owning the product concept owns routes, schemas, commands, queries, errors, action metadata, versioning, and OpenAPI representation. Shared API code owns only technical transport standards.

## Keep HTTP transport subordinate to use-case contracts

Waiotech must keep HTTP transport subordinate to use-case contracts.

It parses and maps transport. Application and Domain own authority, lifecycle, Readiness, evidence, transactions, and events.

## Version incompatible external commitments, not deployments

Version incompatible external commitments, not deployments.

One canonical major API base is used. Before any retained external deployment, an incorrect contract is corrected directly. After a retained external deployment, an incompatible commitment requires an explicit new major version with its own complete contract and migration plan; aliases, dual shapes, and hidden translation inside the canonical version are prohibited. Application releases do not create decorative API versions.

## Use canonical Product Authority vocabulary in routes

Waiotech must use canonical Product Authority vocabulary in routes.

Paths use lowercase plural canonical nouns with hyphens, such as `/functional-locations`, `/assets`, `/process-units`, `/measurement-points`, `/process-routines`, `/process-rounds`, `/process-conditions`, `/work-orders`, `/failure-events`, `/replenishment-requests`, and `/access-profiles`. Obsolete or ambiguous terminology is prohibited.

## Make generated clients and diagnostics unambiguous

Waiotech must make generated clients and diagnostics unambiguous.

OpenAPI path parameters use explicit names such as `{work_order_id}` rather than generic `{id}`.

## Use shallow stable resource addressing

Waiotech must use shallow stable resource addressing.

Nesting is bounded to relationships whose meaning depends on the parent. Stable independently addressable entities receive their own canonical routes. Paths do not reproduce the database graph.

## Map HTTP methods to product semantics rather than generic CRUD

Map HTTP methods to product semantics rather than generic CRUD.

GET performs safe reads. POST creates resources or invokes named commands. PATCH is limited to typed bounded edits of explicitly mutable non-lifecycle fields. PUT is used only for true complete replacement. DELETE is used only for disposable technical data or Product Authority physical deletion.

## Prohibit storage-shaped mutation APIs

Waiotech must prohibit storage-shaped mutation APIs.

Each edit uses a typed request contract. Lifecycle, derived state, authority, and immutable evidence are never assignable through patch documents.

## Never accept client assertions of authority or derived state

Waiotech must never accept client assertions of authority or derived state.

Clients submit legitimate business input, selected canonical identities, reasons, notes, expected version, idempotency key, and supported effective-time facts. The backend derives Actor, Tenant validity, Permission, lifecycle result, Readiness, audit, and event metadata.

## Require one validated Tenant or platform context per operation

Waiotech must require one validated Tenant or platform context per operation.

One consistent explicit mechanism, such as a dedicated header or route boundary, identifies the requested Tenant. The backend validates it against the authenticated principal. Platform operations use explicit platform routes and scope.

## Keep machine Process Reading ingress explicit

Machine Reading submission uses an explicit Process-owned API contract authenticated as an Integration Principal.

The transport may support bounded single and batch submission, but each accepted Reading is validated against canonical Measurement Point identity or a governed Measurement Source Mapping before Application invocation. The API must not accept arbitrary tag names as canonical plant identity.

Machine ingress preserves source event identity where available, effective time, reported value and unit, quality, Data Source, Integration Principal, and idempotency semantics required to make retries duplicate-safe.

Human Dashboard Reading entry uses ordinary human Tenant authority and may use a different transport route or request shape. Both paths converge on the same Process Application and Domain Reading semantics before acceptance.

## Enforce one Tenant boundary per ordinary operation

Waiotech must enforce one Tenant boundary per ordinary operation.

Every identity, Attachment, reference, result, and consequence belongs to the effective Tenant. Cross-Tenant platform operations use dedicated contracts.

## Use one stable language-independent wire naming convention

Waiotech must use one stable language-independent wire naming convention.

Wire properties use lowercase `snake_case`. Generated clients may map to language conventions internally without changing the wire contract.

## Keep technical identity opaque

Waiotech must keep technical identity opaque.

Technical identifiers are opaque UUID strings. Operational codes are separate properties. Clients do not construct or infer meaning from UUIDs.

## Exchange unambiguous time semantics

Exchange unambiguous time semantics.

Recorded instants use RFC 3339 with explicit offset and ordinarily normalize to UTC in responses. Local dates, local times, and timezone identifiers remain distinct fields.

## Preserve exact values across clients

Waiotech must preserve exact values across clients.

Exact decimal values are serialized as strings when a JSON number could lose precision. The schema declares precision, scale, unit, and currency meaning.

## Make partial input semantics explicit

Waiotech must make partial input semantics explicit.

Omission means the contract does not provide or change a value. `null` means explicit absence where the schema permits it. Edit contracts define this distinction per field.

## Reject unrecognized command input by default

Waiotech must reject unrecognized command input by default unless a typed extension object is explicitly defined. Unknown fields indicate drift, typo, or unsupported meaning.

## Permit additive enrichment without silent meaning change

Permit additive enrichment without silent meaning change.

Response fields may be added only when consumers are required to ignore unknown fields and the addition does not change interpretation. Semantically controlling fields require an explicit contract-version decision before release.

## Expose product contracts rather than persistence models

Waiotech must expose product contracts rather than persistence models.

Representations contain product-significant identity, lifecycle, relationships, effective definition references, concurrency version, Readiness, blockers, available actions, and evidence references as applicable. They exclude secrets, ORM shape, private delivery state, and arbitrary metadata bags.

## Use purpose-specific read models rather than one oversized entity schema

Waiotech must use purpose-specific read models rather than one oversized entity schema.

Summary, detail, history, command result, export, and administrative views may differ through explicit schemas.

## Return the accepted product result rather than only transport acknowledgement

Return the accepted product result rather than only transport acknowledgement.

A command response contains the affected subject, operation and receipt identities, replay indicator, warnings, and authoritative concurrency version when the command establishes one. A purpose-specific response may add a typed bounded result such as a credential or upload instruction. Ordinary clients refetch the authoritative read model after mutation rather than receiving an arbitrary command payload bag.

## Do not use `202` for non-durable receipt of a request

Waiotech must not use `202` for non-durable receipt of a request.

Only when the command transaction durably establishes a long-running process. The response identifies the process resource, state, operation, retrieval link, and cancellation action where supported.

## Use status and typed errors consistently

Waiotech must use status and typed errors consistently.

HTTP status communicates broad transport category. Stable Waiotech error codes communicate exact meaning. Common mappings include 400 malformed input, 401 invalid authentication, 403 denied authority, 404 safe non-disclosure, 409 lifecycle/concurrency/idempotency conflict, 422 typed business-input validation, 429 rate limit, and 503 safe temporary unavailability.

## The error envelope

Waiotech must keep errors machine-readable and non-disclosing.

The error envelope contains stable code, category, safe message, correlation identity, operation identity where available, field violations, typed blockers, named safe references, and retryability. Safe detail fields are closed and explicit; arbitrary metadata is prohibited. It never exposes stack traces, SQL, secrets, or infrastructure topology.

## Keep client behavior independent from message wording

Waiotech must keep client behavior independent from message wording.

Domain errors use `<module>.<subject>.<condition>`. Technical cross-cutting errors use a stable platform namespace.

## Expose one stable validation contract

Waiotech must expose one stable validation contract.

Each violation identifies a typed input path and stable code. Framework-specific error structures are mapped into the Waiotech envelope. Transport locations such as `body`, `query`, `path`, `header`, and `cookie` are not part of the input-model path exposed to clients.

Each field violation exposes a stable `code`, an input-model `path`, a localization `message_key`, and an optional closed `parameters` object containing only schema-declared safe scalar display values. Clients localize from `message_key` and `parameters`; they must not branch on English message text. The Server may also provide a safe fallback message for unsupported clients. Arbitrary validation metadata and raw rejected payloads are prohibited.

## An available action

Waiotech must use authoritative action metadata for client usability.

An available action is a backend-evaluated statement that one named action is available to the Actor for the subject at evaluation time. It may include label key, method, route, input schema, idempotency requirement, and concurrency requirement.

## Revalidate every action at execution time

Revalidate every action at execution time.

The command re-evaluates authority, state, concurrency, Readiness, policy, and references because facts may change.

## Related documents
- [API queries, files, batches, and revisions](040-api-queries-files-and-batches.md)
- [OpenAPI and generated clients](050-openapi-and-generated-clients.md)
- [Application surface architecture](../60-applications/010-application-surface-architecture.md)
