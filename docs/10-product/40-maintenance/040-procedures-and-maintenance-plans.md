# Procedures and Maintenance Plans

Procedures define reusable governed maintenance methods through immutable published revisions. Maintenance Plans define recurring maintenance intent, exact published coverage, deterministic triggers, timing, and generation defaults through immutable published revisions. Historical work must always remain interpretable from the exact method and recurring policy that governed it.

## A Procedure

A Procedure is the stable identity of a reusable governed maintenance method.

Procedure lifecycle is:

```text
active → retired
```

An active Procedure is available for ordinary planning only when it has an effective published Procedure Revision. A retired Procedure remains available for historical interpretation but cannot be newly applied.

Retirement does not alter Procedure Revisions already referenced by Maintenance Plans or Work Orders.

## A Procedure Revision

A Procedure Revision contains one exact version of the governed maintenance method.

It may define:

- scope and purpose;
- ordered Task templates;
- instructions and cautions;
- prerequisites;
- Work Requirements;
- required discipline, competence, tool, or service context where defined;
- material expectations;
- measurement expectations;
- evidence expectations;
- acceptance criteria;
- completion requirements;
- verification expectations.

Procedure Revision lifecycle is:

```text
draft → published → superseded
draft → discarded
published → withdrawn
```

Draft revisions are editable and have no operational effect. Published revisions are immutable.

Publishing a new revision supersedes the previously effective published revision for new application. It does not alter an earlier revision or the work that used it.

## Preserve exact execution-method history

Procedure content may change while historical and active work must retain the exact method that applied.

Without immutable revisions, changing instructions, Requirements, measurements, evidence expectations, or acceptance criteria could rewrite the apparent basis of earlier or already-planned work.

Waiotech therefore preserves the exact Procedure Revision wherever the method is operationally applied.

## Present Procedure changes through stable identity and publication

Users manage the stable Procedure and its proposed unpublished changes rather than manually managing revision numbers or version graphs.

Before publication, the experience must make the proposed method understandable and reviewable. Publication establishes a new immutable Procedure Revision.

Restoring earlier content creates new unpublished changes. It never edits or reactivates the old published revision.

## Withdraw an unsafe or materially invalid published revision explicitly

An authorized Actor may withdraw a published Procedure Revision when it must no longer be used for new work because it is unsafe, materially incorrect, or otherwise invalid.

Withdrawal:

- prevents new application of that revision;
- preserves the revision and withdrawal evidence historically;
- does not rewrite completed or accepted execution evidence;
- requires affected unstarted or active Work Orders to be made visible for governed review;
- does not itself retire the stable Procedure.

A replacement method requires another reviewed and published Procedure Revision.

## Snapshot exact method meaning into Work Orders

Applying a Procedure Revision to a Work Order creates Work Order-owned execution content derived from that exact revision.

The snapshot may include:

- Tasks;
- instructions;
- Requirements;
- expected evidence;
- measurements;
- acceptance criteria;
- verification requirements.

The Work Order preserves source Procedure, exact Procedure Revision, generated content, application time, and attributable application basis.

The snapshot is not a live mirror. Later Procedure publication, supersession, withdrawal, or retirement must not silently change accepted Work Order content.

## Allow job-specific additions without changing the source Procedure

A planner may add job-specific content where authorized, including additional Tasks, contextual instructions, Requirements, evidence expectations, or acceptance criteria.

Job-specific additions remain distinguishable from content originating from the Procedure Revision.

Adding work-specific content does not modify the source Procedure or Procedure Revision.

## Govern variation from Procedure-derived content

Controlled Procedure-derived content must not be changed through an unexplained ordinary edit.

Where Product Authority permits job-specific variation, the Work Order preserves:

- original Procedure-derived content;
- changed content;
- reason for deviation;
- authorized Actor;
- change time;
- approval or Decision where required.

The source Procedure Revision remains unchanged.

## Keep maintenance execution measurements contextual

