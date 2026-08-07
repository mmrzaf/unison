# Browser interaction, accessibility, and localization

Forms, revisions, Plan impact, Access Profile authoring, high-impact actions, lifecycle presentation, tables, long-running processes, artifacts, Notifications, uploads, localization, time, quantities, accessibility, untrusted content, storage, telemetry, and feature flags follow explicit interaction contracts.

## Keep form edits distinct from authoritative resources

Waiotech must keep form edits distinct from authoritative resources.

Forms separate server initial values, unaccepted local changes, local structural validation, server validation, submission, and accepted result. Every business field maps to a typed request contract.

## Avoid a second Domain implementation in the browser

Avoid a second Domain implementation in the browser.

It improves usability for required fields, formatting, and generated schema. Backend validation controls authority, lifecycle, policy, relationships, evidence, and calculations.

## Preserve recoverable work without preserving unsafe data

Waiotech must preserve recoverable work without preserving unsafe data.

Recoverable user input remains available after ordinary validation or transport failures except when retention creates a security risk, authorization scope changed, the session ended, the subject disappeared, or the field contains a secret that must be cleared.

## Restrict autosave to explicitly mutable unaccepted content

Restrict autosave to explicitly mutable unaccepted content.

Only for mutable working drafts and non-authoritative preferences. Autosave does not publish, approve, post, release, complete, revoke, or perform another protected transition.

## Expose stable-object workflows

Waiotech must expose stable-object workflows.

The interface uses Edit, Save changes, Unpublished changes, Review changes, Publish changes, Discard changes, Change history, and Restore content. It does not present branches, merge, manual supersession, or revision activation.

## Make publication coverage exact and understandable

Waiotech must make publication coverage exact and understandable.

The Plan editor shows published and proposed targets, additions, removals, exclusions, ineligible targets, missing trigger facts, evaluation time, and effect on occurrence recognition. Raw selector JSON is not exposed.

## Simplify authoring without hiding grants

Simplify authoring without hiding grants.

The interface provides Permission search, module grouping, selected Permissions, dependencies, template comparison, additions, removals, blockers, and impact on assigned Teams. Groups and templates grant no authority themselves.

## Use consequence-specific confirmation according to risk

Waiotech must use consequence-specific confirmation according to risk.

Confirmation names the exact action, subject, material consequence, reversibility, reason, and resulting evidence or lifecycle. Generic “Are you sure?” text is insufficient for high-impact actions.

## Do not present projections as editable lifecycle facts

Waiotech must not present projections as editable lifecycle facts.

Canonical lifecycle codes receive localized labels and accessible visual treatment. Readiness, overdue condition, available stock, target drift, and other projections are labelled as derived and include evaluation time where material.

## Keep operational queries server-owned and bounded

Waiotech must keep operational queries server-owned and bounded.

Tables use typed server filters, deterministic sorting, cursor pagination, accessible semantics, clear states, stable row identity, and bounded presentation preferences. They do not download unbounded Tenant datasets or expose arbitrary filter builders.

## Present process execution without fabricated progress

Waiotech must present process execution without fabricated progress.

The UI displays backend states such as queued, running, completed, failed, and cancelled. Measured byte or item progress may be shown when truthful. Time-based invented completion percentages are prohibited.

## Reauthorize sensitive file access

Reauthorize sensitive file access.

Every retrieval invokes backend authorization. Temporary download URLs are not persisted, shared, or logged. Generating an artifact does not grant permanent retrieval authority.

## Keep communication separate from workflow

Waiotech must keep communication separate from workflow.

Notifications show type, related subject, creation, unread/read state, safe content, navigation, and applicable product actions. Reading does not execute the related action. Channel failures remain diagnostic delivery state.

## Present exact file state

Waiotech must present exact file state.

The UI distinguishes selected locally, transferring, transferred, verifying, safety evaluation, accepted Attachment, failure, quarantine, rejection, and unavailability. Transfer progress does not prove Attachment acceptance.

## Localize presentation without changing contracts

Localize presentation without changing contracts.

