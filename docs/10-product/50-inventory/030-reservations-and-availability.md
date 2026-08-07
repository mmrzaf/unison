# Reservations and availability

A Reservation binds an Item quantity at a Stock Location to an authorized demand without moving stock. Availability is derived from authoritative balances, reservations, condition, and applicable Inventory rules.

## A Reservation

Waiotech must keep Reservation as an operational aggregate separate from Movement.

A Reservation is a governed commitment of available stock to a Work Order or Material Requirement.

It protects availability but does not change on-hand quantity or physical custody.

A Reservation has its own identity and history.

## Support bounded operational Reservation lookup

Reservation listing must remain Tenant-scoped, bounded, and paginated. It may be filtered by lifecycle state, Item, Work Order, or Stock Location so custody and maintenance workflows can inspect the obligations relevant to their current context.

These filters expose existing Reservation facts only. They do not create a second availability calculation or transfer custody ownership out of Inventory.

## Reserve exact governed stock availability for a defined maintenance demand

Reserve exact governed stock availability for a defined maintenance demand.

A Reservation binds:

- Work Order or Material Requirement;
- Item;
- Stock Location;
- reserved quantity in stocking unit;
- optional lot or condition constraints;
- effective time;
- optional expiry;
- reserving Actor.

## Preserve Reservation quantity history rather than one mutable remaining value alone

Waiotech must preserve Reservation quantity history rather than one mutable remaining value alone.

Reservation should preserve:

- originally reserved quantity;
- quantity consumed through issue;
- quantity released;
- quantity expired or cancelled;
- remaining reserved quantity.

Remaining quantity may be derived from preserved Reservation and issue facts.

## The Reservation lifecycle

Waiotech must use a small explicit Reservation lifecycle.

The lifecycle is:

```text
active
→ fulfilled
→ released
→ expired
→ cancelled
```

- **Active:** quantity remains protected.
- **Fulfilled:** the reserved quantity has been fully consumed through issue.
- **Released:** remaining quantity was intentionally made available.
- **Expired:** the Reservation ended at its governed expiry.
- **Cancelled:** the underlying demand or Reservation was invalidated.

Terminal outcomes preserve reason, Actor, and time.

## Reserve only governed stock available at reservation time

Reserve only governed stock available at reservation time under Waiotech Product Authority.

Creating or increasing a Reservation must not reduce available stock below zero.

A shortage remains visible as an unfulfilled Material Requirement rather than an impossible Reservation.

## Never treat Reservation as custody or consumption evidence

Waiotech must never treat Reservation as custody or consumption evidence.

Reservation changes available quantity only.

Physical issue requires a posted Inventory Movement.

A Movement used to fulfil a Reservation must address the same stock position and Work Order. When the Reservation is bound to a Material Requirement, the Movement must address that same requirement. The total fulfilment quantity attributed to one Movement across all Reservations must not exceed that Movement's posted issue quantity. One Reservation records at most one fulfilment event against the same Movement; later fulfilment requires another posted issue Movement.

## Available stock

Waiotech must derive availability from posted Movements, Reservations, and governed stock condition.

Available stock is derived from on-hand stock less active Reservations and other explicitly unavailable stock conditions.

It is a projection, not independently maintained truth.

## Related documents
- [Stock Locations and Inventory Movements](020-stock-locations-and-movements.md)
- [Maintenance materials, tools, services, and cost](040-maintenance-materials-tools-services-and-cost.md)
