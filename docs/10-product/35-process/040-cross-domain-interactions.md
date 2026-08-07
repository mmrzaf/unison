# Cross-domain Process interactions

Process Operations, Maintenance, Inventory, and Reliability have independent authority but must compose one operational story. Cross-domain relationships preserve source ownership and must not copy or silently convert one domain record into another.

## Process and Maintenance

A Process Condition may support a new or existing Work Request when Operations determines that physical maintenance investigation or work is required.

The Process Condition remains Process authority. The Work Request and resulting Work Order are Maintenance authority.

Creating Maintenance work from Process must carry forward relevant context such as Process Unit, related Assets or Functional Locations, supporting Readings, Observations, and condition narrative without forcing the User to re-enter known facts.

Maintenance completion does not automatically resolve the Process Condition. When operating recovery matters, Operations records or confirms Process evidence and resolves the Condition explicitly.

## Maintenance and Process evidence

A Work Order may reference Process Readings and Conditions as diagnosis, planning, execution, or verification context.

Maintenance may also request post-work Process monitoring.

Maintenance technical verification answers whether the maintenance work achieved its defined technical acceptance criteria. Process resolution answers whether the operational condition no longer requires Process handling. Both may be true at different times.

## Process and Reliability

A Process Condition, Reading, or Observation may reveal or support a Failure Event when the functional-failure definition is met.

Failure Event remains Reliability authority. Process evidence remains linked source evidence and must not be rewritten as Failure Event fields when its original meaning matters.

A Failure Event may record Process consequence by reference to affected Process Units, Streams, Conditions, and relevant Readings.

## Process and Inventory

Process Operations may consume Inventory-controlled material when an operational action uses stock such as treatment chemical or another governed plant supply.

Inventory owns physical custody and Movement. Process owns why the material was used and the operational action or condition to which the usage relates.

Process material usage must not directly mutate Inventory balances outside Inventory authority.

## Do not introduce generic cross-domain ownership

Waiotech must not solve cross-domain composition with a generic Activity, Event, Subject, Relationship, or universal attribution entity that becomes business authority.

Each relationship must state the owning records and business meaning explicitly.

## Related documents

- [Work Requests](../40-maintenance/030-work-requests.md)
- [Failure Events](../55-reliability/010-failure-events.md)
- [Stock Locations and Movements](../50-inventory/020-stock-locations-and-movements.md)
