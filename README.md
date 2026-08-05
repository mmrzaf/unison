# Unison 1.0.0

Unison is a local-first Android app for synchronized music playback with nearby people. Each phone
plays a verified local copy of the same track while one room timeline orders queue and playback
intent over private Wi-Fi or Android LocalOnlyHotspot.

## Product contract

- Application ID: `com.darius.unison`
- Version: `1.0.0` (`versionCode` 1)
- Wire protocol: 1 only
- Room database schema: 1 only
- Runtime support: Android 11–13 (`minSdk 30`, `targetSdk 33`)
- No account, cloud backend, analytics, advertising, billing, hosted API, or app-store runtime
- No compatibility handshake, protocol negotiation, database migration, or persisted room session
- Public addresses and DNS joins are rejected
- Audio is identified by SHA-256 and committed only after full verification

This repository is the first production baseline. Pre-release installations are intentionally not a
supported upgrade source; install 1.0.0 cleanly.

## Experience

Unison has two primary surfaces:

1. **Home:** create a room, join a nearby room, search and manage the local library, playlists, and
   imports in one continuous screen.
2. **Room:** view the four-digit code, shared player, listeners, and queue in one continuous screen.

The visible player always represents canonical room intent. Local recovery state never replaces the
room's official song or play/pause state.

## Build locally

Prerequisites:

- JDK 21
- Android SDK 36 and compatible build tools
- The pinned Gradle distribution and Maven dependencies already present locally

Verify the offline toolchain and run the complete repository gate:

```bash
./scripts/verify-offline-ready.sh
./scripts/check-release-quality.sh
```

Build and install a debug APK:

```bash
./scripts/build-debug.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Signed release

Create and securely back up one permanent signing key:

```bash
./scripts/create-release-key.sh
```

Build the signed APK:

```bash
./scripts/build-release.sh
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/release-SHA256SUMS.txt`

Never commit `keystore.properties` or signing material.

## Source layout

```text
app/        application state, settings, command bus
library/    imports, search, playlists, M3U handling
model/      immutable room, queue, command, and UI models
network/    NSD, hotspot, private-address policy, sockets
playback/   Media3 integration and canonical playback application
protocol/   protocol 1 messages, authentication, framing, crypto
room/       reducer, serialized session actor, convergence policy
storage/    Room schema 1 and content-addressed files
sync/       monotonic clock mapping and bounded correction
transfer/   authorized, resumable, authenticated peer transfer
ui/         two-surface Compose interface
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Protocol](docs/PROTOCOL.md)
- [Security](docs/SECURITY.md)
- [Privacy](docs/PRIVACY_POLICY.md)
- [Testing](docs/TESTING.md)
- [Release qualification](docs/RELEASE_QUALIFICATION.md)
- [Physical-device qualification](docs/PHYSICAL_DEVICE_QUALIFICATION.md)
- [Local release](docs/LOCAL_RELEASE.md)
