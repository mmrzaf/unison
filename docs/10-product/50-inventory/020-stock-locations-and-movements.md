# Stock Locations and Inventory Movements

Stock Locations are Inventory custody nodes. Posted Inventory Movements are immutable authoritative stock facts, and stock balances are derived from their posted quantity effects.

## A Stock Location

Waiotech must require all tracked stock custody to use a Stock Location.

A Stock Location is an Inventory-controlled custody node where stock may be held, received, issued, returned, transferred, counted, or disposed.

It is distinct from a Functional Location.

A Stock Location may optionally reference a Functional Location, but it may also represent logical or mobile custody such as:

- quarantine;
- goods in transit;
- technician custody;
- damaged stock;
- external holding.

## Use bounded Stock Location lookup

Waiotech must expose Stock Location lookup as a Tenant-scoped, bounded, paginated query.

The query may accept `q` as a case-insensitive literal match against Stock Location code or name. Percent and underscore characters are ordinary search characters, not wildcard authority. Empty or whitespace-only search values are invalid.

This lookup supports operational selectors and registries; it does not authorize client-side loading of the complete Tenant custody model.

## The authoritative stock fact

Waiotech must derive stock quantity from immutable posted Inventory Movements.

Posted Inventory Movements are the authoritative stock facts.

Replenishment Request receipt attribution is established only by the governed Replenishment receive action, and Stocktaking Run attribution is established only by the governed Stocktaking post action. Generic Movement preparation must reject both relationships so custody evidence cannot diverge from the owning aggregate lifecycle.

A Stock Balance is a projection calculated from posted Movements across the applicable stock dimensions.

Balance rows must not be independently edited as stock truth.

## Make posting the only action that changes authoritative stock

Waiotech must make posting the only action that changes authoritative stock.

An Inventory Movement affects stock only when it is posted.

A Movement may be prepared, validated, or approved before posting, but those stages do not change stock.

Posting establishes the governed stock consequence.

## Make posted Movements immutable and preserve correction history

Waiotech must make posted Movements immutable and preserve correction history.

After posting, the following facts cannot be silently changed:

- Item;
- quantity;
- source Stock Location;
- destination Stock Location;
- lot or batch;
- stock condition;
- effective time;
- Work Order or source attribution.

Corrections require a governed reversal or compensating Movement linked to the original Movement.

## Keep Movement types product-defined and closed

Waiotech must keep Movement types product-defined and closed.

The closed catalogue includes:

- receipt;
- issue;
- return;
- transfer;
- adjustment;
- disposal.

These describe the primary stock consequence.

More detailed operational meaning belongs in governed reasons, source relationships, and evidence.

## Use one adjustment type with explicit governed reasons

Waiotech must use one adjustment type with explicit governed reasons.

Both are adjustments with different governed reasons and source evidence.

Adjustment reasons may include:

- stocktake variance;
- opening balance;
- discovered stock;
- missing stock;
- condition reclassification;
- authorized correction;
- other product-governed reason.

## Correct posted stock through linked compensating evidence rather than editable correction records

Correct posted stock through linked compensating evidence rather than editable correction records.

They are governed correction actions.

A correction action creates a reversing or compensating Movement that:

- references the original Movement;
- records the reason;
- preserves the correcting Actor and time;
- restores the intended stock truth.

The original posted Movement remains visible.

## Treat transfer as one indivisible product action

Waiotech must treat transfer as one indivisible product action.

Normatively, transfer is one business action with one identity and balanced source and destination consequences.

A transfer records:

- Item;
- quantity;
- source Stock Location;
- destination Stock Location;
- lot or condition where applicable;
- Actor;
- effective time;
- reason.

The source decrease and destination increase become effective together. No partial transfer may be presented as successful.

## Use explicit Stock Location custody for in-transit stock

Waiotech must use explicit Stock Location custody for in-transit stock. A dedicated transfer lifecycle requires a Product Authority amendment.

An instantaneous transfer should not pretend to represent prolonged custody in transit.

When goods remain in transit for a meaningful period, use an explicit in-transit Stock Location or require a Product Authority amendment defining a dedicated transfer lifecycle.

## Forbid negative stock

Forbid negative stock under Waiotech Product Authority. Any exception requires a Product Authority amendment defining authority, evidence, reconciliation, and reporting.

A stock-decreasing Movement cannot be posted when it would make the applicable on-hand quantity negative.

When physical stock and recorded stock disagree, users must first reconcile the difference through an authorized adjustment or stocktaking process.

Work Order evidence may record externally supplied or uncatalogued material without inventing an Inventory issue.

## Derive balances at the full governed tracking granularity

Waiotech must derive balances at the full governed tracking granularity.

A stock position is identified by:

- Item;
- Stock Location;
- lot or batch when tracking is required;
- stock condition when tracking is required;
- applicable expiry facts where needed.

Every posted Movement must identify the exact stock position or positions it changes.

## Use stock condition only where it has real custody and availability meaning

Waiotech must use stock condition only where it has real custody and availability meaning.

Stock condition exists only through a typed Product Authority contract defining custody, availability, transitions, evidence, and posting consequences.

Canonical examples include:

- available;
- quarantine;
- damaged;
- expired;
- awaiting inspection.

Stock condition affects whether quantity is available for Reservation or issue.

Changing condition must occur through a governed Movement or equivalent stock consequence, not by editing a Balance projection.

## Inventory tracks quantity, lot, expiry, and condition

Inventory tracks quantity, lot, expiry, and condition. Serialized Item lifecycle is outside this Product Authority.

Waiotech supports:

- quantity by Item and Stock Location;
- optional lot or batch tracking;
- optional expiry tracking;
- optional stock condition.

The following are outside Waiotech Product Authority:

- individual serial custody;
- rotable repair lifecycle;
- exchange pools;
- calibrated-tool history;
- serialized issue and return;
- individual-part genealogy.

## Retire Stock Locations only after active custody obligations are resolved

Waiotech must retire Stock Locations only after active custody obligations are resolved.

Stock Location uses:

```text
active → retired
```

A Stock Location cannot be retired while it contains on-hand stock, active Reservations, unresolved Stocktaking Runs, or pending governed custody obligations.

Retirement preserves all historical Movement and count evidence.

## Related documents
- [Items, categories, and quantities](010-items-categories-and-quantities.md)
- [Reservations and availability](030-reservations-and-availability.md)
- [Functional Locations](../30-plant/010-functional-locations.md)
