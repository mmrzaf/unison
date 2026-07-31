# Unison 1.0.0 release readiness

Status: source-complete and locally validated; device qualification and signing remain open release
gates.

## Root cause and corrections

Accepted item-selection commands were owned indirectly by nullable pending state. Queue
reconciliation and same-item no-op branches could bypass the scheduler without publishing a
terminal result, while duplicate preparation could create a second pending command and deadline.
Timeline shrinking could also remove the audible item before the scheduled target transition.

Transport ownership now uses non-null generation tickets, monotonic phases, bounded completed
tombstones, explicit no-op settlement, duplicate coalescing/supersession, guarded timeline work,
and generation-scoped watchdogs. Every terminal transition is deduplicated and shutdown drains all
remaining active commands as superseded.

## Behavior changes

- Tapping the already-playing canonical item settles immediately without seek, restart, timeline
  rebuild, or canonical mutation.
- Tapping the paused canonical item resumes from its canonical position.
- Duplicate taps during preparation, scheduling, buffering, or execution retain the first owner's
  deadline and are terminally superseded by that command.
- A newer incompatible selection supersedes older selection work and cancels its owned scheduler
  and preparation work.
- Scheduled play, pause, and seek perform final identity/play-state/position-tolerance guards and
  settle aligned no-ops without mutating Media3.
- A universal accepted/scheduled/executing watchdog reconciles once, then settles aligned work or
  reports a typed internal-consistency failure. Preparation retains its ten-second hard deadline.
- Queue reconciliation is revision/generation guarded and retains current plus target until the
  transition settles.
- Idle or intentionally paused rooms suspend synchronization reacquisition; scheduled playback
  resumes monitoring. Repeated diagnostics are rate-limited.
- The room code is never shown automatically. It opens only through the Room code overflow action
  on the device that locally owns the credential, dismisses manually, and hides on backgrounding.
- Every admitted member has the same playback, queue, and room-setting controls. Coordinator status
  remains an internal state-ordering and clock-ownership detail.
- The Media3 player notification is the sole playback notification. Its session metadata always
  supplies a fixed dark Unison brand tile, never music thumbnails, so Android SystemUI can derive a
  high-contrast palette; identical rendered state is dropped, changed state is throttled, and
  diagnostic counters cover enqueue/defer/dedup behavior.
- Application-container construction is main-thread single-owner without synchronized lazy
  contention; nonessential cleanup remains delayed off the first frame. Large room UI components
  were split and playback position remains isolated from the application shell state.
- Spotless with ktfmt is the single repository formatter. `spotlessCheck` and `spotlessApply` are
  available from Gradle.

## Removed obsolete files

- `ArtworkRetryPolicy.kt`, `ArtworkStore.kt`, and `ArtworkRetryPolicyTest.kt`: unused music-artwork
  extraction/cache code. The fixed system-media brand tile needs no file scan, cache, or worker.
- `QrCode.kt`: obsolete QR credential surface; joining remains four-digit PAKE authentication.
- `gradle/gradle-daemon-jvm.properties`: generated machine-local daemon/toolchain state, not a
  reproducible project input.

## Dependency and build-tool changes

- Removed ZXing core because the QR surface was deleted.
- Added Compose UI test JUnit4 and test-manifest artifacts for the room-code instrumentation suite.
- Added Spotless 8.8.0 and standardized Kotlin/Kotlin-DSL formatting on ktfmt.
- Restored `targetSdk` from 36 to the contracted API 33; `compileSdk` remains 36 for pinned library
  compatibility. No broad runtime dependency upgrade was performed.

## Automated validation

- Debug unit tests: 352 tests across 71 suites; 0 failed, 0 errored, 0 skipped.
- Android instrumentation: 5 room-code Compose tests and 1 fixed-system-artwork test compiled; not
  executed without a device.
- Android lint: debug 0 issues; release 0 issues.
- Kotlin/Java compilation: debug, release, unit-test, and Android-test sources passed without
  compiler warnings in Gradle output.
- Builds: debug APK passed; unsigned release APK passed R8, resource shrinking, and vital lint.
- Release APK: 5,894,585 bytes; `debuggable=false`; version 1.0.0 (1); min/target 30/33.
- Room schema export: exactly `1.json`, schema version 1.
- Search benchmark: 100,000 tracks, 14.57 ms median / 15.02 ms p95 in the standalone run.
- Static, schema, release-quality, log-analyzer self-test, formatting, and offline-readiness gates
  passed.

AGP reported that two dependency-provided native libraries could not be stripped and packaged them
unchanged (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`). This did not fail debug
or release packaging. No Gradle deprecation, Kotlin, Java, Compose compiler, Room, manifest, or
resource warning was emitted.

## Remaining risks and unverified areas

- No `adb`, emulator, or physical Android 11–13 device was available. Runtime notification controls,
  audio routes, process recreation, backgrounding, multi-peer behavior, rapid transport, large
  queues, upgrade installation, and soak/ANR/leak behavior remain unverified.
- No signing credentials were available; the verified release artifact is intentionally unsigned.
- `kotlinc` was unavailable, so the standalone core/player/session/network/protocol harness scripts
  explicitly skipped. Equivalent sources compiled in Gradle, but those independent harness runs are
  not claimed.
- No license-report plugin is configured. The Gradle release runtime dependency report is included
  with the external release deliverables; a legal license inventory still needs release-owner review.
- `Pasted text(250).txt` was absent, preventing strict analysis of the supplied reproduction trace or
  a fresh current-build device stress trace.

## Manual 1.0.0 QA checklist

1. Install fresh and upgrade the previous development build on API 30, 31/32, and 33.
2. Create a room; verify the code does not appear automatically, opens through the Room code menu,
   dismisses manually, hides on background, and is not announced while closed.
3. Join with correct/wrong codes and multiple members; verify discovery alone never admits a peer
   and every admitted member receives the same playback, queue, and room-setting controls.
4. Exercise already-playing, paused-current, double-tap, A/B/C rapid selection, missing media,
   removal while pending, disconnect, coordinator change, leave, clear, and shutdown.
5. Verify current and target remain in Media3 until scheduled execution and no track restarts.
6. Confirm the sole notification is Media3 controls and test play, pause, next, previous, rapid
   input, background/foreground, and screen off/on.
7. Test built-in and Bluetooth output, rotation, process recreation, large queues, repeated room
   creation, transfer interruption, and member disconnect during preparation.
8. Run a fresh stress trace through `analyze-playback-log.py --strict`; require zero notification
   shedding, crashes, ANRs, playback exceptions, leaked service work, or pending commands.