Procedure Tasks may require numeric measurements whose business meaning belongs to Maintenance execution, such as torque, clearance, alignment, insulation resistance, or inspection values.

Those values remain Maintenance execution evidence unless the action intentionally records an operational value against a configured Measurement Point as a Process Reading.

Waiotech does not create one universal Measurement aggregate merely because several domains record numeric evidence.

## A Maintenance Plan

A Maintenance Plan is the stable identity of governed recurring maintenance intent.

It provides continuity across published revisions, activation, pause and resume, and later policy changes while keeping each historical occurrence tied to the exact rules that governed it.

Maintenance Plan lifecycle is:

```text
inactive → active
active → paused → active
inactive / active / paused → retired
```

- **Inactive:** ordinary new occurrence recognition and generation are not performed.
- **Active:** the effective published Plan Revision may recognize occurrences and generate work.
- **Paused:** ordinary new generation is suspended while Plan identity, trigger history, already-recognized occurrences, and obligations reached during the pause remain governed.
- **Retired:** no new ordinary occurrences or work are generated after retirement; historical policy and occurrences remain preserved.

A Plan cannot become active without an effective published Plan Revision.

Pause and retirement are prospective controls. They never rewrite Scheduled Work or Work Orders that already exist.

## A Maintenance Plan Revision

A Maintenance Plan Revision is one immutable published version of the recurring-maintenance policy.

It defines:

- maintenance class;
- exact Procedure Revision where applicable;
- exactly one trigger definition;
- exactly one target kind;
- target-authoring definition and exact resolved target set;
- timing window and generation horizon;
- primary and supporting discipline where applicable;
- default responsibility;
- urgency and Work Order defaults;
- occurrence-handling rules;
- other product-defined deterministic generation facts.

Maintenance Plan Revision lifecycle is:

```text
draft → published → superseded
draft → discarded
```

Draft revisions have no occurrence-generation authority. Published revisions are immutable.

## Preserve recurring-policy history through Plan Revisions

Changes to method, target coverage, trigger, frequency or threshold, timing tolerance, classification, responsibility, discipline, or Work Order defaults can change the maintenance obligation.

Historical Scheduled Work and Work Orders must therefore preserve the exact Plan Revision that established their basis.

Changing the stable Plan never rewrites the apparent rules that produced earlier occurrences.

## Pin every Plan Revision to an exact Procedure Revision

Where a Maintenance Plan uses a Procedure, the published Plan Revision references one exact published Procedure Revision.

A Plan must not silently begin using a newer Procedure Revision merely because the Procedure has another effective revision.

Adopting a different Procedure Revision requires reviewed Plan changes and publication of a new Plan Revision.

A withdrawn Procedure Revision blocks new publication or application according to its withdrawal contract and requires affected existing work to be reviewed rather than silently mutated.

## Make Plan publication a business-impact review

Users manage proposed Plan changes through the stable Maintenance Plan. Before publication, Waiotech must make the resulting method, trigger, timing, defaults, target coverage, trigger prerequisites, and prospective occurrence impact reviewable.

Users do not manually manage revision numbers, branches, or supersession.

Publication establishes the immutable Plan Revision only after the protected publication action verifies the reviewed meaning.

## Use Functional Location or Asset Plan targets

One Plan Revision has exactly one target kind:

- Functional Location; or
- Asset.

A Plan may cover several resolved targets of that one kind when they genuinely share the same maintenance intent, method, trigger contract, timing, classification, discipline, and default responsibility.

Each resolved target receives its own Scheduled Work occurrence and normally its own Work Order. Multi-target Plan coverage must not combine independent physical work into one executable record merely because the targets share a Plan.

Functional Location targets are appropriate when recurring work concerns a stable installed position, fixed place, or function whose identity survives physical Asset replacement.

Asset targets are appropriate when recurring maintenance must follow the identified physical Asset itself.

A recurring programme requiring both target kinds uses separate Plans unless Product Authority explicitly defines another typed contract.

