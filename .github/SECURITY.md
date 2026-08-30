# Security policy

Unison is a local-first Android application that handles peer authentication, encrypted control/file
traffic, local media files, and release signing. Please report suspected security vulnerabilities
privately rather than opening a public issue with exploit details.

## Supported versions

Security fixes are focused on the current public 1.2 prerelease/stable line and the most recent stable
release when they differ materially. Older development snapshots are not separately supported.

## Reporting a vulnerability

Prefer GitHub's **Private vulnerability reporting** flow from the repository **Security** tab when it
is enabled. Include:

- affected Unison version/tag and Android version;
- whether the issue requires an already-admitted room participant;
- concise reproduction steps or proof of concept;
- expected impact and any relevant logs with secrets removed;
- whether you believe active exploitation is occurring.

Do not include room PINs, room secrets, transfer tokens, signing material, private keys, or unrelated
personal data.

If private vulnerability reporting is unavailable, contact the repository maintainer privately through
the GitHub account before sending sensitive technical details. Do not use a public Issue or Discussion
for an undisclosed vulnerability.

## What to expect

Maintainers will first acknowledge and reproduce/triage the report, then coordinate remediation and
public disclosure appropriate to the impact. Please allow time for affected release artifacts and
upgrade guidance to be prepared before public disclosure when a vulnerability is confirmed.

## Technical security model

The product threat model, protocol/storage controls, accepted limitations, and the 1.2 SRP review are
documented in [`docs/SECURITY.md`](../docs/SECURITY.md).
