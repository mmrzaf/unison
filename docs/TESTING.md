# Testing

Unison tests should be simpler than Unison itself. The goal is strong invariants and repeated abuse,
not a custom distributed-systems test platform.

## Fast repository checks

These checks do not require an Android device:

```bash
./scripts/check-static.sh
./scripts/check-data.sh
python3 ./scripts/analyze-playback-log.py --self-test
python3 ./scripts/analyze-stability-log.py --self-test
python3 ./scripts/check-log-analyzer-fixtures.py
```

With the repository-pinned Gradle/Kotlin dependency cache available:

```bash
./scripts/check-release-quality.sh
```

The repository-specific release-quality gate runs static/data checks, diagnostic analyzers, the
100,000-track library browse/search benchmark, and the unique Android network-lifecycle harness. The
normal Gradle gate then runs the complete JVM unit suite, formatting, lint, APK compilation, and
Android-test compilation against the real dependencies. The benchmark verifies all four browse sort
modes, a deep title page, literal substring search, and SQLite query plans; indexed browse paths are not
allowed to regress to a temporary B-tree sort.

## High-value stress coverage

Normal JVM tests deliberately repeat the dangerous state transitions rather than inventing a separate
simulation framework:

- `ReliabilityStressTest`: 20,000 deterministic transfer mutations while continuously checking every
  shared capacity invariant;
- readiness/convergence stress: 10,000 repeated Prepare/unavailable-media reconciliation cycles with
  zero futile playback repair until READY;
- `ManagedFileStoreTest`: repeated interrupted writes with monotonic partial offsets, resume, final
  SHA-256 commit, byte equality, atomic readable-file lease acquisition, corruption repair under
  publication protection, durable pending deletion, multi-lease release, and restart cleanup;
- focused tests for duplicate assignments, cancellation ordering, retry/backoff, same-pair admission,
  room reconnect grace, session-tagged transfer terminal events, stale direct-mutation fencing, terminal
  natural-pause replay semantics, pre-33 route-query conservatism, MediaSession command capabilities,
  and long scheduled-command clock remapping;
- `RoomLifecycleSeamRegressionTest`: deterministic consume-time checks for obsolete admission, superseded
  connection identity, old-session progress/heartbeat consequences, and terminal replay revision fencing;
- `ControlConnectionPriorityTest`: exact ready-queue priority plus 2,000 sustained all-lanes-ready cycles
  proving guaranteed/clock traffic is not starved by playback-reference/transfer/telemetry work;
- `Srp6aCoreRfc5054Test` plus `PinPakeTest`: published SRP-6a arithmetic-vector conformance followed by
  production matching-code, wrong-code, proof/session single-use, and public-value checks.

`Media3NaturalBoundaryIntegrationTest` drives short local PCM media through the actual pinned Media3
player and covers two-item continuation, final-item completion, repeat-one, explicit replay re-arming, and
playlist mutation near a boundary. It is the regression boundary for callback-order/misattribution risk;
production listener logic must not be changed merely from a speculative callback order.

Android instrumentation also exercises the integration seams that pure policies cannot prove:

- `PlaylistRepositoryAndroidTest` uses the real Room unique index and verifies long-distance reorder,
  append/remove compaction, and persistence across database close/reopen;
- `TrackRepositoryAndroidTest` imports through a real test `ContentProvider`, app-managed filesystem
  publication, and Room transaction, including unknown/underreported provider sizes, low-space rollback
  with no published partial state, persisted normalized sort keys, and RECENT ordering after playback;
- `RoomRuntimeIdentityAndroidTest` sends the real display-name command through `RoomRuntime` and verifies
  DataStore, runtime identity, and `RoomStore` stay canonical without changing the peer ID;
- `ManagedFileStoreAndroidStressTest` repeats interrupted/resume/verified-commit behavior on the real
  Android filesystem;
- Media3 and Compose tests exercise actual platform/player/UI integration.

