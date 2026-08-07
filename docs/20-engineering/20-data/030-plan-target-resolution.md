# Plan target resolution

Maintenance Plan targets are authored through explicit canonical selections or typed governed selectors, previewed as non-authoritative candidate coverage, resolved atomically during protected publication, and preserved as immutable published targets and trigger bindings.

## Keep target kind and trigger interpretation unambiguous

One Maintenance Plan Revision has exactly one target kind:

- Functional Location;
- Asset.

The target kind is part of revision meaning and cannot vary row by row inside one published revision.

A Plan requiring both kinds is represented by separate Plans unless Product Authority introduces another explicit typed contract.

Measurement Point-triggered Plans additionally preserve one compatible trigger Measurement Point for every resolved target. Trigger identity is therefore part of published target resolution rather than a later lookup against mutable Plant data.

## Explicit target selection

Explicit selection accepts canonical target identities of the Plan target kind.

Application validation must establish at publication time:

- Tenant ownership;
- target kind;
- identity existence;
- duplicate elimination under canonical identity;
- lifecycle and publication eligibility rules;
- trigger prerequisites;
- any Product Authority-defined exclusion or blocking condition.

An external identifier may be used for lookup, but published coverage stores canonical Waiotech target identity.

## Typed selector

A selector is a versioned Product Authority-defined structure. It is not a generic field/operator/value language.

The selector schema explicitly defines which governed facts may participate, normalization, validation, and interpretation.

### Functional Location selector dimensions

Permitted dimensions may include only explicitly supported facts such as:

- exact Functional Location branch;
- lifecycle;
- Operational Criticality;
- governed external-mapping criteria.

### Asset selector dimensions

Permitted dimensions may include only explicitly supported facts such as:

- Asset Classification;
- physical Asset subtree where Product Authority permits it;
- lifecycle;
- current installation within a Functional Location branch where explicitly supported;
- governed external-mapping criteria.

Selectors must not expose arbitrary SQL columns, JSON paths, free-form field names, executable expressions, browser-defined predicates, Team names as Plant semantics, or internal ORM structure.

## Keep selector behavior reviewable and deterministic

For one normalized selector, Tenant state, evaluation instant, catalogue/reference-data revision, and selector contract version, target resolution must be deterministic.

Ordering used for review and digest generation is canonical and stable.

Selector evaluation must be Tenant-scoped before any other predicate can broaden the candidate set.

## Preview is non-authoritative

A preview is a review artifact, not published coverage.

It includes at least:

- target kind;
- normalized selector or explicit-selection summary;
- selector contract version where applicable;
- evaluation time;
- included canonical target identities;
- excluded or ineligible candidates and typed reasons;
- missing trigger prerequisites;
- target-to-Measurement-Point binding where applicable;
- additions and removals relative to effective published coverage where applicable;
- deterministic reviewed-content digest;
- bounded count and truncation/limit meaning where relevant.

The browser must not construct its own candidate target set from partial registry data.

## Resolve operational coverage only during protected publication

Publication re-evaluates the proposed selection inside the authoritative Application transaction using the same typed resolution contract.

For selector-based authoring, the publication result must match the reviewed digest. If it does not, publication fails with an explicit target-drift result and returns or makes available a new reviewable preview.

The Server must never publish a target set materially different from the one the Actor reviewed merely because Plant facts changed between preview and publication.

## Prevent partially published target sets

Target resolution and revision publication are atomic.

If any required target, trigger binding, or publication prerequisite cannot be established, the Plan Revision is not partially published unless Product Authority explicitly defines a reviewed exclusion rule whose result was part of the reviewed digest.

Database rows for some valid candidates do not justify silently dropping invalid candidates.

## A resolved Plan target

A resolved target is immutable published evidence owned by one Plan Revision.

It preserves at least:

- Plan Revision;
- target kind;
- canonical target identity;
- resolution provenance as explicit selection or selector result;
- publication-time relationship to the reviewed target-set digest;
- Product Authority-required snapshot facts needed for interpretation;
- resolved trigger Measurement Point where the trigger kind requires one.

Resolved target identity is not recalculated during ordinary occurrence generation.

## Resolve Measurement Point trigger bindings per target

For a Measurement Point-triggered Plan, every target must resolve exactly one trigger Measurement Point before publication.

The candidate Measurement Point must:

