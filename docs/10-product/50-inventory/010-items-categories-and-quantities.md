# Items, categories, and quantities

An Item is the governed identity of a material, consumable, spare part, tool, or other inventory-relevant thing. Item policy, categorization, units, conversions, and tracking granularity remain explicit.

## An Item

Waiotech must use **Item** as the canonical entity name and keep its meaning limited to physical supplies.

An Item is the stable catalogue identity of a material, spare part, consumable, or other physical supply used by operations.

An Item may be:

- tracked in stock;
- purchased without being stocked;
- planned or recorded as material used by a Work Order.

Tools, services, labor, contractors, vehicles, and rental resources are not Items.

## Separate catalogue identity from stock-tracking behavior

Waiotech must separate catalogue identity from stock-tracking behavior.

Item policy determines whether Waiotech tracks stock custody and quantity for the Item.

A non-stock Item may still be:

- included in a Material Requirement;
- purchased externally;
- recorded as Work Order material usage;
- associated with operational cost.

It does not participate in Stock Balances, Reservations, or stock-changing Movements.

## Keep Item policy small, explicit, and inventory-specific

Waiotech must keep Item policy small, explicit, and inventory-specific.

Item policy defines only concrete inventory behavior:

- stock-tracked or non-stock;
- stocking unit;
- allowed unit conversions;
- lot or batch tracking requirement;
- expiry tracking requirement;
- stock-condition tracking requirement;
- whether Reservation is allowed;
- maximum quantity decimal places, from 0 through 12.

Item policy must not become an open-ended rule engine.

## Replace fixed Category, Family, and Item Type levels with one governed recursive Item Category model

Waiotech must replace fixed Category, Family, and Item Type levels with one governed recursive Item Category model.

A fixed-depth hierarchy will not fit every material catalogue.

Use one recursive Item Category hierarchy:

```text
Item Category
└── Item Category
    └── Item
```

Examples include:

```text
Mechanical Spares
└── Bearings
    └── Rolling-element Bearings
```

The hierarchy may have any required depth.

## Use one primary taxonomy and prohibit overlapping classification systems without an explicit Product Authority amendment

Waiotech must use one primary taxonomy and prohibit overlapping classification systems without an explicit Product Authority amendment.

Item Category follows these rules:

- each Item has one primary category;
- categories may contain child categories;
- cycles are forbidden;
- category identity and codes remain stable after use;
- moving an Item between categories does not change the Item identity;
- category does not independently determine stock policy or authorization.

Additional Item classification dimensions require a Product Authority amendment defining their meaning, governance, relationships, and reporting consequences.

## Use bounded catalogue lookup and explicit hierarchy scope

Waiotech must expose bounded, paginated catalogue lookup rather than loading the complete Tenant catalogue into a client.

Item Category listing may select exactly one hierarchy scope:

- `root_only=true` returns root categories;
- `parent_id=<id>` returns direct children of one category;
- omitting both returns the ordinary paginated registry.

`root_only` and `parent_id` are mutually exclusive.

Item Category and Item listing may accept `q` as a case-insensitive literal match against code or name. Percent and underscore characters are ordinary search characters, not wildcard authority. Empty or whitespace-only search values are invalid. Search remains Tenant-scoped, bounded, and paginated.

## Use one stocking unit per stock-tracked Item with governed Item-specific conversions

Waiotech must use one stocking unit per stock-tracked Item with governed Item-specific conversions.

Every stock-tracked Item has one stocking unit.

Examples include:

- each;
- metre;
- litre;
- kilogram.

Purchasing, issue, and usage may use other allowed units only when the Item defines an explicit conversion.

## Preserve Item-specific conversion meaning

Waiotech must preserve Item-specific conversion meaning.

Conversions are Item-specific when physical meaning may vary.

For example:

```text
Stocking unit: each
Purchasing unit: box
Conversion: 1 box = 24 each
```

Conversions between unlike dimensions, such as volume and mass, require Item-specific physical basis and must never be assumed globally.

## Make quantity precision explicit and reject silent rounding

Waiotech must make quantity precision an explicit Item policy fact.

Every Item declares the maximum number of decimal places accepted for entered and normalized quantity. The policy applies consistently to receipts, issues, transfers, reservations, counts, corrections, material use, and all Item-specific unit conversions.

The API must require the precision when creating or replacing Item policy. Domain and Application validation reject quantities that exceed it. Repositories and PostgreSQL must not silently round an authoritative quantity.

Quantity precision becomes immutable after the first posted Movement because changing it would reinterpret historical and future balance meaning. Cost and currency rounding remain separately governed accounting facts.

## Preserve both entered and normalized quantity facts

Waiotech must preserve both entered and normalized quantity facts.

A Movement should preserve:

- entered quantity;
- entered unit;
- equivalent stocking quantity;
- conversion basis applied;
- applicable conversion revision or effective rule.

Conversion changes effective after the historical Movement must not rewrite historical Movement quantities.

## Use simple active and retired semantics for catalogue records

Waiotech must use simple active and retired semantics for catalogue records.

Item and Item Category use:

```text
active → retired
```

Retirement prevents new ordinary selection while preserving all historical Movements, Requirements, Usage, Reservations, and reporting meaning.

An Item or Category cannot be retired while active obligations require it unless those relationships are resolved or explicitly governed.

## Related documents
- [Stock Locations and Inventory Movements](020-stock-locations-and-movements.md)
- [Maintenance materials, tools, services, and cost](040-maintenance-materials-tools-services-and-cost.md)
