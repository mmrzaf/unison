# Reports

Report Types define governed report meaning and temporal semantics. Report Runs evaluate current authority, produce immutable artifacts, and preserve completion, failure, cancellation, and retrieval evidence.

## Alpha Report Type catalogue

The Alpha catalogue is closed and contains:

- Work Order backlog;
- overdue Scheduled Work;
- maintenance cost;
- Process Condition history;
- Process Round history;
- Failure Event history;
- stock availability;
- Inventory Movement history.

Each Report Type owns its filters, temporal meaning, columns, grouping, sorting,
sensitivity, CSV/PDF support, retention, and maximum execution limits. Adding a
Report Type requires a Product Authority amendment and complete implementation.

## A Report Type

Waiotech must keep Report Types product-defined and governed.

A Report Type is a product-defined governed analytical view.

It defines:

- authoritative source facts;
- supported measures;
- supported filters;
- supported grouping and sorting;
- temporal and as-of semantics;
- sensitivity classification;
- available output formats;
- export controls.

Examples may include:

- Work Order backlog;
- maintenance cost;
- Failure Event history;
- Process Round history;
- overdue Scheduled Work;
- stock availability;
- Inventory Movement history.

Tenants cannot create arbitrary joins, calculations, or source queries.

## A Saved Report Configuration

Waiotech must allow reusable report configuration without introducing a general-purpose query builder.

A Saved Report Configuration is a reusable selection of options supported by a Report Type.

It may contain:

- filters;
- selected columns;
- grouping;
- sorting;
- date range;
- output format;
- display preferences.

It does not define new business meaning, joins, calculations, or authoritative facts.

A configuration may belong to a Tenant or individual User according to its visibility rules.

## Provide controlled analytical flexibility rather than unrestricted querying

Waiotech must provide controlled analytical flexibility rather than unrestricted querying.

An unrestricted builder could create:

- inconsistent calculations;
- unsupported joins;
- alternate definitions of product metrics;
- access-control mistakes;
- misleading reports presented as authoritative.

Waiotech may provide flexible filtering, grouping, supported calculations, and governed analytical views within each Report Type.

## Treat reports as reproducible derived outputs, not alternate truth sources

Waiotech must treat reports as reproducible derived outputs, not alternate truth sources.

A report result is a derived view of authoritative records under a defined:

- Report Type;
- revision;
- parameter set;
- Tenant context;
- temporal rule;
- data cutoff;
- access context.

The underlying domain records remain authoritative.

A report artifact may prove that a particular derived view was produced at a particular time, but it does not replace the source facts.

## Define time and as-of meaning separately for each Report Type

Waiotech must define time and as-of meaning separately for each Report Type.

Every Report Type must define how time affects its result.

Possible semantics include:

- state at execution time;
- lifecycle state at a historical cutoff;
- events effective during a selected period;
- actions recorded during a selected period;
- transactions posted before a cutoff;
- historical cost attributed during a period.

A generic date range must not be assumed to mean the same thing for every report.

## A Report Run

Waiotech must make every generated report reproducible and traceable to its exact semantics.

A Report Run is one execution of a Report Type using an exact configuration and parameter set.

It preserves:

- Report Type and exact product version;
- configuration snapshot;
- actual parameters;
- requesting Actor;
- Tenant context;
- request time;
- start and completion times;
- as-of rule;
- data cutoff;
- output format;
- sensitivity classification;
- result artifact;
- failure evidence where applicable.

## The Report Run lifecycle

Waiotech must use a small execution lifecycle with truthful cancellation semantics.

The lifecycle is:

```text
queued → running → completed
queued → cancelled
queued / running → failed
running → cancelled
```

A running Report Run enters `cancelled` only when execution is successfully stopped and no completed artifact is produced.

A completed Report Run cannot be cancelled.

## Do not label a completed run as cancelled merely because cancellation was requested

Waiotech must not label a completed run as cancelled merely because cancellation was requested.

Cancellation is successful when the run stops without producing a completed report artifact.

For queued work, cancellation prevents execution.

For running work, cancellation requests termination. When termination cannot be completed and the report finishes successfully, the run remains `completed`.

## Keep failed report evidence clear without exposing unsafe technical details

Waiotech must keep failed report evidence clear without exposing unsafe technical details.

A failed Report Run should preserve:

- failure classification;
- safe explanatory message;
- failure time;
- retry relationship where applicable;
- technical diagnostic reference where appropriate;
- absence or invalidity of any partial artifact.

Partial output must not be presented as a completed report unless the Report Type explicitly supports partial results.

## Avoid unnecessary approval workflows for ordinary reporting

Avoid unnecessary approval workflows for ordinary reporting.

Ordinary report generation requires:

- applicable Permission;
- access to the requested information at execution time;
- valid supported parameters;
- compliance with Report Type sensitivity controls.

Approval is required only when a specific sensitive report or export mode explicitly requires it.

## Checked when a report is generated

Waiotech must authorize the requested report at execution time.

At generation time, Waiotech evaluates:

- requesting Actor;
- Tenant context;
- applicable Permissions;
- access to the requested data;
- selected parameters;
- Report Type sensitivity;
- export or approval requirements.

The resulting artifact contains only the data authorized for that run.

## Freeze report content, not access rights at retrieval

Waiotech must freeze report content, not access rights at retrieval.

The report content is frozen at completion, but access to that artifact is evaluated whenever it is retrieved or exported.

A User whose access ends after artifact creation must not retain retrieval authority merely because they generated or accessed the report previously.

## Checked when a Report Artifact is retrieved

Recheck authority at retrieval and export.

Retrieval should evaluate:

- Tenant participation effective at retrieval;
- Permission effective at retrieval;
- Report Type sensitivity controls effective at retrieval;
- access to the artifact’s governing report context at retrieval;
- export-specific controls where applicable;
- artifact availability and integrity.

Access is not inherited permanently from the Actor who generated the report.

## Keep completed report content immutable

Waiotech must keep completed report content immutable.

A completed Report Artifact represents the exact result produced by that Report Run.

Changes to source records, policy, access, Report Type version, or saved configuration do not mutate the existing artifact.

A new result requires a new Report Run.

## Define export controls explicitly per Report Type and output mode

Waiotech must define export controls explicitly per Report Type and output mode.

Sensitivity is defined by the Report Type and output mode.

Sensitive exports may include:

- personal information;
- security or access configuration;
- Failure Event investigations;
- authorization evidence;
- bulk operational records;
- commercially restricted information.

A sensitive export may require:

- additional Permission;
- reason;
- destination classification;
- approval;
- identifying metadata or watermark;
- expiry;
- export audit evidence.

## Do not treat every generated report as permanent business evidence

Waiotech must not treat every generated report as permanent business evidence.

Not automatically.

A Report Artifact is normally a reproducible derived output.

It becomes governed evidence only when an owning domain explicitly links it as evidence for a Decision, action, investigation, or other controlled subject.

The underlying source records remain authoritative regardless.

## The relationship between Report Artifact and Attachment

Waiotech must separate report-output meaning from file identity while keeping a one-to-one artifact file relationship.

Report Artifact owns the business meaning of one completed report output. Attachment owns the accepted file identity, integrity, quarantine status, and storage availability.

The relationship is:

```text
Report Run
└── Report Artifact
    └── exactly one Attachment
```

Replacing the file requires a new Report Artifact and a new Attachment. The original remains preserved.

## Related documents
- [Notifications](020-notifications.md)
- [Attachments and evidence](030-attachments-and-evidence.md)
- [Report and Notification processing](../../20-engineering/50-background-processing/020-report-and-notification-processing.md)