- belong to the same Tenant;
- be active for publication use;
- observe that resolved target directly as its supported subject;
- use the measured quantity required by the trigger;
- have a canonical unit compatible with the trigger contract;
- satisfy any additional Product Authority-defined trigger eligibility rule.

Automatic selection is allowed only when exactly one eligible candidate exists.

Zero eligible candidates and several eligible candidates are both publication blockers until the author explicitly resolves the ambiguity or changes Plan coverage/configuration.

The preview and deterministic digest include each target-to-Measurement-Point binding. Publication persists the binding with the Plan Revision.

A later Measurement Point creation, retirement, source remapping, instrument replacement, Asset replacement, or Plant-model change never substitutes another trigger point inside an already-published revision.

## Preserve published coverage immutably

After publication, Functional Location hierarchy, Asset composition, Asset Classification, Asset installation, lifecycle, Operational Criticality, Measurement Point configuration, and external mappings may change without rewriting the Plan Revision's published target set.

Historical operation uses the resolved target identity and trigger binding preserved by the Plan Revision.

## Target drift is a derived comparison

Target drift compares current selector resolution with one published resolved target set at an explicit evaluation time.

It may identify:

- newly matching targets;
- targets that no longer match;
- retired or decommissioned targets;
- changed installation context;
- classification changes;
- missing or ambiguous trigger prerequisites;
- external-mapping differences;
- other Product Authority-defined causes.

Drift is review information only. It never mutates published coverage or creates/removes Scheduled Work.

A coverage change requires a new draft and reviewed Plan publication.

## Separate historical coverage from current action eligibility

A target may remain historical published coverage while becoming currently ineligible for new occurrence recognition or generation.

Occurrence evaluation therefore checks current eligibility through the owning Plan/Scheduled Work contract without altering the historical target row.

An ineligible published target produces an explicit planning, reconciliation, or disposition result where required. It must not silently disappear.

## Do not rewrite accepted work through definition publication

Publishing or superseding a Plan Revision never changes existing Scheduled Work or Work Orders.

Those records retain their original Plan Revision, Procedure Revision, resolved target, trigger identity, and evidence.

Where a business correction is required, it occurs through the affected record's explicit reconciliation or lifecycle action rather than by changing the source definition.

## Make omitted coverage explainable

Every candidate excluded from reviewed coverage because of missing facts, invalid lifecycle, trigger incompatibility, authorization, or another product rule must have a typed explainable reason when the exclusion is material to publication review.

Unknown or unresolved meaning must not be translated into silent exclusion.

## Bound selector cost and protect Tenant isolation

Selector evaluation uses indexed Plant facts, deterministic ordering, bounded result size, explicit timeout/resource limits, and Tenant predicates enforced below Application code where persistence authority requires it.

A selector that exceeds supported cost or size fails with a typed reviewable result rather than degrading into an unbounded query.

Selector evaluation must not expose records or aggregate counts from another Tenant through results, errors, timing-sensitive diagnostics, or generated previews.

## Version selector structure without making historical operation depend on it

Persisted selector definitions carry a contract version when interpretation can evolve.

A supported release may read earlier selector versions for review or migration according to Engineering Authority, but historical occurrence operation does not depend on re-running obsolete selector logic because published coverage is frozen as canonical resolved targets.

Selector normalization and digest calculation are deterministic and versioned together where required.

## End live selector authority at publication

After publication, the selector remains provenance and authoring history. It is not operational authority for automatic target addition/removal.

Ordinary Scheduled Work recognition reads the immutable resolved target set and applicable trigger bindings.

Re-evaluation is limited to preview, drift analysis, preparation of new unpublished changes, and explicit governance functions.

## Verify authoring, publication, and operation separately

Tests must distinguish:

- selector/explicit-selection authoring and normalization;
- preview and digest generation;
- concurrency between preview and protected publication;
- all-or-nothing target and trigger-binding persistence;
- drift detection;
- historical reproducibility after Plant changes;
- occurrence generation from immutable coverage;
- Tenant isolation and bounded selector cost.

A passing selector query test does not prove publication or operational correctness.

## Related documents

- [Procedures and Maintenance Plans](../../10-product/40-maintenance/040-procedures-and-maintenance-plans.md)
- [Scheduled Work](../../10-product/40-maintenance/050-scheduled-work.md)
- [Revisions and publication](020-revisions-and-publication.md)
- [Measurement Points](../../10-product/30-plant/050-measurement-points.md)
