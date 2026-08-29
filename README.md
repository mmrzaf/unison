# Unison 1.2.0

Unison is a local-first Android app for synchronized music playback with nearby people. Each phone
plays a verified local copy of the same track while one temporary room coordinator orders queue and
playback intent over private Wi-Fi or Android LocalOnlyHotspot.

The product promise is deliberately narrow: get nearby phones to a verified common playable state,
then keep one shared playback experience synchronized with as little ongoing work as possible.

## Product contract

- Application ID: `com.darius.unison`
- Version: `1.2.0` (`versionCode` 4)
- Wire protocol: **2 only**
- Room database schema: **1 only**
- Runtime floor: Android 11 (`minSdk 30`, `targetSdk 33`); release qualification covers Android 11,
  13, and 16
- Local-only: no account, cloud backend, analytics, advertising, billing, hosted API, or relay
- No compatibility handshake, protocol negotiation, database migration, or persisted room session
- Public addresses and DNS joins are rejected
- Audio identity is SHA-256; a received file becomes playable only after full verification

Unison 1.2.0 installs over the signed 1.1.x/1.0.x release line when the same signing key is used.
Pre-release database/protocol states are not supported as upgrade sources.

## Experience

Unison has two primary surfaces:

1. **Home:** create/join nearby rooms, browse All Music, manage playlists, and import audio.
2. **Room:** shared playback, listeners, queue, media readiness, and diagnostics.

Room media readiness is explicit:

- **Not ready** → tap to prepare/transfer the song.
- **Preparing** → current verified transfer progress is shown without pretending the song can play.
- **Ready** → tap to play it for the room.

Background prefetch still prepares likely upcoming songs, but playback never depends on hidden
"prepare then maybe play later" state. A healthy room becomes quiet: no repeated player mutation,
transfer churn, or retry storm when nothing changed.

Temporary connectivity loss enters bounded recovery and pauses local synchronized output. If room
authority/connectivity cannot be recovered, the room ends truthfully instead of leaving zombie
listeners or stale playback behind.

## Architecture in one minute

- One serialized room actor owns canonical room mutations.
- `RoomReducer` owns deterministic canonical state transitions.
- `TransferCoordinator` owns transfer demand, admission, active routes, and route retry/backoff.
- `TransferManager` executes authenticated resumable byte transfer and verified commit.
- `RoomMediaReadinessPolicy` derives `NEEDS_PREPARATION / PREPARING / READY` outside canonical history.
- `PlayerExecutor` is the only Media3 mutation authority.
- Clock synchronization and local playback correction remain device-local and bounded.
- Structured diagnostics are local-only and intentionally causal enough to explain failures.

See [Architecture](docs/ARCHITECTURE.md) and [Invariants](docs/INVARIANTS.md) before changing room,
playback, transfer, or protocol behavior.

## Build locally

Prerequisites:

- JDK 21
- Android SDK 36 and compatible build tools
- Gradle/dependency cache, or normal network access for Gradle setup

Run the repository checks:

```bash
./scripts/check-static.sh
./scripts/check-data.sh
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

Never commit `keystore.properties`, a keystore, local Android configuration, or generated APKs.

## Source layout

```text
app/        application wiring and app lifecycle
library/    imports, search, playlists, M3U handling
model/      immutable room, queue, command, and UI models
network/    NSD, hotspot, private-address policy, sockets
playback/   Media3 integration and canonical playback execution
protocol/   strict Protocol 2 messages, authentication, framing, crypto
room/       serialized room orchestration, reducer, convergence/readiness/transfer policy
storage/    Room schema 1 and content-addressed verified files
sync/       monotonic clock mapping and bounded correction
transfer/   authorized resumable authenticated peer transfer
ui/         Compose surfaces and user interaction policies
util/       bounded structured diagnostics and shared utilities
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Invariants](docs/INVARIANTS.md)
- [Protocol 2](docs/PROTOCOL.md)
- [Security](docs/SECURITY.md)
- [Structured diagnostics](docs/LOGGING.md)
- [Testing](docs/TESTING.md)
- [Contributing](CONTRIBUTING.md)
- [Release qualification](docs/RELEASE_QUALIFICATION.md)
- [Physical-device qualification](docs/PHYSICAL_DEVICE_QUALIFICATION.md)
- [Local release](docs/LOCAL_RELEASE.md)
