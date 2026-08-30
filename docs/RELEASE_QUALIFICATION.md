# Release qualification

Unison uses the same production build/signing path for prereleases and stable releases. A beta is not
an excuse to skip correctness/security gates; it is a way to expose a production-style candidate to
more real devices before declaring the release line stable.

Qualification belongs to an exact source commit/tag and exact APK checksum. Procedures alone are not
release evidence: record actual results under [`release-evidence/`](release-evidence/README.md).

## Release classes

### Public beta / RC gate

A public prerelease requires:

- clean repository checks, unit tests, lint, debug/release assembly;
- real Android instrumentation on API 30, 33, and 36;
- no known security, corruption, canonical-state-authority, or actor-liveness blocker;
- basic multi-device physical qualification on at least three phones/two manufacturers;
- successful install/smoke of the exact GitHub-produced signed APK;
- explicit known-issues review and release-evidence record.

Device/OEM-specific non-critical issues may remain if documented honestly in prerelease notes.

### Stable gate

Stable `1.2.0` requires all prerelease gates plus the complete physical-device matrix, soak/stress
qualification, retained diagnostic evidence, and no unresolved high-priority beta regression.

## Repository and Android gate

On a fully bootstrapped release machine:

```bash
./scripts/verify-offline-ready.sh
./scripts/check-release-quality.sh
./gradlew --offline --no-daemon --stacktrace \
  clean spotlessCheck testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease \
  :app:compileDebugAndroidTestKotlin
```

Then execute Android instrumentation rather than stopping at compilation:

```bash
./gradlew --no-daemon --stacktrace connectedDebugAndroidTest
```

GitHub normal CI runs the real instrumentation suite on API 33. The tag release workflow executes the
suite on API 30/33/36 before signing/publication.

The instrumented set includes the actual pinned-Media3 natural-boundary scenarios and Android managed
storage stress coverage; these tests exist specifically for behavior that JVM/stub tests cannot prove.

The generated Room schema must remain exactly schema `1.json` with the four production tables. Protocol
constants, NSD advertisement, handshakes, framing, envelopes, and documentation must remain strict
Protocol 2 for the 1.2 release line.

`check-release-quality.sh` includes the focused hardening suite: RFC SRP arithmetic conformance,
production PIN-handshake tests, lifecycle seam regressions, session-generation fences, endpoint
authority checks, and control-priority/no-starvation stress coverage.

## Dependency/tooling integrity gate

Before a public release:

- GitHub Actions remain pinned to reviewed full commit SHAs;
- the tag workflow produces a GitHub/Sigstore provenance attestation for the checksummed release subjects;
- public Gradle defaults use official repositories; optional regional mirrors are opt-in only;
- `gradle/verification-metadata.xml` exists, was generated from a trusted dependency-resolution path,
  reviewed, and matches the intended dependency graph;
- ShellCheck/actionlint/Python workflow/tooling checks pass;
- unexpected dependency-verification changes are investigated rather than auto-accepted.

Use `scripts/refresh-dependency-verification.sh` only when intentionally refreshing dependency
metadata. Keep the metadata change reviewable and separate from unrelated behavior changes.

## Playback/stability-log gate

For retained physical candidates:

```bash
./scripts/capture-playback-log.sh unison-playback.ndjson
./scripts/analyze-playback-log.py unison-playback.ndjson --strict
./scripts/analyze-stability-log.py unison-playback.ndjson --strict
```

Retain the sanitized trace or its approved summary together with device details, tag/commit, source
package checksum, and exact APK checksum.

## Device/API matrix

The signed candidate must be exercised on physical Android 11 (API 30), Android 13 (API 33), and
Android 16 (API 36) devices. Across the matrix, qualify discovery/control, verified file transfer, and
playback. Include private router Wi-Fi and LocalOnlyHotspot on at least one supported device.

The selected network route should be `SYSTEM_DEFAULT` when the owning LAN is Android's active network,
`NETWORK_BOUND` for a non-default owning `Network`, and `ENDPOINT_FALLBACK` only for genuine hotspot or
downstream cases where Android exposes no owning `Network`.

Use the detailed scenario list in [`PHYSICAL_DEVICE_QUALIFICATION.md`](PHYSICAL_DEVICE_QUALIFICATION.md).

## Critical scenarios

At minimum retain evidence for:

- one-hour normal playback and a 30-minute mixed-control three-device room;
- rapid transport/current-item changes and unavailable-successor preparation;
- final natural completion followed by canonical replay from position 0;
- actual Media3 natural-boundary attribution/duplicate suppression;
- queue mutations during preparation/import;
- coordinator loss and bounded recovery/clean end;
- listener Wi-Fi interruption and complete reconciliation;
- API 30/32 connected-but-unselected output-route behavior (`UNKNOWN`) and API 33/36 actual route changes;
- audio-focus interruption/rejoin and becoming-noisy silence semantics;
- transfer interruption/resume, corruption, insufficient storage, source loss;
- room-A delayed admission delivered after room-B creation;
- superseded control socket with already-queued envelope and delayed close;
- stale transfer completion/failure/progress crossing a room generation;
- authenticated endpoint host spoof attempt;
- concurrent temporary deletion/upload resolution including corrupt-file repair;
- sustained lower-priority control traffic with repeated guaranteed/clock work;
- background/screen-off/process/task-removal lifecycle scenarios.

## Acceptance invariants

A candidate fails if any of these are violated:

- a stale asynchronous operation mutates the current authoritative room/session/connection;
- a connected ready listener remains on a different canonical item/play-pause intent without bounded repair;
- a stale scheduled command reaches Media3;
- natural completion resurrects the finished item or attributes one physical boundary more than once;
- final-item Play after genuine terminal natural pause fails to become canonical replay from zero;
- unavailable content mutates current playback or creates a preparation/command storm;
- cleared transient audio-focus suppression leaves automatic rejoin stuck indefinitely;
- a participant projects canonical time while its room clock is unlocked;
- leaving leaks player work, transfers, sockets, leases, jobs, or notification ownership;
- diagnostics are malformed/unbounded, drop under candidate soak, or include
  `room.event.unexpected_handler_cancellation`;
- a spoofed endpoint host receives an outbound transfer connection;
- an upload-readable file disappears after its atomic upload lease handoff;
- pending deletion can remove newly republished valid media;
- guaranteed room commands or clock traffic starve under sustained lower-priority traffic;
- repository/SRP/admission/authorization/security gates fail.

Stale admission/envelope/transfer/session diagnostic events may appear as bounded forensic evidence only
when the corresponding obsolete work was rejected before current-state mutation.

## Exact-artifact publication gate

For a tag such as `v1.2.0-beta.4`:

1. tag the exact qualified commit and push that immutable tag;
2. let the GitHub release workflow re-run verification/instrumentation and build/sign the APK;
3. download the APK, source package, `release-info.txt`, and `SHA256SUMS.txt` from that workflow/release;
4. verify published checksums/signing identity and, for the public repository, verify the GitHub artifact
   attestation (for example with `gh attestation verify` against the repository);
5. install the exact downloaded APK (clean install and supported upgrade path where applicable);
6. repeat a focused physical smoke test using that artifact;
7. update `docs/release-evidence/<version>.md` with real hashes/results/known issues;
8. only then approve prerelease/stable publication/announcement.

Never backfill a release-evidence checkbox from a different debug/local build or another commit.
