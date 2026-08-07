# Process material usage

Process Operations may use Inventory-controlled material during an Operational Action. Inventory owns physical custody and Movement; Process owns the operational reason, action, and outcome associated with that use.

## A Process Material Usage

Process Material Usage is Process-owned evidence that an Operational Action consumed or otherwise used an Inventory Item.

It may preserve:

- exactly one Operational Action;
- related Process Condition where the Action belongs to one;
- Item;
- actual used quantity and unit;
- relevant Process Unit or Stream context;
- recording Actor;
- operational reason;
- linked Inventory Movement;
- correction evidence.

Process Material Usage does not own stock balance or Stock Location custody.

## Post custody through Inventory

When Process use changes tracked stock, the physical quantity effect must be represented through an Inventory Movement under Inventory authority.

Process must not directly decrement stock or create an alternate Process balance.

The accepted cross-domain action must preserve both meanings:

- Process: why the material was used and what operational action it supported;
- Inventory: what physical quantity moved from which Stock Location under which Actor and reason.

## Keep chemical-process meaning bounded

Waiotech may use Process Material Usage for treatment chemicals and other governed operational supplies where the plant tracks them through Inventory.

Process Material Usage is not a dosing-control system, laboratory ledger, purchasing record, or financial accounting journal.

## Correction

Correcting Process usage and correcting Inventory custody are related but distinct actions.

A Process correction must not silently reverse a posted Inventory Movement. Where physical custody also requires correction, Inventory records the governed corrective Movement explicitly.

## Related documents

- [Operational Actions, outcomes, and responsibility](../35-process/030-actions-outcomes-and-responsibility.md)
- [Stock Locations and Movements](020-stock-locations-and-movements.md)
