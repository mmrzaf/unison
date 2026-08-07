# Assets and installation

An Asset is an individually identified physical object or assembly whose own lifecycle, maintenance, reliability, or installation history matters. Asset identity is separate from the stable Functional Location where the Asset may be installed.

## Use one physical Asset concept without mandatory structural types

Waiotech must use one Asset concept.

Asset does not have mandatory structural types such as Unit, Equipment, or Component. What an Asset technically is belongs to governed Asset Classification. Where physical decomposition is useful, an Asset may contain child Assets.

Asset parentage means physical composition only.

It must not be used for:

- Functional Location containment;
- Process Unit hierarchy;
- Process Stream topology;
- organizational ownership;
- responsibility;
- Inventory custody.

## Create Asset identity only when independent history matters

A physical item should be an Asset when Waiotech needs its own durable identity and history, for example because it is maintained, inspected, installed, removed, replaced, failed, traced, or reported independently.

A consumable bearing or gasket normally remains an Inventory Item when its individual serialized history is not needed. A serialized motor, pump, blower, valve, instrument, gearbox, generator, or replaceable assembly may be an Asset.

## Physical composition

An Asset may have one effective parent Asset in the same Tenant when it is physically part of that parent assembly. The composition graph must not contain cycles and one Asset may have at most one effective physical parent at one instant.

Physical composition has no fixed depth and no mandatory category sequence.

Composition changes preserve effective history. Attaching, detaching, or moving a child Asset to another physical parent must not overwrite which assembly contained it when earlier work, failures, or evidence occurred.

A child Asset retains its own identity, lifecycle, history, and classification.

## Asset installation

Installation is an explicit historical relationship between one root physical Asset and one Functional Location.

At any effective instant, an Asset with no effective physical parent may be directly installed at no more than one Functional Location. A Functional Location may contain several directly installed root Assets when the stable position legitimately contains several assemblies or objects.

An Asset that has an effective physical parent does not carry a competing direct installation. Its effective installed Functional Location is inherited through the nearest physically containing ancestor with a direct installation. This keeps one authoritative placement path for a composed assembly.

An installation relationship preserves:

- directly installed Asset;
- Functional Location;
- installation effective time;
- installing Actor;
- reason or source where required;
- removal effective time when ended;
- removing Actor and reason;
- relevant evidence.

Changing composition or installation must preserve enough effective history to resolve an Asset's installed Functional Location at a retained historical instant. Installation history must not be represented by overwriting one current-location field without history.

## Replacement

Replacing a physical Asset does not replace the Functional Location.

The outgoing Asset is removed from the Functional Location and the incoming Asset is installed through explicit actions. Both Asset histories remain intact.

A direct Asset-to-Asset replacement relationship may be preserved when it adds useful traceability, but it must not substitute for installation history or allow the new Asset to inherit the old Asset identity.

## The Asset lifecycle

Asset administrative lifecycle uses:

```text
inactive → active
active → inactive
inactive / active → decommissioned
```

- **Inactive:** retained physical identity is not available for ordinary current operational use.
- **Active:** Asset is available for ordinary operational use, whether installed or held as a managed spare according to its context.
- **Decommissioned:** Asset is permanently retired from operational use.

Decommissioning is terminal and preserves all history.

Asset lifecycle is separate from installation and from temporary operating condition. An Asset may be active but uninstalled. An installed Asset may be stopped, isolated, degraded, or failed without changing its administrative lifecycle.

## Do not create one universal Asset-condition status

Operational condition may be represented by Process evidence, Findings, Failure Events, Work Order evidence, and other domain-owned records.

Waiotech must not collapse operating, stopped, isolated, degraded, failed, unavailable, and similar dimensions into one mutable Asset status.

## Asset codes and identity

Asset identity is immutable. Asset has a required human-readable name and may have a human-facing Asset code under the Plant Model code contract.

A Functional Location code and an Asset code are different namespaces and may intentionally resemble one another where the plant uses familiar tag conventions. The interface must preserve which identity is being shown.

Manufacturer serial number, barcode, ERP identifier, and similar source identities are not silently substituted for Asset identity or Asset code; they are preserved as governed technical facts or external mappings according to their owning contract.

## Related documents

- [Functional Locations](010-functional-locations.md)
- [Asset classification and Operational Criticality](030-asset-classification-and-criticality.md)
- [Failure Events](../55-reliability/010-failure-events.md)
