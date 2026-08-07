# Stocktaking

A Stocktaking Run governs observed counts, frozen submissions, variance evaluation, approval, and posting through explicit Inventory Movements.

## A Stocktaking Run

Waiotech must use Stocktaking Run as the controlled source of stocktake adjustments.

A Stocktaking Run is a governed process for counting stock positions, reviewing differences, authorizing variances, and posting resulting adjustments.

It does not directly edit Stock Balances.

## A Count Observation

Waiotech must preserve every physical count attempt.

A Count Observation is immutable evidence of one physical count.

It should preserve:

- Stock Location;
- Item;
- lot or condition where applicable;
- counted quantity and unit;
- counter;
- count time;
- evidence;
- relationship to a prior observation when a recount supersedes it.

A recount creates another Count Observation rather than editing the original.

## A frozen stocktake submission

Waiotech must keep the reviewed count set stable.

Submission establishes the exact set of Count Observations presented for review.

After submission, those observations cannot be silently added, removed, or edited.

Corrections require reopening where allowed, recounting, or creating an explicit revised submission.

## Preserve count-time variance and rebase its adjustment against stock at posting time

Waiotech must preserve count-time variance and rebase its adjustment against stock at posting time.

Variance is calculated against expected stock at the Count Observation time.

Movements occurring after the count but before posting must not be treated as count variance.

Example:

```text
Expected at count time: 100
Counted: 97
Variance: -3

Receipt after count: +20
Stock before posting: 120
Posted adjustment: -3
Result: 117
```

## Keep approval and posting as distinct actions

Waiotech must keep approval and posting as distinct actions.

**Approval** accepts the reviewed variance and authorizes its stock consequence.

**Posting** creates the resulting adjustment Movements.

Tenant policy may allow the same Actor to approve and post low-risk stocktakes. Separation of duties is not universally mandatory.

## Preserve consistency between approved variance and posted stock consequences

Waiotech must preserve consistency between approved variance and posted stock consequences.

No unexplained partial result may remain visible.

A posting action must either establish all approved adjustment consequences or report failure without presenting the Stocktaking Run as fully posted.

Engineering Authority determines transaction and recovery mechanisms.

## The Stocktaking Run lifecycle

Waiotech must separate counting, review, approval, and posting as durable Stocktaking conditions.

The canonical lifecycle is:

```text
counting → submitted → approved → posted
submitted → returned_for_recount → counting
counting / submitted / returned_for_recount → cancelled
```

- **Counting:** Count Observations may be recorded.
- **Submitted:** the exact count set is frozen for review.
- **Returned for recount:** review requires new Count Observations.
- **Approved:** variance is authorized but has not yet changed stock.
- **Posted:** all approved adjustment consequences are represented by posted Inventory Movements.
- **Cancelled:** the run ended without posting stock consequences.

## The minimum Inventory and material model

Waiotech must keep Inventory focused on stock custody, availability, movement, and operational cost evidence while keeping tools, services, procurement, serialized resources, and accounting outside its scope defined by the governing record.

The minimum model is:

```text
Item
├── Item Category
├── Item Policy
└── allowed Unit Conversions

Stock Location

Inventory Movement
├── receipt
├── issue
├── return
├── transfer
├── adjustment
└── disposal

Reservation

Work Order
├── Material Requirement
├── Material Usage
├── Tool Requirement
└── Service Requirement

Replenishment Request
└── Receipt Movements

Stocktaking Run
├── Count Observations
├── variance review
├── approval
└── Adjustment Movements
```

The governing distinctions are:

- Item identifies a physical supply.
- Posted Inventory Movement owns stock truth.
- Stock Balance and availability are derived.
- Reservation protects availability without moving stock.
- Material Requirement records technical demand.
- Inventory issue records custody.
- Material Usage records actual work consumption.
- Replenishment Request records authorized demand, not procurement.
- Inventory cost is operational evidence, not accounting valuation.

## Related documents
- [Stock Locations and Inventory Movements](020-stock-locations-and-movements.md)
- [Decisions, waivers, and separation of duties](../60-policy-and-decisions/020-decisions-waivers-and-separation-of-duties.md)