## Support explicit target selection and governed selectors

Plan authoring may use:

1. **Explicit selection:** specifically selected canonical Functional Locations or Assets of the Plan target kind.
2. **Governed selector:** one product-defined typed selector used to resolve a proposed target set.

The selector is an authoring mechanism. It is never a continuously evaluated source of operational coverage.

Publication always preserves one exact resolved target set.

## Keep selectors typed, narrow, and product-governed

Waiotech must not expose arbitrary field/operator/value expressions, SQL-like queries, scripts, executable formulas, user-defined metadata predicates, or generic expression builders for Plan target coverage.

Supported selector dimensions may include only Product Authority-defined plant facts.

### Functional Location selectors

Supported dimensions may include:

- exact Functional Location branch;
- active lifecycle;
- Operational Criticality where defined;
- explicitly supported external-mapping criteria.

### Asset selectors

Supported dimensions may include:

- Asset Classification;
- physical Asset subtree where meaningful;
- active lifecycle;
- current installation within a Functional Location branch where explicitly supported;
- explicitly supported external-mapping criteria.

Maintenance discipline, Team names, display text, or naming convention must not substitute for governed Plant facts.

## Preview and publish exact target coverage

A target preview is non-authoritative and must make the exact proposed coverage understandable before publication.

The preview includes, as applicable:

- normalized authoring definition;
- evaluation time;
- target kind;
- proposed canonical targets;
- exclusions and reasons;
- ineligible targets;
- missing trigger prerequisites;
- target additions and removals relative to the effective published revision;
- resolved target-to-Measurement-Point bindings for Measurement Point triggers;
- count and deterministic reviewed-content digest.

Publication re-resolves selector-based coverage inside the protected publication action. If the reviewed meaning or digest changed, publication is rejected and a new review is required.

The published Plan Revision owns the immutable resolved target set by canonical Functional Location or Asset identity.

## Preserve explainable omissions and all-or-nothing publication

A selector candidate excluded from publication because required facts are missing or invalid must have an explainable reason in the reviewed result.

Publication must not silently drop unresolved or invalid targets and continue with a different coverage set than the Actor reviewed.

A required target or trigger prerequisite that cannot be resolved blocks publication unless Product Authority explicitly defines a reviewed exclusion path.

## Do not let changing Plant facts silently change published coverage

After publication, changes to Functional Location hierarchy, Asset composition, Asset Classification, Asset installation, lifecycle, Operational Criticality, Measurement Points, or external mappings do not add or remove published Plan targets.

Waiotech may derive target drift by comparing current selector resolution with published coverage. Drift is awareness and review input, not mutation.

Coverage changes require a new reviewed and published Plan Revision.

## Keep current eligibility separate from historical coverage

A target remains part of the historical published Plan Revision even when it later becomes retired, decommissioned, uninstalled, otherwise ineligible, or unable to satisfy a trigger prerequisite.

At occurrence evaluation time, Waiotech evaluates current eligibility according to the Plan and Scheduled Work contracts.

An ineligible target creates a visible planning, reconciliation, or disposition obligation where required. It does not silently disappear from the historical Plan definition.

Existing Scheduled Work and Work Orders remain governed by their own records and actions.

## Generate only from immutable published coverage

Scheduled Work recognition uses the exact targets and trigger bindings preserved by the effective published Plan Revision.

A selector may be re-evaluated to preview proposed changes, detect drift, or prepare unpublished Plan changes. It must not act as a live filter that silently changes generation coverage.

## Support calendar and Measurement Point triggers

Alpha Maintenance Plans support exactly two deterministic trigger kinds:

- calendar trigger;
- Measurement Point trigger.

Analytical judgment, alarms, Process Conditions, predictive models, and anomaly detection are not arbitrary Plan trigger expressions. They may create or support Work Requests or direct accepted work only through explicit owning-domain actions.

## Calendar trigger

