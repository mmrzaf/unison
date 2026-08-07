# Maintenance materials, tools, services, and cost

Maintenance owns its requirements and usage evidence; Inventory owns stock custody and movement. Tools, service requirements, substitutions, returns, and direct cost attribution preserve their distinct meanings.

## A Material Requirement

Waiotech must keep technical material demand in Maintenance, not Inventory.

A Material Requirement is structured maintenance demand owned by the Work Order.

It may define:

- required Item or free-text material;
- required quantity and unit;
- acceptable alternatives;
- required-by action or time;
- linked Reservation;
- fulfillment state;
- issued and used material references.

It is a specialized form of the Work Order’s material Requirement.

## Separate technical demand from physical custody

Waiotech must separate technical demand from physical custody.

Inventory owns:

- Stock Location;
- stock availability;
- Reservation;
- custody;
- Inventory Movement.

Maintenance owns:

- Material Requirement;
- technical suitability;
- actual Work Order usage;
- substitution acceptance;
- completion evidence.

## The difference between material issue and material usage

Waiotech must not infer that all issued material was consumed.

Material issue records stock leaving Inventory custody for a Work Order or another governed destination.

Material usage records what was actually consumed, installed, applied, or otherwise used by the Work Order.

They may differ.

Example:

```text
10 seals issued
7 seals installed
3 seals returned
```

Issue is authoritative for stock custody. Material Usage is authoritative for work attribution.

## Require explicit disposition of issued material where applicable

Waiotech must require explicit disposition of issued material where applicable.

Work Order material reconciliation should distinguish:

- issued quantity;
- used or installed quantity;
- returned quantity;
- disposed or damaged quantity;
- unresolved difference.

The Work Order cannot close when a material reconciliation Requirement remains unresolved.

## Keep custody and work attribution aligned as one successful product action

Waiotech must keep custody and work attribution aligned as one successful product action.

A successful issue must establish both:

- its posted Inventory Movement;
- its Work Order attribution.

No valid product state may show stock issued to work without the corresponding Work Order relationship, or Work Order-issued stock without the posted Movement.

Engineering Authority determines how this consistency is enforced.

## Support explicit alternatives and governed technical substitution without automatic equivalence inference

Waiotech must support explicit alternatives and governed technical substitution without automatic equivalence inference.

Substitution has two supported paths.

### Pre-approved alternative

A Material Requirement or Item declares specific Items approved as alternatives.

### Unapproved substitution

A technical Decision is required and records:

- originally required Item;
- selected substitute;
- compatibility basis;
- reason;
- deciding Actor;
- quantity;
- effect on Procedure, safety, warranty, or acceptance where applicable.

The original requirement and actual material used remain preserved.

## Allow truthful non-stock material evidence without pretending it belongs to Inventory

Waiotech must allow truthful non-stock material evidence without pretending it belongs to Inventory.

Free-text material may represent:

- uncatalogued supply;
- externally provided material;
- locally purchased material;
- incidental consumable.

It should record:

- description;
- quantity;
- unit;
- source;
- Actor;
- time;
- cost where known;
- evidence where applicable.

It does not affect stock and does not automatically create an Item.

## Preserve historical reporting meaning

Waiotech must preserve historical reporting meaning.

Not silently.

When a catalogue Item is created after the historical usage, historical free-text usage remains as originally recorded unless an authorized correction explicitly establishes the relationship.

## Treat return as a new custody event, not reversal of usage evidence

Waiotech must treat return as a new custody event, not reversal of usage evidence.

Return is a separate posted Movement.

It records:

- originating issue where available;
- returning Work Order;
- Item;
- quantity;
- destination Stock Location;
- stock condition;
- Actor;
- time;
- assessment evidence where required.

## Require condition-aware returns

Waiotech must require condition-aware returns.

Unused serviceable material may return to ordinary available stock.

Opened, contaminated, damaged, expired, or suspect material must enter an appropriate unavailable Stock Location or stock condition until its disposition is determined.

## Do not assume removed components are stock returns

Waiotech must not assume removed components are stock returns.

Not automatically.

Removed material may be:

- scrap;
- repairable material;
- failed evidence;
- externally owned property;
- rotable stock after return to Inventory.

It may enter Inventory only through an explicit receipt or return action whose custody meaning is valid.

## A tool in Waiotech

Waiotech must represent tool needs through Work Requirements. A Tool or Resource aggregate requires a Product Authority amendment.

A tool is represented as a Work Requirement describing equipment needed to perform work.

It may record:

- description or catalogue reference;
- quantity;
- required capability;
- provider;
- provision evidence.

Waiotech does not claim:

- tool custody;
- issue and return;
- availability;
- calibration control;
- serial tracking.

## Keep tools outside the Item and stock model

Waiotech must keep tools outside the Item and stock model.

Tools are governed by their distinct custody, availability, calibration, assignment, and return semantics, not merely because they are physical objects.

Stock Items represent consumable or supply custody. Controlled tools have different lifecycle, availability, calibration, assignment, and return semantics.

A dedicated Tool or Resource model is outside Waiotech Product Authority and requires an amendment defining identity, lifecycle, custody, availability, evidence, and cost.

## A Service Requirement

Manage required service provision without introducing procurement or supplier-management workflows.

A Service Requirement is a Work Order Requirement for an external or internal service needed to perform work.

Examples include:

- crane support;
- specialist contractor;
- machining;
- external inspection;
- temporary utilities.

It may record:

- required service;
- provider;
- provision time;
- delivered scope;
- acceptance evidence;
- direct operational cost.

## Keep procurement outside this Product Authority

Waiotech must keep procurement outside this Product Authority.

Waiotech may:

- establish material demand;
- authorize replenishment demand;
- send demand to ERP or procurement;
- retain external references;
- receive fulfillment information;
- record Inventory receipt.

The following remain external. Adding any of them requires an explicit Product Authority amendment:

- supplier selection;
- quotations;
- purchase orders;
- invoice approval;
- payment;
- tax;
- accounting liability.

## Provide traceable operational cost evidence without claiming accounting authority

Waiotech must provide traceable operational cost evidence without claiming accounting authority.

Inventory may provide operational cost snapshots for stock Movements and Work Order history.

Where available, preserve:

- unit cost;
- total cost;
- currency;
- cost source;
- costing basis or method;
- effective time;
- external reference.

Possible sources include:

- ERP-provided valuation;
- receipt cost;
- maintained operational standard cost;
- authorized manual estimate.

## Keep operational costing and accounting valuation separate

Waiotech must keep operational costing and accounting valuation separate.

Inventory cost evidence must not be presented as automatically equal to:

- general-ledger value;
- tax basis;
- invoice value;
- replacement cost;
- financial inventory valuation.

Accounting systems remain authoritative for financial valuation.

## Attribute Inventory cost to Work Orders without transferring source authority

Attribute Inventory cost to Work Orders without transferring source authority.

A Work Order may aggregate the historical material cost snapshots associated with its issued, used, returned, or disposed Items.

Inventory Movement remains authoritative for the underlying custody and cost facts. Material Usage remains authoritative for how material contributed to the work.

## Preserve historical operational cost meaning

Waiotech must preserve historical operational cost meaning.

Historical Movements and Work Order cost attribution retain the cost source and basis applied when the evidence was recorded or finalized.

Corrections require explicit source-owned actions and must not silently rewrite history.

## Related documents
- [Completion, verification, and closeout](../40-maintenance/080-completion-verification-and-closeout.md)
- [Stock Locations and Inventory Movements](020-stock-locations-and-movements.md)
- [Reservations and availability](030-reservations-and-availability.md)