Stable localization keys and canonical API codes drive labels. Locale-aware formatting applies to dates, time, decimal, quantity, and money. Translated labels never become identity.

## Keep operational time independent from device settings

Waiotech must keep operational time independent from device settings.

Tenant operational scheduling uses the Tenant timezone. Browser timezone may support personal display only where the Experience Contract permits it. Ambiguous time presentation includes the applicable timezone.

## Prevent JavaScript floating-point from changing product meaning

Waiotech must prevent JavaScript floating-point from changing product meaning.

Frontend code uses decimal-safe parsing and formatting. Authoritative calculations come from the backend. A client preview is labelled non-authoritative.

## Treat accessibility as a mandatory functional requirement

Waiotech must treat accessibility as a mandatory functional requirement.

Applications support semantic structure, keyboard operation, visible focus, labels, accessible errors, dialogs, tables, status announcements, contrast, text resizing, reduced motion, and non-color status meaning.

## Treat all external and uploaded content as hostile until verified

Waiotech must treat all external and uploaded content as hostile until verified.

Untrusted HTML is not rendered directly. Rich content requires approved sanitization, safe URLs, script removal, isolation, content security policy, and media-type controls.

## Keep credentials and private service authority outside browser code

Waiotech must keep credentials and private service authority outside browser code.

Every frontend build value is public.

## Use browser persistence only for non-authoritative presentation data

Waiotech must use browser persistence only for non-authoritative presentation data.

Persistent browser storage is limited to bounded low-risk presentation preferences. Tenant and User scope are explicit. Authentication secrets, authoritative aggregates, sensitive evidence, and unrestricted payloads are prohibited.

## The browser offline contract

Waiotech must limit browser offline behavior to safe preparation and resumable technical state.

Tenant Dashboard and Platform Admin remain online-authoritative. A browser may preserve permitted unsent draft input, presentation preferences, and resumable transfer state, but it cannot execute a Work Order, post Inventory, publish, approve, close out, or perform another protected command without backend confirmation.

Governed offline field execution belongs only to Android Work App through the Work Package contract.

## Require deliberate submission of recovered business input

Waiotech must require deliberate submission of recovered business input.

Not for protected commands without explicit user confirmation. Resumable byte transfer may continue under its specific contract.

## Minimize diagnostic data and keep product metrics server-authoritative

Minimize diagnostic data and keep product metrics server-authoritative.

Telemetry contains safe route performance, latency, error code, request and correlation identity, and client exception classification. It excludes secrets, form values, file content, unrestricted API payloads, and personal information without an explicit contract.

## Use flags as deployment controls only

Waiotech must use flags as deployment controls only.

Feature flags control technical exposure of already governed behavior. They do not create hidden product rules, weaken Permission, change lifecycle meaning, create Tenant forks, or maintain two permanent vocabularies.

## Verify every user-facing surface without relying on client-side product authority

Waiotech must verify every user-facing surface without relying on client-side product authority.

Tests cover:

- Dashboard and Platform Admin component, integration, accessibility, and protected end-to-end behavior;
- generated browser and Android clients;
- Public Website bilingual routes, metadata, structured data, sitemaps, redirects, accessibility, performance, and content parity;
- Android User and installation binding, Room migrations, Work Package verification, offline journal ordering, file capture, scanning, material scope, synchronization, conflicts, revocation, upgrade, and evidence recovery;
- Help Center search, contextual links, visibility, bilingual parity, mission definitions, private test-Tenant isolation, side-effect fences, progress separation, and deterministic reset;
- separation of public, Tenant, platform, field, and learning responsibilities.

## Related documents
- [Browser application contracts](060-browser-application-contracts.md)
- [Tenant Dashboard and Platform Admin](../../30-experience/030-tenant-dashboard-and-platform-admin.md)
- [Testing and software release](../80-delivery/050-testing-and-release.md)

## Related experience

- [Tenant Dashboard experience](../../30-experience/080-tenant-dashboard-experience.md)
- [Dashboard design conventions](../../30-experience/090-dashboard-design-conventions.md)
