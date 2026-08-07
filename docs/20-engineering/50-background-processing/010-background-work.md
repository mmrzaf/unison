# Background work

Background work is represented by durable PostgreSQL obligations with explicit identity, schema, claims, leases, retries, dead-lettering, fairness, and recovery. Redis coordinates delivery but does not own business truth.

## Background work

Waiotech must use background execution only for work with explicit durable semantics.

Background work is durable execution outside the initiating HTTP request because it requires substantial processing, retry, scheduling, artifact generation, or reconciliation.

## Treat Worker execution as a mechanism, not another product model

Waiotech must treat Worker execution as a mechanism, not another product model.

Authority, lifecycle, evidence, atomicity, failure, and Tenant isolation remain identical. The Worker invokes module-owned Application contracts.

## Make PostgreSQL recover every required background action

Waiotech must make PostgreSQL recover every required background action.

They provide queue delivery, Worker wake-up, routing, delay, and technical coordination. They do not own accepted process state or retry obligations.

## A durable work identity

Waiotech must separate logical work from execution attempts.

It identifies one logical background obligation across queue redelivery, Worker restart, lease expiry, retry, and dead-letter recovery. Each attempt has a separate attempt identity.

## A job payload contents

Waiotech must keep queue payloads small and reference PostgreSQL state.

The payload contains work type, schema version, durable record identity, applicable scope, correlation, and delivery attempt metadata. It does not contain a complete authoritative entity or secret.

## Version durable job envelopes separately from work type

Version durable job envelopes separately from work type.

A queued payload may cross a deployment boundary. The Worker must identify supported structure and fail safely when a payload is incompatible.

## Use PostgreSQL claims rather than queue visibility as execution authority

Waiotech must use PostgreSQL claims rather than queue visibility as execution authority.

The Worker atomically claims eligible PostgreSQL work with Worker identity, attempt identity, claim time, lease expiry, and attempt number. Only one unexpired claim controls the item.

## A Worker lease

Waiotech must prevent a failed Worker from holding work indefinitely.

A lease is a bounded technical claim. It may be renewed during demonstrable execution, expires after Worker loss, and never proves product completion.

## Resume from durable state and preserve uncertainty truthfully

Resume from durable state and preserve uncertainty truthfully.

The last committed PostgreSQL state controls. Uncommitted work rolls back and becomes claimable. Committed steps remain. Uncertain external effects enter reconciliation rather than blind replay.

## Use explicit work-type retry policy

Waiotech must use explicit work-type retry policy.

Each work type defines retryable failures, attempt limit, backoff, jitter, maximum delay, expiry, dead-letter condition, and reconciliation. Automatic retry is bounded.

## Dead-lettering

Waiotech must not discard exhausted obligations.

Dead-lettering stops automatic attempts while preserving obligation, attempts, failure class, payload schema, destination, and required intervention. Authorized replay creates another attempt for the same durable work identity.

## Protect workload isolation as well as data isolation

Waiotech must protect workload isolation as well as data isolation.

Worker scheduling uses bounded per-Tenant concurrency, fair queueing, rate limits, and batch size so one Tenant cannot consume all execution capacity.

## Avoid database locks across external latency

Avoid database locks across external latency.

It claims and loads state in bounded transactions, performs uncontrolled I/O outside long locks, and records acknowledgement or failure in another bounded transaction.


## Waiotech Alpha worker inventory

Waiotech Alpha defines exactly five scheduled job types:

- Attachment cleanup;
- Scheduled Work generation;
- Process Round generation;
- authentication cleanup;
- Tenant Export processing.

This is a closed operational inventory. Adding another job type requires an owned Application use case, explicit policy, durable evidence, configuration, recovery, and verification. It does not justify a generic workflow engine.

## Persist logical runs, attempts, and item failures

Each scheduled slot has one deterministic logical run identity. PostgreSQL records every claim and attempt, its lease, classified counts, bounded failure evidence, retry availability, and terminal outcome. Tenant-owned item failures are keyed by Tenant, job type, subject identity, and subject version so exclusion and recovery cannot cross Tenant boundaries. They prevent one poison record or Tenant from consuming each later batch indefinitely. Redis and ARQ wake the Worker but do not own retry state.

## Separate liveness, readiness, and operational health

Worker liveness proves the process and event loop are responsive. Readiness proves required dependencies and the complete accepted job inventory are available. Operational health is evaluated per job from last successful scheduled slot, consecutive failures, active or expired claims, retry backlog, pending age, and unresolved terminal item failures. A Redis heartbeat alone cannot report the Worker healthy.

## Make recovery explicit

Authorized operators recover work through explicit commands that retry one durable run or resolve one failed candidate after corrective action. Recovery preserves the original logical identity and creates another attempt; it does not rewrite prior attempt evidence.

## Related documents
- [Audit, events, and transactional outbox](../30-actions-and-contracts/020-audit-events-and-outbox.md)
- [Report and Notification processing](020-report-and-notification-processing.md)
