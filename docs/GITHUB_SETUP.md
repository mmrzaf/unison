# GitHub repository setup

These settings cannot be enforced solely by files in the source tree. Apply them before actively
promoting the public beta and review them whenever repository ownership changes.

## Repository metadata

Suggested description:

> Local-first Android synchronized music playback across nearby phones. No account, cloud backend, or Internet connection required.

Suggested topics:

- `android`
- `kotlin`
- `jetpack-compose`
- `media3`
- `local-first`
- `offline-first`
- `peer-to-peer`
- `music`
- `wifi`
- `synchronization`

Add a genuine social-preview image and real product screenshots/demo media before broad promotion.
Do not use mockups that imply behavior not demonstrated by the current beta.

## Discussions and issue flow

Enable GitHub Discussions. Useful categories:

- Announcements
- Help / Q&A
- Ideas
- Show and tell
- Development

Issues are for reproducible bugs and accepted scoped work; open-ended help/ideas belong in Discussions.

## Security features

Enable where available:

- private vulnerability reporting;
- Dependabot alerts and security updates;
- secret scanning/push protection;
- CodeQL/default security reporting.

The repository contains `.github/SECURITY.md`, CodeQL, and Dependabot configuration, but GitHub account
features still need to be enabled in repository settings.

## Branch and tag protection

`develop` remains the normal integration/development branch. For external pull requests, require the
Android CI checks before merge and disable force pushes/deletion on the protected integration branch.
A solo maintainer does not need an artificial approval requirement for their own work.

Create a ruleset for release tags matching `v*` that prevents force updates and deletion. Tags identify
immutable source/artifact releases; a broken beta gets a new tag rather than replaced assets.

## Labels

Create at least:

- `bug`
- `enhancement`
- `documentation`
- `good first issue`
- `help wanted`
- `playback`
- `networking`
- `transfer`
- `storage`
- `android`
- `security`
- `testing`
- `dependencies`
- `ci`

Prepare a small number of genuine, bounded `good first issue` tasks before actively recruiting
contributors. Good candidates are documentation/test/tooling improvements that do not require changing
the canonical room protocol or actor lifecycle on a first contribution.

## Release policy

- release publication is tag-triggered only;
- `-beta.*` and `-rc.*` versions are prereleases;
- stable tags are normal/latest releases;
- release assets for an existing tag are not replaced;
- debug APKs remain CI artifacts rather than public release downloads;
- signing and publishing permissions remain separated in CI;
- public release subjects receive GitHub/Sigstore build-provenance attestations.

## Promotion checklist

Before sharing the beta broadly:

- README download/status text matches the actual public release;
- the exact GitHub-produced APK has been installed and smoke-tested;
- beta release evidence is updated from real results;
- known issues are explicit and current;
- screenshots/demo media show real devices/current UI;
- Discussions and issue/security paths are active.
