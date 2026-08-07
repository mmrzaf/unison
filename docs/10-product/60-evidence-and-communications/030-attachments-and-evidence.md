# Attachments and evidence

An Attachment is an immutable accepted file identity linked to governed subjects through explicit roles. Access, integrity, quarantine, correction, unavailability, and preservation remain explicit.

## An Attachment

Waiotech must keep file identity separate from domain meaning.

An Attachment is the stable identity of one accepted file.

It owns file-level facts such as:

- Tenant;
- filename;
- media type;
- size;
- integrity identifier;
- uploader;
- upload time;
- classification;
- integrity and quarantine status;
- storage availability.

Attachment does not define why the file matters to the business.

## Maintenance-specific upload and linking

Maintenance and Process upload are governed workflows. Supported evidence subjects include Work Requests, Work Orders, Tasks and completion evidence, Findings, Process Conditions, Operational Observations, Operational Actions, Failure Events, Procedure Revisions, and Android offline evidence.

```text
local file
-> Upload Session
-> private storage
-> integrity verification
-> safety and quarantine evaluation
-> accepted Attachment
-> module-owned Attachment Link
-> audited result
```

A local or uploaded file is not presented as accepted evidence before Attachment
acceptance and successful domain-specific linking. The link command verifies the
selected Tenant, subject authority, allowed role, classification, sensitivity,
file availability, duplicate link, and action-specific evidence requirement in
one transaction. Quarantined, rejected, expired, or unavailable files cannot
satisfy a Requirement.

## An Attachment Link

Let the owning domain link define business meaning.

An Attachment Link connects an Attachment to a governed product subject and defines the file’s business role.

Possible roles include:

- completion evidence;
- inspection evidence;
- Finding evidence;
- Procedure instruction;
- Decision evidence;
- Report Artifact;
- reference document;
- supporting photograph.

The link preserves:

- subject;
- role;
- added by;
- added at;
- description;
- governing status;
- correction or supersession relationship where applicable.

## Require explicit governed evidence linkage

Waiotech must require explicit governed evidence linkage.

A file satisfies an evidence Requirement only when:

- it is linked through the correct governed evidence role;
- the relevant Requirement accepts that evidence type;
- the file is available;
- integrity is established;
- it is not blocked by quarantine;
- required metadata is present.

Uploading a file somewhere on a Work Order does not automatically satisfy a Requirement.

## The governing subject of an Attachment

Waiotech must use one governing subject to prevent access bypass through weaker links.

Every Attachment must have one governing subject that determines:

- minimum access;
- business classification;
- sensitivity;
- primary evidence meaning;
- retention dependency under a Product Authority amendment defining retention.

Additional links may reference the file for navigation or context, but they cannot weaken the governing subject’s access controls.

## Permit controlled reuse without access inheritance from weaker links

Permit controlled reuse without access inheritance from weaker links.

An Attachment may support several related subjects where that reuse is legitimate.

However:

- its governing subject remains authoritative for minimum access;
- additional links cannot broaden access;
- each link must state its own business role;
- sensitive evidence must not become accessible through a less restricted subject.

## Recheck governing-subject and file-level authority on every retrieval

Recheck governing-subject and file-level authority on every retrieval.

Access requires:

- access to the governing subject at retrieval;
- applicable Attachment or evidence Permission where required;
- compliance with sensitivity classification;
- acceptable integrity status;
- acceptable quarantine status;
- Tenant context at retrieval.

Access through an additional link must never bypass these checks.

## Correct evidence by addition and supersession, never in-place replacement

Correct evidence by addition and supersession, never in-place replacement.

A file accepted under one Attachment identity must remain historically stable.

Correction creates:

- a new Attachment;
- a new Attachment Link;
- correction or supersession relationship;
- correction reason;
- correcting Actor;
- correction time.

The original file and its historical role remain visible.

## Prevent metadata edits from rewriting evidence meaning

Waiotech must prevent metadata edits from rewriting evidence meaning.

Material metadata corrections must preserve history.

Minor display corrections may be allowed where they do not change business meaning.

Changes to any of the following require an explicit correction action or new link rather than silent editing:

- evidence role;
- governing subject;
- sensitivity;
- uploader attribution;
- accepted file identity;
- integrity facts.

## File integrity

Waiotech must make verifiable file identity mandatory for governed evidence.

