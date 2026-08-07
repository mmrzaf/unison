# Dashboard design conventions

Dashboard design conventions translate the Waiotech experience contract into consistent layout, density, hierarchy, interaction, state, and responsive behavior. They support operational clarity rather than defining product authority.

## Visual hierarchy

Every viewport should make the following order visually obvious where applicable:

1. where the user is and which operational subject is in context;
2. current state and material attention;
3. one primary next action;
4. the information needed to decide or act;
5. related history and secondary detail.

Pages must not use visual decoration to compete with operational meaning.

## Spacing scale

Use one shared spacing scale. Components must use design tokens rather than one-off page values for ordinary padding, margin, and gap.

| Token | Value | Typical use |
|---|---:|---|
| `space-1` | 4 px | icon-to-label, very tight inline separation |
| `space-2` | 8 px | compact control and key-value gaps |
| `space-3` | 12 px | compact rows, filter groups, related controls |
| `space-4` | 16 px | ordinary section and surface padding |
| `space-5` | 20 px | prominent object context and comfortable forms |
| `space-6` | 24 px | major section separation |
| `space-8` | 32 px | page-level separation where hierarchy benefits |

Operational registries use compact spacing; detail and decision surfaces use comfortable spacing. Dense information remains grouped rather than compressed into indistinguishable rows. New spacing tokens require a system-wide need rather than a one-page exception.

## Typography

Typography must create unmistakable page, section, record-identity, field-label, value, and supporting-text hierarchy. The ordinary Dashboard body size is 14 px; text smaller than 12 px is not used for operational content.

| Token | Size | Typical use |
|---|---:|---|
| `text-xs` | 12 px | metadata, compact timestamps, low-emphasis labels |
| `text-sm` | 13 px | dense table content and secondary text |
| `text-body` | 14 px | ordinary body and form content |
| `text-lg` | 16 px | section and workspace headings |
| `text-xl` | 20 px | object identity and narrow-layout page titles |
| `text-2xl` | 24 px | primary desktop page titles |

Font weight, spacing, and placement should carry hierarchy before font-size inflation. Codes, quantities, units, timestamps, and technical identifiers use readable tabular or monospace treatment only when it improves scanning. Machine-oriented values remain directionally isolated in both English and Persian layouts.

## Navigation hierarchy

Primary navigation follows the canonical Dashboard areas. Secondary navigation belongs inside the selected area and must not reproduce the complete application tree.

The interface should prefer contextual links between related records over forcing users to navigate back to top-level modules.

## Page headers

An operational page header normally includes:

- human operational identity;
- concise context breadcrumb or relationship;
- lifecycle or handling state where important;
- one primary available action;
- secondary actions in a labelled menu or grouped control.

A page should not display several equally dominant primary buttons.

## Master-detail workspace

High-volume workspaces may use a master-detail layout on suitable desktop widths.

The master region contains queue identity, search, filters, ordering, and records. The detail region contains selected-record context, important state, blockers, actions, related evidence, and history.

Selection, filters, ordering, page size, and relevant workspace mode should be URL-backed where practical so browser navigation restores the working context.

At narrower widths, master-detail becomes a focused list-to-detail flow while preserving return state.

## Operational detail composition

Plant, Process, Maintenance, Inventory, and Reliability detail experiences use a common conceptual order:

1. operational identity and state;
2. primary available action;
3. immediate context and attention;
4. working content;
5. connected cross-domain records;
6. evidence and operational timeline;
7. technical details.

The exact sections vary by subject. The common order must not force irrelevant tabs onto every record type.

## Plant explorer

Plant views may combine hierarchy, process topology, selected-object detail, and related records.

Functional Location hierarchy and Process Unit hierarchy must remain visually distinguishable. Asset installation is shown as a relationship to Functional Location, not as a fake shared tree level. Process Streams show direction.

Simple diagram or map lenses use the same canonical selection and detail patterns as list and hierarchy lenses.

## Process Reading entry

Reading entry should be exceptionally low-friction.

When context already determines Measurement Point, unit, Tenant, User, and normal current time, the primary form should emphasize only value and any genuinely optional note or time correction.

Source and provenance are shown after acceptance and when needed for interpretation. Machine source configuration never intrudes into routine human Reading entry.

## Process Round workspace

A Process Round workspace optimizes rapid sequential human entry. It shows Routine identity, current entry, remaining required entries, recent accepted values, concise reference guidance, and completion state without making the User navigate to each Measurement Point separately.

Keyboard and touch progression must be predictable. Accepting one entry should move naturally to the next required entry while preserving a clear path to review earlier entries.

## Process Condition workspace

Process Condition queues emphasize:

- condition description;
- Process Unit or Stream;
- handling state;
- owner Team and responsible User;
- latest significant Reading or Observation;
- next review obligation;
- related Maintenance or Failure Event status.

