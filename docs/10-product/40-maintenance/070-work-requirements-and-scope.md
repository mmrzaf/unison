# Work Requirements and scope control

Work Requirements express governed conditions that must be satisfied, waived where permitted, or remain visible as blockers. Material scope changes are explicit and may invalidate preparation, release, or issued field authority.

## A Work Requirement

Waiotech must use explicit Work Requirements for action-blocking obligations without creating a generic policy engine.

A Work Requirement is a Work Order-owned obligation that must be satisfied, waived where explicitly allowed, or declared not applicable before a named Work Order action.

A Work Requirement records:

- governed requirement type;
- description;
- source;
- action or actions it blocks;
- responsible party where known;
- satisfaction evidence;
- validity or expiry where applicable;
- whether waiver is permitted;
- applicable waiver Decision.

## Keep Requirement sources closed and governed

Waiotech must keep Requirement sources closed and governed.

Work Requirements may come from:

- an applied Procedure Revision;
- a Maintenance Plan Revision;
- an authorized planner;
- an explicit product rule;
- a governed Decision or variation.

IAM, Asset classification, or other modules must not inject arbitrary requirements through generic references.

Additional sources should be added only through explicit product contracts.

## Use a small closed catalogue and expand it only for distinct blocking semantics

Waiotech must use a small closed catalogue and expand it only for distinct blocking semantics.

The governed catalogue includes:

- information;
- Task definition;
- execution responsibility;
- material;
- tool or service;
- Procedure or document;
- permit;
- isolation;
- access;
- safety control;
- execution window;
- approval;
- completion evidence;
- verification;
- follow-up.

Detailed module-specific meaning may exist beneath these types without creating a new universal requirement type for every case.

## Use explicit requirement outcomes with preserved evidence

Waiotech must use explicit requirement outcomes with preserved evidence.

A Work Requirement may be:

- unsatisfied;
- satisfied;
- waived;
- not applicable;
- expired.

Satisfaction, waiver, and not-applicable outcomes preserve:

- Actor;
- time;
- evidence;
- reason;
- validity period where relevant.

Expired satisfaction causes the Requirement to become an active blocker again when applicable.

## Make waiver explicit, exceptional, and controlled per Requirement

Waiotech must make waiver explicit, exceptional, and controlled per Requirement.

Waiver is available only when the Requirement explicitly permits it.

A waiver requires:

- authorized Decision;
- reason;
- deciding Actor;
- decision time;
- temporary controls where required;
- effective period or expiry where applicable.

Mandatory product, safety, permit, or isolation Requirements cannot be waived merely because an Actor has broad administrative authority.

## Combine Requirements conservatively and prohibit silent weakening

Combine Requirements conservatively and prohibit silent weakening.

Requirements are additive unless an explicit governed rule states that one replaces or supersedes another.

A Work Order may add stricter Requirements.

It must not silently remove mandatory Requirements originating from:

- the Procedure Revision;
- the Maintenance Plan Revision;
- product rules;
- approved Decisions.

Conflicting Requirements must be resolved explicitly before the blocked action becomes ready.

## Preserve material variation explicitly rather than rewriting the original scope

Waiotech must preserve material variation explicitly rather than rewriting the original scope.

A scope variation records a material change discovered or required after release or execution start.

Minor variation may be allowed within explicitly defined tolerance.

A material variation requires:

- effective work to pause where necessary;
- preservation of safe condition;
- description of the original and revised scope;
- reason;
- reassessment of risk and Requirements;
- authorization;
- renewed release where applicable;
- linked Work Order when the new objective is independently manageable.

## Establish non-negotiable variation boundaries

Establish non-negotiable variation boundaries.

The following are always material:

- primary Work Target;
- core objective;
- maintenance class where the meaning of work changes;
- mandatory Procedure content;
- execution-risk classification;
- permit or isolation boundary;
- shutdown boundary;
- acceptance criteria;
- required authority;
- separation-of-duty requirements.

The primary target cannot change after execution begins.

## Related documents
- [Work Order lifecycle and Readiness](060-work-order-lifecycle-and-readiness.md)
- [Completion, verification, and closeout](080-completion-verification-and-closeout.md)
- [Decisions, waivers, and separation of duties](../60-policy-and-decisions/020-decisions-waivers-and-separation-of-duties.md)
