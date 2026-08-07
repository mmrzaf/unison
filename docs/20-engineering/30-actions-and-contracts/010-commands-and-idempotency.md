# Commands and idempotency

Every accepted mutation is a named command with explicit context, action code, authorization, guards, transaction boundary, idempotency, operation identity, and recoverable receipt semantics.

## A command

Waiotech must represent every product mutation through an explicit Application command.

A command is a named request to perform one product-significant mutation. It identifies the owned action, typed input, Actor context, Tenant or platform scope, authority, lifecycle guards, Readiness, transaction, result, audit, events, and retry behavior.

## Use stable action identities across API, audit, events, and generated references

Waiotech must use stable action identities across API, audit, events, and generated references.

Action codes use stable module-qualified identities such as `maintenance.work_order.release` or `inventory.movement.issue`. The code is independent from route names, labels, handler names, and implementation classes.

## Establish authoritative execution context before command evaluation

Establish authoritative execution context before command evaluation.

The command context contains:

- authenticated principal;
- effective Tenant or platform scope;
- Actor type and identity;
- request identity;
- correlation identity;
- idempotency key where required;
- expected concurrency version where required;
- recorded time from the trusted clock;
- support or system-authority context where applicable.

Clients cannot assert Permission, effective Access Profile, lifecycle outcome, audit identity, or event identity.

## The canonical command execution sequence

Waiotech must keep command acceptance atomic and ordered.

The sequence is:

```text
authenticate
-> establish Tenant or platform context
-> acquire or inspect Command Receipt
-> authorize
-> load authoritative state
-> verify concurrency
-> evaluate lifecycle and Readiness
-> execute Domain behavior
-> persist all mandatory consequences
-> record Domain Events and required audit
-> create outbox obligations only for declared durable consumers
-> finalize Command Receipt
-> commit
-> return the accepted result
```

## Make retry safety explicit for every mutation boundary

Waiotech must make retry safety explicit for every mutation boundary.

Every externally retryable mutation with business consequences requires idempotency. This includes browser actions, integration commands, artifact creation, publication, posting, assignment changes, support actions, and process creation. A purely internal transaction may omit a client key when duplicate invocation is impossible by contract.

## The scope of an idempotency key

Waiotech must prevent key collision across authority and action boundaries.

The key is scoped by effective Tenant or platform scope, authenticated principal class, action code, and any additional stable boundary required by the command contract. It is opaque, bounded, contains no secret, and remains stable for one logical submission.

## A request fingerprint

Waiotech must use fingerprints to distinguish replay from conflicting key reuse.

The fingerprint is a deterministic digest of the command's effective normalized input, including subject identities and business inputs but excluding transport noise such as request identity. Secret values are normalized without being stored in clear form.

## A Command Receipt

Waiotech must use durable receipts to establish one result for one logical attempt.

A Command Receipt is the durable record of one idempotent logical command attempt. It preserves:

- receipt identity;
- scope;
- principal;
- action code;
- idempotency key digest;
- request fingerprint;
- processing claim;
- established result or rejection;
- operation identity;
- created and completed times;
- reconciliation condition where required.

The receipt does not become domain evidence unless Product Authority assigns that meaning.

## Keep receipt state technical and separate from product lifecycle

Waiotech must keep receipt state technical and separate from product lifecycle.

A receipt may represent bounded technical conditions such as:

```text
processing
succeeded
rejected
failed_retryable
failed_internal
reconciliation_required
```

The state describes command execution, not the lifecycle of the subject.

## Prevent abandoned receipts from blocking safe replay indefinitely

Waiotech must prevent abandoned receipts from blocking safe replay indefinitely.

A processing claim has a bounded lease. When execution terminates before commit, the claim expires and the same logical command may resume. The claim does not permit a second concurrent executor while valid.

## Replay one authoritative result rather than re-executing the command

Replay one authoritative result rather than re-executing the command.

Waiotech returns the established result without repeating accepted business consequences. The response may indicate idempotent replay.

## Protect key identity from semantic reuse

Waiotech must protect key identity from semantic reuse.

The command is rejected with `platform.idempotency.conflict`. The established receipt and original result remain unchanged. The original payload is not exposed.

## Make negative outcomes duplicate-safe without fabricating accepted actions

Waiotech must make negative outcomes duplicate-safe without fabricating accepted actions.

A deterministic business rejection may be stored in the Command Receipt so a retry returns the same rejection while the receipt contract applies. A rejection creates no partial domain consequence. Material security denials may create separate audit evidence.

## Distinguish rollback, response loss, and cross-system uncertainty

Waiotech must distinguish rollback, response loss, and cross-system uncertainty.

When the transaction did not commit because of an explicitly classified transient infrastructure failure, the API returns `service.command_failed_retryable` with category `retryable_infrastructure`, `retryable: true`, and the established operation identity. PostgreSQL classification is limited to known connection, serialization, deadlock, lock-availability, and shutdown conditions; constraint failures and arbitrary exceptions are not transient. A deliberate retry reuses the same idempotency key and may reclaim the receipt. An unexpected programming, invariant, or mapping failure establishes `failed_internal`, returns a sanitized non-retryable `500`, and is not automatically re-executed by receipt replay. A connection failure raised while committing an otherwise successful command has an unknown outcome: the API returns `platform.command.reconciliation_required` with the operation identity and must not classify the command as a safe pre-commit retry. Reusing the same idempotency key then reconciles against any committed receipt without repeating accepted business consequences. When the transaction committed but the response was lost, replay returns the established result. When an external outcome is uncertain, the receipt or related process enters reconciliation-required handling.

## Operation identity

Waiotech must use operation identity as the execution spine of a command.

Operation identity identifies one logical command execution across requests, receipt replay, audit, events, outbox delivery, Worker processing, and external reconciliation. The backend establishes it and preserves it across retries of the same logical attempt.

## Keep execution identities distinct and propagate only those applicable to each boundary

Waiotech must keep execution identities distinct and propagate only those applicable to each boundary.

Request identity identifies one transport attempt. Correlation identity groups a broader flow. Operation identity identifies one logical command. Causation identity identifies the immediate command or event that produced another fact. Trace identity remains technical observability context.

## Related documents
- [Audit, events, and transactional outbox](020-audit-events-and-outbox.md)
- [API contracts](030-api-contracts.md)
- [Background work](../50-background-processing/010-background-work.md)
