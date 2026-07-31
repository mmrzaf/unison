# Testing

## Network-free repository checks

```bash
./scripts/check-static.sh
./scripts/check-core.sh
./scripts/check-data.sh
./scripts/check-risky-kotlin.sh
./scripts/check-player-kotlin.sh
./scripts/check-session-player-kotlin.sh
./scripts/check-network-lifecycle-kotlin.sh
./scripts/check-release-quality.sh
./scripts/benchmark-library-search.py --max-p95-ms 50
```

The core harness compiles selected production sources with Kotlin/JVM and runs deterministic
protocol encryption, transfer authentication, reducer, lifecycle, synchronization, storage, search,
playlist-resolution, path-security, SAF-ledger, sanitization, service lifecycle, coordinator playback, and
player-event-isolation, playback-dispatch, timeline-reconciliation, and room-UI policy tests without Android SDK downloads.
The network-lifecycle harness also executes stale-callback, late-reservation, active-resolver cleanup, and
multicast-lock ownership tests against Android API stubs.

## Android release checks

After the Android SDK, pinned Gradle distribution, and Maven dependencies are available locally:

```bash
./scripts/verify-offline-ready.sh
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --offline
```

These checks are mandatory before installing a release candidate. Repository JVM gates do not prove
Compose compilation, manifest integration, Media3 runtime behavior, or device power-management behavior.

## Manual device matrix

Test at least three physical devices covering Android 11, Android 12, and Android 13:

1. one-participant room playback for at least one hour with repeated screen-off/on cycles;
2. confirm the only persistent notification is the Media3 player-control notification and that
   play, pause, seek, previous, and next remain synchronized;
3. confirm no image appears in library, playlist, queue, full player, compact player, notification,
   or lock screen, including audio files containing embedded pictures;
4. first launch, permission denial/recovery, file-picker import, share-sheet import, and malformed M3U rejection;
5. room creation, four-digit SRP join, wrong-code throttling, bounded discovery, and join cancellation;
6. LocalOnlyHotspot creation and explicit teardown, including task removal while active;
7. three-device play/pause/seek/skip for at least 30 minutes without speed churn, seek churn, or button flicker;
8. transfer interruption/resume, insufficient space, corrupted partial recovery, and source loss;
9. coordinator leave/recovery, terminal reconnect cleanup, transient Wi-Fi loss, and sleep/wake recovery;
10. repeated create/join/leave cycles followed by post-room CPU, network, storage, coroutine, and notification quiescence;
11. signed APK upgrade over the previous locally signed build.

Record device model, Android version, battery restrictions, router/hotspot topology, notification
behavior, audio interruptions, and observed drift for every release candidate.

## Stability invariants

Keep these cases green:

- coordinator and participant players use the same canonical timeline and bounded correction policy;
- speed commands are quantized, hysteresis-gated, and rate-limited before reaching Media3;
- UI commands refresh service start ownership without foreground promotion, and an idle-stop timer
  cannot stop a newer command;
- generic foreground notifications and repeated `startForegroundService` calls are absent;
- position and duration changes do not enter the serialized room actor; meaningful player transitions do;
- same-size managed-file corruption is rejected by SHA-256;
- active leases block deletion and cleanup;
- cancellation closes transfer sockets before cancelling jobs;
- control frames and file records reject tampering and replay;
- generation-scoped teardown leaves no tracked room jobs;
- screen-off heartbeat grace, reconnect pacing, and bounded CPU/Wi-Fi ownership remain covered;
- private IPv4/IPv6 parsing rejects public addresses and DNS names.

## Architecture boundaries

- structural room changes publish independently from playback and transfer telemetry;
- all canonical mutations remain actor-serialized and queue bulk work remains batched;
- high-frequency playback position remains outside the actor;
- `UnisonApp.kt` remains a shell and `MainViewModel.kt` remains a flow coordinator;
- no artwork extraction, image cache, image worker, image UI, or artwork metadata is reintroduced;
- the private invitation code is exposed only through the temporary host-only Invite surface; room
  discovery never grants admission and protocol options are not added unless enforced end to end.

## Playback trace analysis

Use `scripts/analyze-playback-log.py` on every playback soak trace. The strict mode rejects transition
storms, unavailable-song failures, circuit-breaker activation, and reported playback-dispatch failures.
See [Release qualification](RELEASE_QUALIFICATION.md) for the device matrix and acceptance criteria.