These are execution tests, not compile-only fixtures. Normal GitHub CI runs the full instrumented suite
on API 33; tagged release qualification runs it on API 30, 33, and 36 before signing. Contributors can
run the same suite on any connected device/emulator:

```bash
./gradlew --no-daemon --stacktrace connectedDebugAndroidTest
```

A multi-device lab is still unnecessary for ordinary changes, but Android/Media3/filesystem behavior
that depends on the platform must not be declared verified from JVM/stub tests alone.

## Stability invariants

Keep [INVARIANTS.md](INVARIANTS.md) green. In particular:

- unavailable content cannot become executable playback;
- repeated Prepare/demand is idempotent;
- same source→destination transfer capacity cannot be exceeded;
- intentional cancellation is not network failure;
- transfer progress/completion/failure from an old generation cannot affect the current room;
- completed media is hash-verified;
- a resolved upload file is leased atomically before it can be deleted;
- logical deletion while leased becomes a durable cleanup obligation rather than silent retained bytes;
- final natural completion followed by Play becomes canonical replay from position 0, while manual
  seek-to-end retains ordinary Play semantics;
- one physical Media3 natural boundary names the item that ended and is counted once;
- API 30-32 route state remains UNKNOWN rather than being inferred from connected-device inventory;
- MediaSession exposes only commands backed by canonical room behavior;
- a healthy steady room causes no player/transfer repair storm;
- exhausted room recovery ends rather than leaving stale synchronized state;
- an unexpected handler `CancellationException` while the persistent actor owner is still active is a
  release failure and must appear as `room.event.unexpected_handler_cancellation`;
- rejected stale admission/envelope/transfer/session work may be diagnosed, but cannot mutate current
  canonical state;
- sustained lower-priority control traffic cannot starve guaranteed room commands or clock traffic.

## Diagnostic trace analysis

Capture a real candidate trace when doing final device validation:

```bash
./scripts/capture-playback-log.sh unison-playback.ndjson
python3 ./scripts/analyze-playback-log.py unison-playback.ndjson --strict
python3 ./scripts/analyze-stability-log.py unison-playback.ndjson --strict
```

The playback analyzer checks transition/player/convergence failures plus the physical boundary,
pending-successor, content-readiness, local rejoin, and clock-domain invariants exercised by the
physical-device incidents. The stability analyzer checks unavailable-media rejection, duplicate
assignments, handshake timeouts, transfer reconnect/retry storms, malformed diagnostics, unclean room
teardown, and materially late scheduled playback. Playback arrival lateness is reported separately
from PlayerExecutor execution lateness so a slow network/room-actor delivery is not misdiagnosed as a
player scheduler failure.

`scripts/check-log-analyzer-fixtures.py` runs sanitized regression traces derived from 1.2 release-line
qualification failures. A release-quality change must keep the healthy fixture green and every bad
fixture red for the intended reason.

## Android/build qualification

Gradle commands run through the standard `./gradlew` wrapper against the developer or CI
Gradle home and normal cache behavior.

When Android SDK 36 and the pinned Gradle/dependency cache are available:

```bash
./scripts/verify-offline-ready.sh
./gradlew --offline --no-daemon --stacktrace \
  spotlessCheck testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease \
  :app:compileDebugAndroidTestKotlin
```

Compilation is followed by real Android execution:

```bash
./gradlew --no-daemon --stacktrace connectedDebugAndroidTest
```

CI uses API 33 as the ordinary instrumented baseline. Tagged prerelease/stable workflows require an
API 30/33/36 emulator matrix before signing/publication. Physical phones remain the final validation
layer because emulators still do not reproduce every OEM Wi-Fi, Media3, Bluetooth, foreground-service,
or power-management behavior.

Candidate-specific pass/fail status belongs in `docs/release-evidence/<version>.md`; this testing guide
describes the required mechanism and must not be edited to imply a pending candidate has already passed.

Release evidence records the exact tag/commit, GitHub-produced APK checksum/signing fingerprint,
automated matrix results, physical devices, retained diagnostics, known issues, and final decision.
See [`release-evidence/`](release-evidence/README.md).