A calendar trigger defines deterministic recurrence and timing facts governed by the Plan Revision.

The product preserves nominal occurrence identity separately from recognition, generation, due, execution, and completion times.

Waiotech must not expose an unrestricted cron expression as user-facing Product Authority.

## Measurement Point trigger

A Measurement Point trigger defines a deterministic recurring maintenance obligation from accepted Process Readings.

The Plan Revision defines:

- measured quantity and canonical unit required by the trigger;
- trigger direction and threshold-sequence semantics;
- threshold increment or exact governed threshold rule;
- planning or generation horizon where supported;
- reset, rollover, replacement, or correction behavior when required by the measured quantity;
- accepted Reading-quality requirements;
- any other deterministic facts needed to prevent ambiguous occurrence recognition.

For every resolved Plan target, the published Plan Revision preserves exactly one compatible trigger Measurement Point.

That Measurement Point must observe the resolved target directly as its supported subject and must satisfy the trigger quantity and unit contract.

Authoring may resolve a trigger Measurement Point automatically only when exactly one eligible Measurement Point exists for the target. Zero or several candidates block publication until the author explicitly resolves the binding or changes the target coverage.

The reviewed target preview shows the resolved Measurement Point for every target, and the reviewed-content digest includes those bindings.

The published target-to-Measurement-Point binding is immutable with the Plan Revision. Later Measurement Point creation, retirement, instrument replacement, source remapping, Asset replacement, or other Plant Model change does not silently change the trigger source for existing published coverage.

Maintenance does not create a separate meter or usage-source registry. Measurement Point identity belongs to Plant; Process Reading evidence belongs to Process.

Human and machine Readings may satisfy the trigger when their provenance is permitted by the Plan rule and their quality is acceptable.

A `bad` Reading must never recognize a maintenance occurrence. `uncertain` or `unknown` quality may affect automated recognition only when the Plan trigger contract explicitly permits it.

## Preserve cumulative, reset, rollover, and correction semantics explicitly

Measurement Point triggers must distinguish cumulative quantities from ordinary instantaneous values.

Where a counter can reset, roll over, be replaced, or be corrected, the trigger contract must define deterministic sequence ownership and occurrence recognition.

Waiotech must not infer a new maintenance threshold merely because a later value is lower than an earlier one.

When a Reading used for recognition is later corrected or invalidated, Scheduled Work reconciliation follows its own governed contract. Historical occurrence or accepted work must not be silently erased.

## Keep exactly one trigger per Plan Revision

Combined trigger expressions are outside the Alpha contract.

A maintenance programme requiring different trigger logic uses separate Plans unless Product Authority explicitly introduces one typed combined-trigger contract.

Arbitrary logical expressions remain prohibited.

## Preserve obligations through pause and retirement

Pausing a Plan does not erase already-recognized Scheduled Work or trigger evidence that becomes relevant during the pause.

On resume, Waiotech evaluates preserved trigger history according to the Scheduled Work contract so obligations reached during the pause receive explicit recognition or disposition rather than disappearing.

Retirement prevents new ordinary post-retirement occurrences while preserving all earlier Plan Revisions, Scheduled Work, Work Orders, trigger evidence, and publication history.

## Plan publication does not rewrite existing work

Publishing a new Plan Revision affects subsequent occurrence recognition according to its effective rules.

Existing Scheduled Work and Work Orders preserve their original Plan Revision, Procedure Revision, target, trigger facts, and evidence unless an explicit governed reconciliation action changes a fact that Product Authority permits to be reconciled.

Definition publication itself never edits accepted work.

## Related documents

- [Scheduled Work](050-scheduled-work.md)
- [Measurement Points](../30-plant/050-measurement-points.md)
- [Process Readings and Operational Observations](../35-process/010-readings-and-observations.md)
- [Revisions and publication](../../20-engineering/20-data/020-revisions-and-publication.md)
- [Plan target resolution](../../20-engineering/20-data/030-plan-target-resolution.md)
