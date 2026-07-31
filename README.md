# Unison 1.0.0

Unison is a fully local Android application for synchronized music playback with nearby friends.
Every phone plays a verified local copy. Room commands, clock synchronization, and authorized audio
transfers stay on the private LAN or Android LocalOnlyHotspot.

## Product foundation

- Application ID: `com.darius.unison`
- Version: `1.0.0` (`versionCode` 1)
- Android 11–13 runtime support (`minSdk 30`, `targetSdk 33`)
- No account, cloud backend, telemetry, advertising, hosted API, or store runtime
- No Google Play Services, Firebase, Play Billing, Play Asset Delivery, or dynamic delivery
- APK-only local distribution and local signing
- Content-addressed SHA-256 audio library with resumable peer transfer
- Shared queue, transport controls, shuffle, repeat, clock sync, and drift correction
- Manual nearby-room search: each Find rooms tap performs one bounded eight-second scan
- Text-only track metadata with no thumbnail extraction, image cache, or image worker; system media
  controls receive one fixed Unison brand tile
- File picker, share sheet, playlists, and bounded M3U/M3U8 import/export

Android's `INTERNET` permission is intentionally retained because Android requires it for raw TCP
sockets, including private LAN sockets. Unison rejects public addresses and contains no remote
endpoint.

## Build locally

Prerequisites:

- JDK 21
- Android SDK 36 and compatible build tools (compile-time only; runtime target remains Android 11–13)
- Gradle distribution and Maven artifacts already present in the local Gradle cache

Check that the machine is ready for a network-free build:

```bash
./scripts/verify-offline-ready.sh
```

Build a debug APK:

```bash
./scripts/build-debug.sh
```

Install it directly:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Signed local release

Create and securely back up one permanent local signing key:

```bash
./scripts/create-release-key.sh
```

Then build the release APK:

```bash
./scripts/build-release.sh
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/release-SHA256SUMS.txt`

Never commit `keystore.properties` or signing keys. Future local upgrades must use the same signing
identity.

## Validation

```bash
./scripts/check-static.sh
./scripts/check-core.sh
./scripts/check-data.sh
```

The development baseline is Room schema 1, wire protocol 1, application version `1.0.0`, and Android
`versionCode` 1. Future database changes must add explicit migrations from schema 1; protocol changes
must deliberately increment protocol 1 rather than carrying pre-release fallback branches.

## Structure

```text
app/        application container, settings, command bus
library/    managed imports, library, playlists, M3U
model/      domain and command models
network/    local discovery, hotspot, sockets, address policy
playback/   Media3 playback, media session, scheduler
protocol/   framing, handshake, authentication, wire models
room/       reducer, serialized engine, session runtime
storage/    Room database, content-addressed files, cleanup
sync/       monotonic clock and playback correction
transfer/   authorized resumable peer transfer
ui/         Compose UI and permission flow
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Protocol](docs/PROTOCOL.md)
- [Security](docs/SECURITY.md)
- [Privacy](docs/PRIVACY_POLICY.md)
- [Testing](docs/TESTING.md)
- [Local release](docs/LOCAL_RELEASE.md)
- [Validation status](docs/VALIDATION.md)
- [Technical specification](docs/unison-technical-specification.md)
