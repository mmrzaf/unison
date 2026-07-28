# Testing

## Network-free repository checks

```bash
./scripts/check-static.sh
./scripts/check-core.sh
./scripts/check-data.sh
```

The core harness compiles selected production sources with Kotlin/JVM and runs deterministic
protocol, reducer, sync, storage, playlist, and policy tests without Android SDK downloads.

## Offline Android checks

After the Android SDK, Gradle distribution, and Maven dependencies have been provisioned locally:

```bash
./scripts/verify-offline-ready.sh
./scripts/build-debug.sh
./scripts/build-release.sh
```

The release path runs Android unit tests, release lint, R8/resource shrinking, signed APK
generation, optional `apksigner` verification, and SHA-256 output.

## GitHub CI

Pushes and pull requests run the repository checks, Android unit tests, strict debug and release
lint, and both APK assemblies on Ubuntu 24.04. Successful runs expose `Unison-debug` for 14 days
and the unsigned, shrunk `Unison-release-unsigned` artifact for 30 days. Neither CI artifact is a
signed release.

## Manual device matrix

Test at least three physical devices, including Android 11 and a current target device:

1. first launch, cloned-install identity collision recovery, and permission denial/recovery;
2. audio import from file picker and share sheet, including tracks with and without embedded
   artwork;
3. large and malformed M3U rejection;
4. room creation, QR join, wrong-PIN throttling, and manual nearby-room discovery: opening the lobby
   does not scan, Find rooms scans for eight seconds, results remain visible, and another tap
   performs a fresh scan;
5. LocalOnlyHotspot creation and explicit teardown, including app task removal while the hotspot is
   active;
6. three-device join while playback is running, followed by at least 30 minutes of
   play/pause/seek/skip testing without button flicker or seek churn;
7. queue add/remove/move, shared shuffle/repeat, headset, lock-screen, and notification controls;
8. transfer interruption/resume, already-verified-file recovery, source loss, insufficient space,
   and corrupted partial recovery;
9. artwork appearance on the receiving phone in the queue, full player, compact player,
   notification, and lock screen after transfer completion;
10. coordinator leave/recovery, terminal reconnect failure returning to a clean lobby, and transient
    Wi-Fi loss;
11. background playback, process removal, low-memory artwork clearing, corrupt artwork-cache
    recovery, and temporary cleanup;
12. signed APK upgrade over the previous locally signed build.

Document device model, Android version, battery restrictions, router/hotspot topology, and observed
drift for every release candidate.
