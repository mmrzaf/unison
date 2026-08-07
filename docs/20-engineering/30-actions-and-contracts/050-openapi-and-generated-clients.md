# OpenAPI and generated clients

OpenAPI is generated from module-owned API contracts. Supported browser and
Android clients are generated from that canonical artifact and are never edited
manually.

## Use backend-generated OpenAPI as the transport reference

Waiotech must use backend-generated OpenAPI as the transport reference.

The Server generates one canonical OpenAPI document from registered routes,
typed request and response schemas, errors, authentication, headers, action
metadata, and owned catalogues.

## Correct implementation when OpenAPI conflicts with authority

OpenAPI is a generated engineering reference, not an independent source of
Product Authority.

An incorrect contract that has not been published as part of a supported release is corrected directly, and every affected generated client and caller changes atomically. The incorrect operation, field, schema, client module, and documentation are removed in the same change.

Once a contract version is part of a supported release, an incompatible public-contract change uses an explicit new contract version and governed consumer transition. Waiotech does not retain aliases, translation shims, dual request shapes, or competing models inside one canonical contract version.

## Make operations complete enough for generation and verification

Every operation defines a stable `operationId`, owner, summary, request and
response schemas, errors, authentication, Tenant context, idempotency,
concurrency behavior, tags, and content types.

Incomplete operations are not published to OpenAPI and are not represented by
handwritten frontend clients.

## Maintain one contract chain for every operational surface

Each application generates only the client required by its accepted server
surface. A frontend without an accepted operational API does not carry an unused
copy of another application's client.

Generation is deterministic. Verification regenerates into a temporary
directory and fails on file-set or content drift.

## Verify the complete contract and rejection behavior

Contract tests cover schemas, status codes, errors, Tenant isolation,
authentication, authorization, idempotency, concurrency, filters, sorting,
pagination, files, safe non-disclosure, and generated-client parity.

## Related documents

- [API contracts](030-api-contracts.md)
- [Application surface architecture](../60-applications/010-application-surface-architecture.md)
- [Testing and software release](../80-delivery/050-testing-and-release.md)
