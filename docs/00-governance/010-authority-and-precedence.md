# Authority and precedence

Product Authority defines Waiotech’s business truth. Engineering Authority defines the technical guarantees required to preserve it.

## Authority scopes

Product Authority owns:

- product concepts and canonical terminology;
- authoritative facts and relationships;
- lifecycles, actions, guards, and outcomes;
- responsibility, authority meaning, and separation of duties;
- required evidence and historical interpretation;
- cross-domain behavior and product boundaries;
- observable product obligations.

Engineering Authority owns:

- architecture and module boundaries;
- persistence, transactions, consistency, and recovery;
- commands, APIs, events, background work, and integrations;
- authentication, authorization enforcement, and operational security;
- application, file, deployment, observability, and testing standards.

Product documents do not prescribe code structure, HTTP shapes, database design, repository layout, or framework conventions. Engineering documents do not redefine product meaning or weaken product guarantees.

## Concern ownership

Each material concern has one authoritative owner. Other documents may summarize the concern or coordinate its actions, but they must link to the owner instead of creating a competing definition.

Domain documents own their entities, facts, lifecycles, actions, guards, responsibility, Decisions, evidence, exceptions, and end-of-life behavior. Cross-domain workflows coordinate domain-owned actions without redefining them. Experience contracts define observable interaction behavior without granting authority absent from Product Authority.

## Documentation classes

A document’s numbered path establishes its class:

- `00-governance/` — authority and documentation rules;
- `10-product/` — Product Authority;
- `20-engineering/` — Engineering Authority;
- `30-experience/` — Experience Contracts;
- `70-reference/` — consolidated references;
- `90-generated/` — generated output.

Documents do not carry classification banners, document IDs, owner labels, or status panels. Numbering exists for stable ordering and navigation, not for display inside the content.

## Precedence

Conflicts are resolved in this order:

1. Product Authority;
2. Engineering Authority;
3. accepted bounded architecture decisions within delegated scope;
4. Experience Contracts and operational standards within their delegated scope;
5. generated references derived from authoritative sources;
6. implementation;
7. tests and operational observations;
8. superseded or legacy guidance.

A lower-order source cannot weaken, broaden, or reinterpret a higher-order source. Existing implementation behavior does not become authoritative because it is old, widely used, or difficult to change.

## Product meaning changes

A change to a product concept, ownership rule, lifecycle, action, evidence requirement, authority meaning, catalogue meaning, cross-domain consequence, Tenant rule, or user-facing obligation requires an explicit Product Authority change.

Engineering may identify ambiguity, contradiction, infeasibility, or missing meaning. It must not resolve those issues through schema shape, feature flags, framework behavior, configuration, compatibility aliases, or undocumented conventions.

## Normative language

`Must` and `must not` state mandatory requirements and prohibitions. `May` states a permitted option. `Should` states the canonical rule where a mandatory form would be unnecessarily rigid; departure requires an explicit bounded decision or authority change.

Examples never override a rule. A list or example is either a required canonical minimum, a governed extension point, or non-normative illustration. Its meaning must be clear from the surrounding text.

## Timeless authority

Authoritative documents state enduring contracts. They do not contain delivery status, implementation percentages, roadmap phases, release dates, task ownership, remediation tracking, or migration progress.

Runtime time semantics remain explicit through terms such as effective time, recorded time, occurrence time, cutoff, and evaluation time.

## Conformance

A conforming implementation preserves Product Authority, Engineering Authority, accepted bounded technical decisions, generated-contract parity, and mandatory verification. Conformance is determined from observable guarantees, not directory names, framework labels, or implementation intent.

Implementation cannot override authority. A known dependency on non-conforming behavior is a defect to correct, not a reason to preserve competing product meaning.

## Related documents

- [Documentation rules](020-documentation-rules.md)
- [Repository and source ownership](../20-engineering/10-foundations/010-repository-and-source-ownership.md)
- [Canonical terminology](../70-reference/010-canonical-terminology.md)