Selected-condition detail emphasizes the operational story and available actions rather than configuration metadata.

## Work Order workspace

Work Order views emphasize why work exists, Work Target, current readiness, blockers, responsibility, schedule, Tasks, material availability, Process or Failure context, evidence, and next action.

Lifecycle changes use explicit actions. Editable status dropdowns are prohibited.

## Inventory workspace

Inventory registries use tables where scanning quantity, location, availability, reservation, and replenishment state is more effective than cards.

Inventory detail emphasizes current custody position before Movement history.

## Reliability workspace

Failure Event detail emphasizes failed function, primary target, actual consequence, operational condition, investigation condition, restoration history, related Process evidence, Maintenance response, Cause Assessments, and recurrence.

Derived metrics must link back to supporting events where feasible.

## Button hierarchy

Use these visual roles consistently:

- **primary** — one dominant next action in the current context;
- **secondary** — important neutral action;
- **outline** — lower-weight navigation or inline action;
- **ghost** — contextual control that should not compete with content;
- **danger** — destructive or irreversible action with explicit confirmation or guard.

A screen may contain several actions but should rarely contain several simultaneous primary visual actions.

## Forms and dialogs

Short focused actions use dialogs or compact inline flows when doing so preserves context.

Long or consequential workflows use full pages or structured step experiences when users need sustained context, evidence review, or several meaningful sections.

Dialogs must not become miniature scroll-heavy record-edit pages.

## Empty, loading, error, refreshing, and success states

Every data-dependent view handles:

- first loading;
- successful empty state;
- recoverable and non-recoverable error;
- background refresh while current content remains visible;
- submitted or completed success where acknowledgement is useful.

State containers use the same visual system as ordinary content and avoid unnecessary layout jump.

Error presentation includes a human explanation, safe next action, and correlation reference when support diagnostics require one.

## Radius and control scale

Waiotech uses restrained geometry. Ordinary surfaces and controls should not look excessively soft or decorative.

| Token | Value | Typical use |
|---|---:|---|
| `radius-sm` | 4 px | badges and compact controls |
| `radius-md` | 6 px | fields, tables, cards, dialogs |
| `radius-lg` | 8 px | floating action panels or exceptional grouped surfaces |
| `radius-pill` | 999 px | status or compact identity pills only |

Desktop compact controls should normally fit a 32-36 px visual rhythm. Touch-oriented controls at tablet and mobile widths must provide at least a 44 px effective touch target even when the visible control is smaller.

## Layout breakpoints

Shared layout primitives use these reference ranges unless content testing proves a component needs an earlier transition:

- **narrow:** below 640 px;
- **medium:** 640-1023 px;
- **wide:** 1024 px and above.

The permanent labelled primary navigation is available at wide widths. At narrower widths it becomes an accessible labelled drawer; Waiotech must not collapse primary navigation into an unlabeled icon rail.

Master-detail layouts normally require wide width. Tables may retain horizontal comparison, reduce secondary columns, or become structured rows according to the task; they must not mechanically convert every table into cards at one universal breakpoint.

## Responsive behavior

The Dashboard uses responsive behavior based on content needs rather than assuming one desktop table layout everywhere.

Typical behavior:

- desktop: master-detail, full comparison tables, multi-column summaries where useful;
- tablet: reduced split layouts, touch-friendly actions, compact but complete information;
- mobile widths: focused single-column workflows, cards or structured rows where tables no longer scan reliably.

Important fields and actions must not disappear merely because width is constrained.

## Density

Operational registries default to compact density. Detail, decision, and form experiences default to comfortable density.

Density changes control spacing, row height, and section rhythm; they do not remove information or alter product behavior.

## Cards and surfaces

Use cards only when a group benefits from a distinct visual surface. Do not wrap every section and every metric in an identical card.

Avoid excessive borders, nested boxes, decorative gradients, competing colours, and passive KPI tiles.

## Status and colour

Colour reinforces meaning but never carries meaning alone.

Neutral lifecycle states remain restrained. Warning and danger colours are reserved for genuine attention. Domain status badges must not become a rainbow of equally prominent labels.

## Operational timeline

Timeline presentation uses concise business events with Actor, time, subject, and result. It may group low-value repetitive events while preserving access to complete history.

Raw audit diffs remain available where authorized but are not the default operational story.

## Accessibility and bidirectionality

All components support keyboard interaction, visible focus, semantic names, screen readers, sufficient contrast, and correct RTL/LTR behavior.

Persian and English use the same information hierarchy and workflow completeness. Codes, quantities, and technical tokens remain isolated LTR where required.

## Related documents

- [Tenant Dashboard experience](080-tenant-dashboard-experience.md)
- [Browser interaction, accessibility, and localization](../20-engineering/60-applications/070-browser-interaction-accessibility-and-localization.md)
