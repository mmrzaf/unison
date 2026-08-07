# Report and Notification processing

Report Runs and Notifications are persisted before asynchronous processing. Authority, cutoff, artifact integrity, recipient resolution, preference, duplicate prevention, and external delivery remain explicit.

## Prohibit arbitrary SQL and unrestricted report expressions

Waiotech must prohibit arbitrary SQL and unrestricted report expressions.

A Report Type is a product-defined analytical contract declaring purpose, sources, metrics, dimensions, parameters, temporal semantics, output formats, sensitivity, authorization, and artifact behavior.

## Make report output reproducible and attributable

Waiotech must make report output reproducible and attributable.

A Report Run is one durable execution preserving Report Type, contract schema, exact parameters, Actor, Tenant, temporal rule, data cutoff, format, sensitivity, execution lifecycle, artifact, failure, and retry relationship.

## Use truthful terminal process states

Waiotech must use truthful terminal process states.

The lifecycle is:

```text
queued -> running -> completed
queued -> cancelled
queued / running -> failed
running -> cancelled
```

Completed requires a valid Report Artifact. Cancellation succeeds only when no completed artifact exists.

## Separate execution authorization from retrieval authorization

Waiotech must separate execution authorization from retrieval authorization.

Authority and parameter access are evaluated at request acceptance. Execution does not use broader Worker authority. Artifact retrieval re-evaluates Tenant participation, Permission, sensitivity, and governing report access.

## A Report Artifact

Waiotech must separate report meaning from file storage identity.

It is the immutable domain-owned output of one completed Report Run and references exactly one Attachment. It preserves report meaning, parameters, cutoff, format, sensitivity, and content digest.

## Prevent reports from combining incompatible source times

Waiotech must prevent reports from combining incompatible source times.

The Report Type declares temporal semantics and one coherent cutoff or period. Execution uses an appropriate snapshot, repeatable read, as-of source, staging, or reconciliation mechanism.

## Distinguish equivalent report meaning from renderer bytes

Waiotech must distinguish semantic report equivalence from byte-for-byte renderer identity. Byte-identical regeneration is required only when the Report Type explicitly requires it; semantic reproducibility and exact integrity of each completed artifact are always mandatory.

## Prohibit arbitrary Tenant-defined notification rule engines

Waiotech must prohibit arbitrary Tenant-defined notification rule engines.

A Notification Type is a product-defined communication contract declaring trigger, recipients, content, mandatory or optional behavior, channels, deduplication, subject, navigation, and sensitivity.

## Keep preferences within product-defined limits

Waiotech must keep preferences within product-defined limits.

Preferences control only Product Authority options such as optional enablement, channel, digest, quiet period, locale, or destination. They cannot disable mandatory communication.

## Use Notification for awareness rather than duplicate workflow state

Waiotech must use Notification for awareness rather than duplicate workflow state.

A Notification is a recipient-visible communication record with recipient, type, related subject, creation, stable content or rendering context, unread/read state, navigation, and deduplication identity.

## Keep communication state separate from product actions

Waiotech must keep communication state separate from product actions.

Reading does not approve, complete, acknowledge, resolve, cancel, or satisfy a Requirement.

## Make recipient determination explicit and testable

Waiotech must make recipient determination explicit and testable.

The Notification Type uses typed product-defined rules and authoritative facts. The created Notification preserves the resolved recipient. Arbitrary audience queries are prohibited.

## Make communication generation duplicate-safe

Waiotech must make communication generation duplicate-safe.

A stable identity based on type, recipient, triggering event, subject, occurrence, or escalation stage is protected by a durable uniqueness mechanism.

## Separate recipient-visible state from transport state

Waiotech must separate recipient-visible state from transport state.

Channel delivery has separate technical attempts and states. Provider failure does not remove the Notification or mark it read.

## Related documents
- [Reports](../../10-product/60-evidence-and-communications/010-reports.md)
- [Notifications](../../10-product/60-evidence-and-communications/020-notifications.md)
- [Background work](010-background-work.md)
