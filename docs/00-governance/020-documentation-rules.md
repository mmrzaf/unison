# Documentation rules

Waiotech documentation is a numbered, subject-oriented library. It defines Waiotech directly and can be combined into one deterministic Markdown document.

## Numbering and order

Every maintained directory contains `000-index.md`. Every maintained document uses a zero-padded numeric prefix.

Numeric path order is canonical reading order. New documents use available numbering gaps instead of renaming unrelated files.

Example:

```text
10-product/
└── 40-maintenance/
    ├── 000-index.md
    ├── 010-maintenance-classification-and-control.md
    ├── 020-accepted-maintenance-need.md
    ├── 030-work-requests.md
    └── 040-procedures-and-maintenance-plans.md
```

## Subject-oriented writing

Documents begin with the subject itself. They do not reproduce Q&A history, deliberation, rejected alternatives, extraction labels, document metadata panels, or commentary about how the document should be read.

A document includes only sections needed by its subject. Operational aggregates, evidence records, configuration entities, projections, engineering mechanisms, and experience contracts use structures appropriate to their concerns rather than one rigid template.

## Granularity

A separate document is appropriate when a subject has distinct ownership, lifecycle, security meaning, substantial rules, or a stable independent audience.

Closely related rules remain together when splitting them would produce shallow files, repeated introductions, or competing authority.

## Definitions and fields

Product documents define product-significant facts only. A fact is product-significant when changing it can alter meaning, permitted actions, lifecycle outcomes, responsibility, authorization, Decisions, required evidence, historical interpretation, cross-domain behavior, or user-facing obligations.

Storage columns, framework properties, transport shapes, repository paths, and generated-client mechanics belong to Engineering Authority.

## Lifecycles, actions, and workflows

Entity lifecycles and named actions live with the owning domain. Workflow documents coordinate those actions and may contain a concise linked summary, but they do not restate complete transition tables or introduce alternate states and guards.

Lifecycle, action, permission, responsibility, approval, and separation-of-duty matrices are generated or verified from their owners. Hand-maintained summaries cannot become a second authority source.

## Revisions

Immutable revisions preserve exact historical meaning for governed definitions. Ordinary users interact with stable objects through edit, review, publish, discard, history, and restore operations.

Restoring historical content creates new unpublished changes. It does not edit, reactivate, or rewrite a prior published revision.

## Cross-document navigation

A document may end with `Related documents` when direct links materially improve navigation. Links point to prerequisites, coordinated workflows, technical realizations, or experience contracts. The section is omitted when it adds no value.

Related links do not repeat the target document’s rules.

## Architecture decisions and deviations

Architecture decisions select bounded technical mechanisms delegated by Engineering Authority. They cannot redefine product meaning, create alternate authority paths, weaken mandatory guarantees, or authorize permanent unbounded exceptions.

A deviation is narrow, time-bounded, risk-assessed, approved, observable, and removable. Repeated renewal does not make a deviation permanent.

## Generated output

`just docs` validates the maintained library and writes the cumulative Markdown file in canonical numeric order.

Generated output:

- is deterministic;
- contains a cover and table of contents;
- identifies every ordered source file;
- marks source-document boundaries;
- rewrites internal document links to cumulative anchors;
- excludes `90-generated/` from its source set;
- carries a generated-file warning;
- must not be edited manually.

`just verify-docs` fails when validation fails or generated output differs from the maintained sources.

## Prohibited content

Maintained documentation must not contain:

- Q&A headings or `Answer` and `Decision` extraction labels;
- document IDs, classification banners, owner banners, or canonical-status panels;
- task markers, unresolved placeholders, or empty sections;
- delivery progress, roadmap, phase, or implementation-status language;
- obsolete terminology retained as an alternate canonical vocabulary;
- manually edited generated output;
- unresolved internal Markdown links.

A genuine unresolved product or engineering rule is written as `SPEC GAP` at the exact point where the missing authority blocks a complete statement.

## Related documents

- [Authority and precedence](010-authority-and-precedence.md)
- [Canonical terminology](../70-reference/010-canonical-terminology.md)
