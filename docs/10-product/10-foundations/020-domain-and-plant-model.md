# Domain and plant model

Waiotech represents one plant through several explicit, connected truths rather than one universal hierarchy. Plant structure, process structure, operational work, material custody, and reliability remain distinct authorities that reference one another without duplicating ownership.

## The high-level product model

The canonical high-level model is:

```text
Tenant
└── Plant Model
    ├── Functional Locations
    ├── Assets and installation history
    ├── Process Units
    ├── Process Streams
    ├── Measurement Points
    ├── Data Sources and external mappings
    └── operational criticality

Tenant
├── Process Operations
├── Maintenance
├── Inventory
└── Reliability
```

The Plant Model describes what the plant is and how its stable operational subjects relate. Process Operations describes what is happening and what Operations does about it. Maintenance describes accepted physical work. Inventory describes material custody. Reliability describes actual functional failure, restoration, cause assessment, recurrence, and derived learning.

## Keep plant dimensions orthogonal

Waiotech must not force physical placement, physical object composition, process function, process flow, and measurement meaning into one tree.

The canonical questions are:

- **Functional Location:** at which stable installed position or maintainable place?
- **Asset:** which individually identified physical object or assembly?
- **Process Unit:** which stable process function?
- **Process Stream:** what logical process medium flows between process functions?
- **Measurement Point:** what operational quantity can be observed, and on which explicit plant subject?

These concepts may be related but are never aliases for one another.

## Keep relationships explicit and product-owned

Waiotech must use explicit relationship meaning instead of a universal Plant Object or arbitrary relationship engine.

Canonical relationships include:

- Functional Location contains child Functional Location;
- Asset physically contains child Asset;
- Asset is installed at Functional Location;
- Process Unit contains child Process Unit;
- Process Stream flows from and/or to Process Unit;
- Functional Location serves Process Unit;
- Measurement Point observes one supported plant subject;
- Measurement Point may be implemented by an instrument Asset;
- a supported plant object may have governed external mappings.

A generic `entity_type + entity_id + relationship_type` product model must not become the source of business meaning.

## Keep work, process, and reliability targets explicit

Maintenance Work Targets are Functional Locations or Assets.

Process Conditions concern Process Units or Process Streams and may reference related Assets, Functional Locations, Measurement Points, Readings, and evidence.

Failure Events concern one primary Functional Location or Asset and may reference process consequences and supporting Process evidence.

These target sets are deliberately different because each domain answers a different business question.

## Keep human-facing identity separate from immutable identity

Every canonical Plant Model entity has immutable product identity.

Functional Locations, Assets, Process Units, Process Streams, and Measurement Points have a required human-readable name and may have a human-facing plant code. Names are not required to be unique. When a code is present, it is unique case-insensitively within the Tenant and that entity category.

The same familiar plant code may intentionally exist in different categories, for example a Functional Location position code and the physical Asset installed there. User interfaces must always preserve the entity type so this does not become ambiguous.

Changing a retained code is an explicit attributable recode action that preserves prior code history. A code that has identified a retained entity must not later be silently reused for a different entity in the same code scope.

External identifiers never replace canonical identity. Machine-readable labels and integration contracts should resolve immutable Waiotech identity or a governed external mapping rather than depending on mutable display names.

## Related documents

- [Functional Locations](../30-plant/010-functional-locations.md)
- [Assets and installation](../30-plant/020-assets-and-installation.md)
- [Process Units and Streams](../30-plant/040-process-units-and-streams.md)
- [Measurement Points](../30-plant/050-measurement-points.md)
