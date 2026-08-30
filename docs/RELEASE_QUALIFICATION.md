# Release qualification

Unison uses the same production verification, signing, packaging, and publication path for beta, RC,
and stable releases. Prereleases do **not** receive a reduced automated test gate: they are how the
actual release path is exercised before the stable version is declared.

The goal is strict automation with minimal human ceremony.

## Automated release gate

Every tag-triggered beta, RC, and stable release must pass the same automated gate:

- repository/static/data/tooling checks;
- dependency-verification checks;
- focused hardening, protocol, playback, diagnostics, lifecycle, and network tests;
- formatting checks and JVM unit tests;
- debug and release lint/build checks;
- Android instrumentation on API 30, 33, and 36;
- immutable tag/version/commit validation;
- signed release APK build;
- APK signing and zipalign verification;
- deterministic source packaging;
- SHA-256 checksum and release metadata generation;
- GitHub/Sigstore provenance attestation;
- immutable GitHub Release publication.

A failure in any required automated step blocks publication.

GitHub normal CI also runs Android instrumentation on API 33. The release workflow intentionally
re-runs the full release matrix on API 30/33/36 so prereleases continuously test the production path.

## Release evidence

Keep one concise record for every public version under [`release-evidence/`](release-evidence/README.md).
The record is a human qualification note, not a duplicate of machine-generated evidence.

Do not manually copy information that already exists authoritatively in the release assets or GitHub
run unless it is useful for investigation. In particular:

- `SHA256SUMS.txt` is authoritative for published artifact hashes;
- `release-info.txt` is authoritative for commit/signing metadata emitted by the release build;
- the GitHub Actions run is authoritative for automated gate results;
- GitHub attestation is authoritative for build provenance.

The per-version evidence file should retain the information automation cannot replace cleanly:

- release/tag identity;
- GitHub Release or Actions run reference;
- exact published APK physical-device smoke result;
- devices/networks used for human qualification;
- notable soak/stress results when performed;
- known issues accepted for the release;
- reviewer/date and final human disposition.

The evidence file may start as a small pre-tag stub and be completed after the immutable GitHub artifact
exists. Never claim a human test was performed on a local/debug/different build when the record says it
covered the published APK.

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

- wire protocol remains Protocol 2;
- Room database schema remains schema 1;
- GitHub Actions remain pinned to reviewed full commit SHAs;
- dependency-verification changes are reviewed rather than blindly accepted;
- release assets are immutable and are never replaced in place.

The focused hardening suite in `scripts/check-release-quality.sh` remains part of every release gate,
including SRP conformance, lifecycle seam regressions, session-generation fences, endpoint authority,
and control-priority/no-starvation coverage.

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

That is the release ceremony. Automated test coverage should stay strict; duplicated bookkeeping should
not grow around it.
