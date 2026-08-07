# Process Units and Streams

Process Units describe stable wastewater-process functions. Process Streams describe directional logical flow between those functions. Together they provide process topology without turning physical plant hierarchy into process hierarchy or making Waiotech an engineering P&ID authoring system.

## A Process Unit

A Process Unit is a stable functional boundary in which a defined part of the wastewater process occurs.

Examples include:

- preliminary treatment;
- screening;
- grit removal;
- primary clarification;
- aeration train;
- aeration basin;
- secondary clarification;
- sludge dewatering;
- disinfection.

A Process Unit is not a Functional Location and is not an Asset.

## Use one recursive Process Unit hierarchy

A Process Unit has immutable identity, required name, and optional human-facing code under the Plant Model code contract.

A Process Unit may contain child Process Units in the same Tenant. The hierarchy must not contain cycles.

Parent changes are explicit governed changes and preserve effective hierarchy history when earlier operational evidence depends on the prior process decomposition. A materially different process function is represented by retiring the old Process Unit and creating a new identity rather than reusing the old identity.

The hierarchy expresses functional process decomposition only. Waiotech must not require fixed levels such as area, stage, train, unit, and step.

A plant may therefore model only the depth that is operationally useful while retaining one clear meaning for Process Unit.

## The Process Unit lifecycle

Process Unit uses:

```text
active → retired
```

Retirement prevents new normal Process activity and configuration against the Process Unit while preserving historical Conditions, Readings, Observations, Failure Event consequence links, Reports, and other evidence.

Retirement must evaluate active child Process Units, Process Streams, Measurement Points, and open Process Conditions.

## A Process Stream

A Process Stream is a stable logical flow of process medium between Process Units or between a Process Unit and the plant boundary.

Examples include:

- influent wastewater;
- effluent;
- return activated sludge;
- waste activated sludge;
- internal recycle;
- process air;
- chemical feed;
- service water;
- bypass flow.

A Process Stream is not a pipe, channel, valve, or other physical object. Physical infrastructure is represented through Functional Locations and Assets when Waiotech needs that physical history.

## Represent direction explicitly

A Process Stream records direction.

A Stream may have:

- an upstream Process Unit and downstream Process Unit;
- only a downstream Process Unit when it enters from outside the modelled plant boundary;
- only an upstream Process Unit when it leaves the modelled plant boundary.

At least one Process Unit endpoint is required. Branching and merging are represented by several explicit Streams rather than arbitrary graph expressions hidden inside one Stream.


## The Process Stream lifecycle and identity

A Process Stream has immutable identity, required name, and optional human-facing code under the Plant Model code contract.

Process Stream uses:

```text
active → retired
```

Retirement prevents new ordinary Process use while preserving historical Readings, Conditions, Failure consequence links, and topology evidence.

The process medium and directional endpoints define the Stream's stable operational meaning. Once the Stream has retained operational evidence, a materially different medium or endpoint path requires retirement and a new Process Stream rather than silently rewriting the earlier flow meaning. Name or code corrections remain governed identity-preserving changes.

## Keep stream medium governed but simple

A Process Stream may use a governed medium classification where operational interpretation depends on it. The classification must not become a substitute for process topology or an unrestricted engineering fluid-property model.

## Relate stable installed function to Process Units explicitly

A Functional Location may serve one or more Process Units when the stable installed function supports those process functions. One Process Unit may be served by several Functional Locations.

This relationship is independent of Functional Location containment and Process Unit containment. It must preserve effective history when changing the relationship would alter the interpretation of retained Process, Maintenance, or Reliability evidence.

A physical Asset normally acquires current process context through its effective installation at a Functional Location. Replacing the Asset therefore does not require rebuilding the stable process relationship.

Waiotech must not infer process service solely from physical containment or naming similarity.

## Do not make the diagram authoritative

A P&ID-oriented or process-flow view may render Process Units, Streams, Measurement Points, Functional Locations, and Assets.

The view is a projection over canonical relationships. Editing a visual diagram must not become an alternate ungoverned source of plant identity or topology.

## Related documents

- [Domain and plant model](../10-foundations/020-domain-and-plant-model.md)
- [Measurement Points](050-measurement-points.md)
- [Process Conditions](../35-process/020-process-conditions.md)
