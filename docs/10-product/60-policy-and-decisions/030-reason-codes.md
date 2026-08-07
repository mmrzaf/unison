# Reason codes

Reason codes provide governed, stable meaning for actions that require structured explanation. Free text may add context but cannot replace a required canonical reason.

## Reason codes

Waiotech must use reason codes where recurring reasons affect reporting, control, or interpretation.

Reason codes are governed catalogue values that provide consistent meaning for recurring operational actions and Decisions.

Examples include:

- Work Order cancellation reasons;
- Work Request rejection reasons;
- hold reasons;
- Inventory adjustment reasons;
- release-withdrawal reasons;
- waiver reasons;
- skipped Scheduled Work reasons.

## Keep reason catalogues domain-specific

Waiotech must keep reason catalogues domain-specific.

Each action family should own its reason-code catalogue because cancellation, stock adjustment, rejection, and waiver have different meanings.

## Use controlled meaning with optional explanatory narrative

Waiotech must use controlled meaning with optional explanatory narrative.

Where a governed reason code is required, an Actor must select one.

Free text may supplement the controlled meaning.

An `other` reason should require an explanation.

## Preserve reason-code meaning over time

Waiotech must preserve reason-code meaning over time.

Reason-code identity and meaning must remain stable after historical use.

A code may be retired from new selection while remaining understandable in historical records.

A replacement code must use a new identity.

## The minimum policy and Decision model

Waiotech must keep policy concrete, Tenant-scoped, revisioned, and product-defined without introducing a generic rule or workflow engine.

The minimum model is:

```text
Mandatory Product Rules
└── non-configurable product guarantees

Tenant Control Policy
└── Tenant Control Policy Revision
    └── product-defined typed controls

Work Requirement
├── unsatisfied
├── satisfied
├── waived
├── not applicable
└── expired

Domain-owned Decision
├── subject
├── governed outcome
├── Actor and authority
├── rationale and evidence
├── applicable policy revision
├── effective period
└── supersession or revocation where applicable

Domain-specific Reason Codes
```

The governing distinctions are:

- Product rules define non-negotiable behavior.
- Tenant policy configures only product-defined controls.
- Work Requirements express action-blocking obligations.
- Decisions preserve authorized judgment.
- Waivers are narrow Decisions against explicitly waivable Requirements.
- Exceptions remain named domain actions.
- Reason codes provide controlled operational meaning.

## Related documents
- [Decisions, waivers, and separation of duties](020-decisions-waivers-and-separation-of-duties.md)
- [Canonical terminology](../../70-reference/010-canonical-terminology.md)
