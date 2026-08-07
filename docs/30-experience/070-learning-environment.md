# Learning environment

Guided missions run inside each learner’s private learning-purpose Tenant using synthetic identities and data. Learning state is isolated from operations, may be reset, and never grants operational authority or converts into operational evidence.

## A guided mission

Waiotech must represent onboarding as governed task-based learning.

A guided mission defines:

- learning objective;
- required starting data;
- ordered or conditional learning steps;
- relevant product surface;
- contextual instructions;
- completion evidence;
- reset behavior;
- language content.

A mission guides the User but must not bypass product rules.

## Give each learner an isolated practice environment

Give each learner an isolated practice environment.

Guided missions run in a private test Tenant containing safe synthetic data.

The test Tenant is assigned to the learner and is not shared with unrelated Users.

## Use one reusable private learning environment per User

Waiotech must use one reusable private learning environment per User.

A User has at most one active private test Tenant in one Waiotech product environment.

The Tenant may be reset for another mission while onboarding progress for completed or paused missions remains separate. A mission switch that replaces synthetic state requires explicit confirmation.

## A synthetic learning User

Waiotech must support realistic multi-Actor training without creating operational identities.

A synthetic learning User is a non-operational identity used only inside learning-purpose Tenants to demonstrate collaboration, assignment, approval, or separation of duties.

A synthetic learning User:

- cannot authenticate independently;
- cannot have Membership in an operational Tenant;
- cannot receive real communication;
- cannot own operational credentials;
- cannot be converted into a real User;
- is visibly identified as synthetic;
- may act only through a guided mission's explicit learning simulation contract.

The learner's own authenticated User remains the Actor for actions the learner performs.

## Use real product rules without treating synthetic practice as operational truth

Waiotech must use real product rules without treating synthetic practice as operational truth.

It is a Tenant with `learning` purpose.

It uses canonical product behavior for practice, but its records are explicitly synthetic and have no operational, financial, compliance, Process-history, maintenance-history, Reliability-history, Inventory-custody, or evidential effect outside the learning environment.

## Preserve realistic in-mission behavior while allowing deterministic disposal of synthetic state

Waiotech must preserve realistic in-mission behavior while allowing deterministic disposal of synthetic state.

Within a mission, lifecycle, immutability, authority, and evidence rules behave as they do in the product so the User learns the correct workflow. The complete synthetic Tenant state remains disposable under the explicit reset contract.

Onboarding progress is a separate durable User learning record.

## Prohibit training-data promotion into operational authority

Waiotech must prohibit training-data promotion into operational authority.

Synthetic records, identifiers, Attachments, Decisions, audit, revisions, Inventory facts, and artifacts cannot be promoted into operational use.

## Confine destructive reset authority to learning-purpose synthetic state

Confine destructive reset authority to learning-purpose synthetic state.

Reset is an explicit product action over a learning-purpose Tenant whose complete business state is synthetic and disposable. It does not authorize destruction, rollback, or replacement of operational Tenant evidence.

## Teach the real product rather than a simplified imitation

Teach the real product rather than a simplified imitation.

The test Tenant uses the same product concepts, Domain rules, Application actions, lifecycle, Permission evaluation, Readiness, validation, revision behavior, APIs, and user-interface behavior as an operational Tenant.

Simulation is limited to external effects and synthetic data.

## Prevent training activity from affecting operational systems or people

Waiotech must prevent training activity from affecting operational systems or people.

The test Tenant is isolated from:

- operational Tenant data;
- unrelated Users;
- real communications;
- operational integrations;
- billing;
- externally visible files;
- operational reports and search;
- platform support activity unrelated to the mission.

## Use safe, recognizable synthetic training data

Waiotech must use safe, recognizable synthetic training data.

It contains clearly identified synthetic Users, Teams, Functional Locations, Assets, Process Units, Measurement Points, Process Conditions, Items, Work Orders, Maintenance Plans, Findings, Failure Events, Attachments, report inputs, and other mission data.

Synthetic records must not impersonate real organizations, people, credentials, or evidence.

## Keep guided missions free from real external consequences

Waiotech must keep guided missions free from real external consequences.

Email, push, export-delivery, and other external effects are blocked or simulated inside the test environment.

## Separate learning progress from simulated product state

Waiotech must separate learning progress from simulated product state.

Onboarding progress is a separate learner record containing mission identity, resume position, completed learning steps, completion, and permitted assessment result.

Test-Tenant product records remain separate simulated business data.

## Support resumable learning without conflating progress and product records

Waiotech must support resumable learning without conflating progress and product records.

The User may exit and resume from preserved onboarding progress. The mission must verify that its test-Tenant state remains compatible with the recorded learning step.

## Support repeatable practice

Waiotech must support repeatable practice.

A mission may be repeated without removing permanent documentation access or changing operational Tenant data.

## Make test-environment reset deterministic and isolated

Waiotech must make test-environment reset deterministic and isolated.

Reset restores the mission's declared synthetic starting state.

It clears or replaces unfinished synthetic business activity, cancels synthetic processes, and removes generated test files and artifacts according to the mission definition.

Reset does not alter operational Tenants.

## Make learning reset an explicit attributable action controlled by the learner or governed support

Waiotech must make learning reset an explicit attributable action controlled by the learner or governed support.

The learner may reset the private test Tenant through the guided-mission interface. A governed support action may reset it when the learner requests assistance or the environment is unusable.

Reset requires explicit confirmation when unsaved mission work will be removed. It preserves a reset record and does not imply onboarding-progress reset.

## Preserve learning progress independently from test data reset

Waiotech must preserve learning progress independently from test data reset.

A test-Tenant reset does not reset onboarding progress unless the User explicitly chooses a separate onboarding-progress reset.

## Keep learning completion separate from IAM and workforce capability

Waiotech must keep learning completion separate from IAM and workforce capability.

Mission completion does not:

- grant Permission;
- create Team Membership;
- assign an Access Profile;
- prove Qualification;
- replace required training, approval, or verification;
- authorize operational work.

## Related documents
- [Tenant and operational scope](../10-product/10-foundations/010-tenant-and-operational-scope.md)
- [Documentation and Help Center](060-documentation-and-help.md)
- [Documentation, help, and learning engineering](../20-engineering/60-applications/050-documentation-help-and-learning.md)
