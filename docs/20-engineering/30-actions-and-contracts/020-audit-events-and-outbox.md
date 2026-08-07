# Audit, events, and transactional outbox

Required audit facts and durable domain events commit with the authoritative state change. The transactional outbox provides recoverable at-least-once delivery without turning delivery order into hidden business authority.

## An audit record

Waiotech must record who exercised protected authority without copying unrestricted payloads.

An audit record is durable accountability evidence for a protected action or security change. It preserves the Actor, authority path, Tenant or platform scope, action code, subject, reason, accepted or denied outcome where required, recorded time, operation identity, and safe changed-fact summary.

## Do not accept an auditable action without durable accountability

Waiotech must not accept an action requiring audit without durable accountability. Required audit is written in the same PostgreSQL transaction as the accepted action or denial it records.

## Keep audit append-only and independently interpretable

Waiotech must keep audit append-only and independently interpretable.

Corrections are additive and reference the original record. Physical destruction requires a separate explicit legal and Product Authority contract.

## A domain event

Emit facts rather than imperative integration commands from Domain ownership.

Persisting a Domain Event does not by itself create an asynchronous delivery obligation. An outbox destination is declared only when a declared internal consumer owns a required durable consequence. Waiotech does not create a default catch-all `internal` destination, and the outbox is not a second audit log.

A domain event is an immutable accepted fact owned by the module whose Domain action established it. It names what happened, not what a consumer should do.

## Use stable fact-oriented event names

Waiotech must use stable fact-oriented event names.

Event types use `<module>.<subject>.<past_tense_fact>`, for example `maintenance.work_order.released`. A new business fact receives a new event type.

## Version durable payload schemas without embedding versions in event type names

Version durable payload schemas without embedding versions in event type names.

Persisted, outbox, replayable, cross-module, or external messages may be processed after a deployment, retry, dead-letter recovery, or consumer upgrade. The envelope therefore carries `schema_version` so consumers can interpret the payload structure safely.

## Separate payload compatibility from fact identity

Waiotech must separate payload compatibility from fact identity.

Compatible optional additions may retain the version when consumers are required to ignore unknown fields. Removing, renaming, changing requiredness, changing type, changing structure, or changing meaning requires another schema version. A different business fact requires another event type.

## Use one stable event envelope for durable messaging

Waiotech must use one stable event envelope for durable messaging.

The envelope contains, where applicable:

- event identity;
- event type;
- schema version;
- source module;
- Tenant or platform scope;
- subject type and identity;
- occurred time;
- recorded time;
- Actor context;
- operation identity;
- Command Receipt identity;
- correlation identity;
- causation identity;
- typed payload.

It excludes secrets and unrestricted entity snapshots.

## The transactional outbox

Guarantee that an accepted fact cannot lose its required asynchronous consequence.

The outbox is a PostgreSQL record created in the same transaction as the source fact. It preserves the event and destination-specific delivery obligations. External publication occurs only after commit.

## Design every event consumer for duplicate delivery

Waiotech must design every event consumer for duplicate delivery.

Delivery is at least once. Consumers must be duplicate-safe through an inbox, processed-event record, unique constraint, or equivalent durable mechanism. Exactly-once transport claims do not replace application idempotency.

## Preserve only necessary bounded ordering

Waiotech must preserve only necessary bounded ordering.

Ordering is declared only where product or integration meaning requires it. The contract identifies the ordering key, ordinarily one subject or stream, and consumers reject or reconcile impossible sequence. Global ordering is prohibited.

## Avoid event payloads becoming uncontrolled replica models

Avoid event payloads becoming uncontrolled replica models. Events ordinarily contain the accepted fact and references needed by consumers rather than complete aggregate snapshots. A specific contract may require an immutable snapshot when replay or independent interpretation requires it. Consumers query owning modules through approved contracts when more data is required.

## Apply versioning where compatibility risk exists

Waiotech must apply explicit schema versioning to any message that is persisted, replayable, or exchanged across an independently deployable boundary. Purely in-process ephemeral messages may omit schema version when they cannot survive or cross that boundary.

## Related documents
- [Commands and idempotency](010-commands-and-idempotency.md)
- [Background work](../50-background-processing/010-background-work.md)
- [Observability](../80-delivery/030-observability.md)