File integrity means Waiotech can establish whether the retrieved file is the same file accepted under that Attachment identity.

Product Authority requires reliable integrity verification.

Engineering Authority defines the storage, hashing, and verification mechanisms.

When integrity cannot be established, the Attachment cannot satisfy governed evidence Requirements.

## Quarantine

Waiotech must treat quarantine as restricted file usability, not silent deletion.

Quarantine means the Attachment is unavailable for normal use because its safety, integrity, or content status is unresolved or unacceptable.

Possible reasons include:

- suspected malware;
- integrity mismatch;
- unsafe content;
- failed security review;
- unsupported or unresolved content processing.

A quarantined file:

- cannot be normally viewed or downloaded;
- cannot satisfy an evidence Requirement;
- remains visible as a restricted historical reference;
- may be released after governed review or permanently rejected through a governed review.

## Separate file usability from evaluation of the underlying business claim

Waiotech must separate file usability from evaluation of the underlying business claim.

Quarantine means the file cannot be trusted at evaluation time or safely used.

It does not by itself prove that the related observation, action, or business claim is false.

The owning domain may require replacement evidence, review, or another corrective action.

## Support explicit quarantine review and release

Waiotech must support explicit quarantine review and release when an authorized review establishes that the file is safe, intact, and acceptable.

Release from quarantine must preserve:

- reviewing Actor;
- review time;
- evidence;
- resulting status.

A file that remains unsafe or unverifiable may be permanently rejected from normal use while its historical link remains visible.

## Preserve file identity and evidence history even when binary content is unavailable

Waiotech must preserve file identity and evidence history even when binary content is unavailable.

When a file cannot be retrieved because of technical loss or corruption, the Attachment record and domain links must remain visible.

The product should clearly distinguish:

- available;
- quarantined;
- unavailable;
- rejected from normal use.

Unavailability must not be presented as though the Attachment never existed.

## Configurable retention and physical destruction are outside this Product Authority and require a complete Product Authority contract

Configurable retention and physical destruction are outside this Product Authority and require a complete Product Authority contract.

Retention and physical destruction require a dedicated contract covering:

- evidence classes;
- legal or contractual holds;
- privacy;
- deletion;
- anonymization;
- backups;
- integration copies;
- destruction authority;
- historical proof.

A simple Tenant-configurable retention period would be incomplete and unsafe.

## Preserve governed evidence by default

Waiotech must preserve governed evidence by default. Any destruction or configurable retention requires a complete Product Authority contract.

Attachments and governed evidence remain preserved while required by their owning records and mandatory product-integrity rules.

Ordinary users cannot physically destroy governed evidence.

A file may become quarantined or technically unavailable, but its Attachment identity, governing link, and historical meaning remain preserved.

## Allow report outputs to support evidence without changing source authority

Waiotech must allow report outputs to support evidence without changing source authority.

A completed Report Artifact may be linked as evidence to a Decision, investigation, Work Order, or other governed subject.

The link must preserve:

- Report Run;
- Report Type version;
- parameters;
- as-of semantics;
- artifact identity;
- evidence role.

The artifact remains derived evidence and does not replace its underlying source records.

## The minimum Report, Notification, and Attachment model

Waiotech must keep reporting, communication, and file evidence governed without introducing unrestricted querying, configurable event rules, access bypass, or premature retention behavior.

The minimum model is:

```text
Report Type
└── Saved Report Configuration
    └── Report Run
        └── Report Artifact

Notification Type
├── Notification Preference
└── Notification
    └── normal authorized domain action

Attachment
└── Attachment Link
    ├── governing subject
    ├── evidence
    ├── instruction
    ├── Report Artifact
    └── reference
```

The governing distinctions are:

- Report Type defines a product-governed analytical view.
- Saved Report Configuration selects supported options.
- Report Run creates one frozen derived result.
- Report Artifact is not an authoritative source record.
- Notification communicates a product event but does not own its workflow.
- Attachment owns file identity and integrity.
- Attachment Link defines business meaning.
- One governing subject controls minimum access.
- Evidence correction adds new records and preserves originals.
- Configurable retention and physical destruction are outside Waiotech Product Authority.

## Related documents
- [Reports](010-reports.md)
- [Tenant portability](../70-tenant-lifecycle/020-tenant-portability.md)
- [File ingestion and storage](../../20-engineering/70-files/010-file-ingestion-and-storage.md)
