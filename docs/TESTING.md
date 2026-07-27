# Testing

## Network-free repository checks

```bash
./scripts/check-static.sh
./scripts/check-core.sh
./scripts/check-data.sh
```

The core harness compiles selected production sources with Kotlin/JVM and runs deterministic protocol, reducer, sync, storage, playlist, and policy tests without Android SDK downloads.

## Offline Android checks

After the Android SDK, Gradle distribution, and Maven dependencies have been provisioned locally:

```bash
./scripts/verify-offline-ready.sh
./scripts/build-debug.sh
./scripts/build-release.sh
```

The release path runs Android unit tests, release lint, R8/resource shrinking, signed APK generation, optional `apksigner` verification, and SHA-256 output.

## GitHub CI

Pushes and pull requests run the repository checks, Android unit tests, strict debug and release
lint, and both APK assemblies on Ubuntu 24.04. Successful runs expose `Unison-debug` for 14 days
and the unsigned, shrunk `Unison-release-unsigned` artifact for 30 days. Neither CI artifact is a
signed release.

## Manual device matrix

Test at least two physical devices, including Android 11 and a current target device:

1. first launch and permission denial/recovery;
2. audio import from file picker and share sheet;
3. large and malformed M3U rejection;
4. room creation, NSD discovery, QR join, and wrong-PIN throttling;
5. LocalOnlyHotspot creation and teardown;
6. queue add/remove/move, shared shuffle/repeat, seek, headset, lock-screen, and notification controls;
7. transfer interruption/resume, source loss, insufficient space, and corrupted partial recovery;
8. coordinator leave/recovery and transient Wi-Fi loss;
9. background playback, process removal, low-memory artwork clearing, and temporary cleanup;
10. signed APK upgrade over the previous locally signed build.

Document device model, Android version, battery restrictions, router/hotspot topology, and observed drift for every release candidate.
