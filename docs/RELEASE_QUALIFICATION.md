# Release qualification

Unison uses the same production verification, signing, packaging, and publication path for beta, RC,
and stable releases. Prereleases do **not** receive a reduced automated test gate: they exercise the
same release path that will later produce the stable build.

The goal is strict automation with minimal human ceremony.

## Automated release gate

Every tag-triggered beta, RC, and stable release must pass:

- repository static/data/tooling checks, dependency verification, diagnostic analyzers, the 100,000-track
  library benchmark, and the network-lifecycle seam harness;
- Gradle formatting, JVM unit tests, debug/release lint, debug/release APK compilation, and Android-test
  compilation against the real dependencies;
- Android instrumentation on API 30, 33, and 36;
- immutable tag/version/commit validation;
- pre-build keystore certificate verification against the pinned release SHA-256 identity;
- signed release APK build followed by signer identity, signature, package/version/SDK, non-debuggable,
  APK-size, and zip-alignment verification;
- deterministic source packaging plus SHA-256 checksums and release metadata;
- GitHub/Sigstore provenance attestation;
- immutable GitHub Release publication.

A failure in any required automated step blocks publication.

Normal GitHub CI also runs Android instrumentation on API 33. The release workflow intentionally
re-runs the complete API 30/33/36 matrix so prereleases continuously qualify the production path.

## Release evidence

Keep one concise record for every public version under [`release-evidence/`](release-evidence/README.md).
The record is a human qualification note, not a second CI log.

Machine-generated evidence remains authoritative in its original location:

- automated tests and gates → GitHub Actions run;
- artifact hashes → `SHA256SUMS.txt`;
- commit/signing details → `release-info.txt`;
- build provenance → GitHub attestation.

The per-version evidence file should retain what automation cannot replace cleanly:

- release/tag identity and GitHub Release/Actions reference;
- exact published APK physical-device smoke result;
- devices/networks used for human qualification;
- notable soak/stress results when performed;
- known issues accepted for the release;
- reviewer/date and final human disposition.

A pre-tag evidence file may remain **IN PROGRESS** while the candidate is being qualified. Once a
candidate is abandoned or superseded, either finalize it with the real disposition or delete an
unpublished empty stub; do not leave historical evidence permanently looking like an active release.
Never claim an exact-artifact test using a debug, local, or different release build.

## Physical-device qualification

The detailed scenario catalog lives in
[`PHYSICAL_DEVICE_QUALIFICATION.md`](PHYSICAL_DEVICE_QUALIFICATION.md). It remains the reference for
manual device testing and investigation.

For a beta or RC, record the physical scenarios actually exercised and any known limitations. For the
stable release, complete the project-selected stable device/soak qualification before treating the line
as fully qualified.

Human physical testing complements the automated gate; it does not replace or weaken it.

## Security and compatibility invariants

A release is blocked by a known issue that can cause security compromise, media corruption, invalid
canonical-state mutation, unbounded actor/lifecycle failure, or a violation of the 1.2 protocol/storage
contract.

For the 1.2 line:

- wire protocol remains Protocol 1;
- Room database schema remains schema 1 and local-data format remains v1;
- Beta 10 qualification starts from a clean install/app-data state; earlier development-build data is unsupported.
- Beta 10 is the first supported local-data v1 baseline; Beta 10-and-later data must survive later 1.2
  candidate upgrades unchanged;
- local transfer policy/access denial must be bounded and visible rather than entering an automatic
  retry storm;
- a blocked current/pending successor must not remain presented as `Preparing` after preparation has
  reached a terminal or circuit-suspended state;
- GitHub Actions remain pinned to reviewed full commit SHAs;
- the release signing certificate must match the independently pinned SHA-256 identity before and after
  APK signing;
- dependency-verification changes are reviewed rather than blindly accepted;
- release assets are immutable and are never replaced in place.

The complete Gradle unit suite remains part of every release gate, including SRP conformance,
lifecycle seam regressions, session-generation fences, endpoint authority, and
control-priority/no-starvation coverage. Repository-specific scripts supplement that suite only where
they provide a distinct analyzer, benchmark, packaging/security invariant, or network-lifecycle seam.

## Normal release flow

1. Update `appVersionName`/`appVersionCode` in `gradle/libs.versions.toml`.
2. Add the version section to `CHANGELOG.md`.
3. Add `docs/release-evidence/<version>.md` from the concise template.
4. Run local verification.
5. Commit the release source.
6. Create and push the matching immutable `v<version>` tag.
7. Let GitHub Actions run the full release gate and publish the immutable release.
8. Install/smoke-test the exact published APK and update the evidence record with the human result.
9. If the published candidate has a blocker, do not replace it; fix forward with a new beta/RC/patch.

That is the release ceremony. Automated coverage should stay strict; duplicated bookkeeping should not
grow around it.
