# Replenishment Requests

A Replenishment Request records a governed need to increase stock availability. It does not make Waiotech a procurement system or claim external purchasing fulfillment.

## A Replenishment Request

Waiotech must use **Replenishment Request** instead of the ambiguous term Restock.

A Replenishment Request is authorized demand to replenish an Item at a destination Stock Location.

It may record:

- Item;
- destination Stock Location;
- requested quantity and unit;
- required-by time;
- reason;
- requester;
- approving Actor where required;
- external procurement reference.

It is not a purchase order and does not prove that stock has arrived.

## Keep replenishment demand separate from receipt evidence

Waiotech must keep replenishment demand separate from receipt evidence.

A Replenishment Request is fulfilled through one or more posted receipt Movements linked to it.

Partial fulfillment may remain visible until:

- the requested quantity is received;
- the remaining demand is cancelled;
- an authorized Decision accepts the shortfall.

## Start with Replenishment Request and add Recommendation only for a concrete automation workflow

Start with Replenishment Request and add Recommendation only for a concrete automation workflow.

Only when automated reorder suggestions are in scope.

A Replenishment Recommendation would be an unaccepted system suggestion. A Replenishment Request is authorized demand.

The two concepts should not be introduced unless the distinction is operationally required.

## Keep replenishment demand active until fulfilled or explicitly cancelled

Waiotech must keep replenishment demand active until fulfilled or explicitly cancelled.

Replenishment Request uses:

```text
active → fulfilled
active → cancelled
```

Partial fulfillment is derived from linked receipt quantities and does not require another lifecycle state.

Cancellation preserves received quantity, external references, Actor, time, and reason.

## Related documents
- [Items, categories, and quantities](010-items-categories-and-quantities.md)
- [Stock Locations and Inventory Movements](020-stock-locations-and-movements.md)
- [Product boundaries](../10-foundations/030-product-boundaries.md)
