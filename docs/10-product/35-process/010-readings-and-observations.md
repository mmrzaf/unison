# Process Readings and Operational Observations

Process Readings preserve structured operational values at configured Measurement Points. Operational Observations preserve qualitative or contextual evidence. Human entry is a first-class Process workflow; machine submission supplements it through explicit source authority.

## A Process Reading

A Process Reading is immutable operational evidence that one value was observed for one Measurement Point.

It preserves at least:

- Measurement Point;
- reported value and unit;
- canonical normalized value and unit where normalization is required;
- effective time when the value applies or was observed;
- recorded time when Waiotech accepted the Reading;
- recording Actor;
- provenance source;
- Data Source and source identity where machine-submitted;
- quality;
- correction relationship where applicable.

A Reading is not a Process Condition and does not by itself establish that action is required.

## Human entry is primary and normal

An authorized User may record a Reading manually for a Measurement Point that permits human entry.

The interface should default known context such as Measurement Point, canonical unit, current time, Tenant, and User rather than asking the User to reconstruct it.

Manual Reading does not mean low-quality Reading. Its provenance remains human and its quality is explicit where uncertainty exists.

## Machine submission uses the same business record

A machine-submitted value accepted through an Integration Principal creates the same Process Reading concept with different provenance.

Waiotech must not maintain separate human-reading and machine-reading truths for the same Measurement Point merely because the ingestion path differs.

The Integration Principal, Data Source, external source identity, and effective time remain attributable.

## Reading quality

Canonical Reading quality values are:

- `good`;
- `uncertain`;
- `bad`;
- `unknown`.

Quality describes confidence in the observed value, not whether the operational condition is desirable.

A bad Reading may remain valuable evidence but must not drive automated Maintenance trigger recognition. Uncertain or unknown quality may influence automated use only where the consuming Product Authority explicitly permits it.

Human review may still use lower-quality evidence with visible provenance.

## Preserve reported and canonical units

When a supported Reading is entered in a compatible unit different from the Measurement Point canonical unit, Waiotech must preserve the original reported value and unit and the deterministic normalized value used by Waiotech.

Unit conversion must not silently reinterpret an incompatible quantity.

## Correct Readings additively

Accepted Readings are not overwritten.

An authorized correction identifies the prior Reading, reason, correcting Actor, and correction time. A correction may invalidate the prior value and may establish a replacement Reading. The original evidence remains historically visible.

A corrected or invalidated Reading must not continue to drive derived current views or automated trigger behavior as though it remained accepted current evidence.

## An Operational Observation

An Operational Observation is immutable qualitative or contextual evidence about plant operation.

It may record:

- narrative observation;
- effective time;
- recording Actor;
- exactly one primary operational subject: Process Unit, Process Stream, Functional Location, or Asset;
- related Process Units, Streams, Functional Locations, Assets, Measurement Points, or Readings where useful;
- attachments and evidence;
- correction relationship where applicable.

The primary subject uses one of the explicit supported subject types above, not an arbitrary untyped identifier or generic subject engine.

## Keep Observation separate from Condition

An Observation records what was noticed. A Process Condition records an accepted operational situation that requires active handling, monitoring, or resolution.

Most Readings and Observations should never need a Process Condition.

## Keep Process evidence separate from Maintenance execution measurements

A technician measurement recorded as part of a Work Order task may remain Maintenance execution evidence when its meaning belongs only to that task.

When the observed value is intentionally part of the plant's ongoing operational history, it should be recorded against a configured Measurement Point as a Process Reading and may also be referenced by Maintenance.

The product must not force all numeric evidence into one universal Measurement aggregate.

## Related documents

- [Measurement Points](../30-plant/050-measurement-points.md)
- [Integration Principals](../20-identity-and-access/040-integration-principals.md)
- [Process Conditions](020-process-conditions.md)
