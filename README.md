# Unison

[![Android CI](https://github.com/mmrzaf/unison/actions/workflows/android.yml/badge.svg)](https://github.com/mmrzaf/unison/actions/workflows/android.yml)
[![CodeQL](https://github.com/mmrzaf/unison/actions/workflows/codeql.yml/badge.svg)](https://github.com/mmrzaf/unison/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-brightgreen.svg)](docs/PHYSICAL_DEVICE_QUALIFICATION.md)

Unison is a **local-first Android app for synchronized music playback across nearby phones**.

One phone coordinates the room while every listener plays a verified local copy of the same track.
Unison works over the local network and does not require an account, cloud backend, hosted relay,
analytics service, advertising SDK, or Internet connection.

## Features

- Synchronized playback across nearby Android devices
- Nearby room discovery and four-digit room codes
- Local library, playlist, share-sheet, and file-picker imports
- Direct transfer of missing tracks between devices
- SHA-256 verification before transferred audio becomes playable
- Shared room queue and playback controls
- Bounded recovery from temporary connectivity loss
- Local-only structured diagnostics
- No accounts, cloud service, analytics, advertising, or hosted relay

## Install

Unison requires **Android 11 or newer**.

Download the latest available APK from [GitHub Releases](https://github.com/mmrzaf/unison/releases).
Release notes, checksums, source packages, and release metadata are published with each release.

Because Unison is distributed as an APK, Android may ask you to allow installation from your browser
or file manager.

## How it works

1. **Create a room** on one phone or join a nearby room.
2. **Add music** from the local device.
3. **Invite nearby listeners** to join.
4. If another device is missing a track, Unison transfers it directly over the local network.
5. The received file is verified before it becomes playable.
6. Playback is coordinated across participating devices.

Every listener ultimately plays a local copy of the same audio instead of receiving a live audio stream
from the coordinator.

## Media readiness

Tracks have an explicit readiness state:

- **Not ready** — the device does not yet have a verified playable copy.
- **Preparing** — the track is being transferred or verified.
- **Ready** — the track is available for synchronized playback.

Unison does not mark transferred media as playable until verification succeeds.

## Local-first by design

Unison is designed for nearby devices on private networks. It does not depend on:

- user accounts
- cloud storage
- hosted APIs
- Internet relays
- analytics services
- advertising networks
- billing services

Public network addresses and Internet-based room joining are intentionally outside the product model.

## Technical contract

- Application ID: `com.darius.unison`
- Wire protocol: **2 only**
- Room database schema: **1 only**
- Runtime floor: Android 11 (`minSdk 30`)
- Audio identity and transferred-file verification use SHA-256
- Public addresses and DNS joins are rejected

Version history and current release status belong in [CHANGELOG.md](CHANGELOG.md) and
[GitHub Releases](https://github.com/mmrzaf/unison/releases), not in this README.

## Architecture

At a high level:

- A serialized room actor owns canonical room mutations.
- `RoomReducer` handles deterministic room-state transitions.
- `TransferCoordinator` manages transfer demand and routing.
- `TransferManager` performs authenticated, resumable transfers and verified commits.
- `RoomMediaReadinessPolicy` derives runtime media readiness.
- `PlayerExecutor` owns Media3 playback mutations.
- Clock synchronization and playback correction remain device-local and bounded.
- Structured diagnostics remain local to the device.

Before changing room, playback, transfer, storage, or protocol behavior, read:

- [Architecture](docs/ARCHITECTURE.md)
- [Invariants](docs/INVARIANTS.md)
- [Protocol](docs/PROTOCOL.md)
- [Security model](docs/SECURITY.md)

## Project structure

```text
app/        application wiring and app lifecycle
library/    imports, search, playlists, and M3U handling
model/      immutable room, queue, command, and UI models
network/    discovery, hotspot, address policy, and sockets
playback/   Media3 integration and playback execution
protocol/   messages, authentication, framing, and cryptography
room/       room orchestration, reducer, convergence, and policies
storage/    database and content-addressed verified files
sync/       clock mapping and bounded playback correction
transfer/   authorized resumable peer-to-peer transfer
ui/         Jetpack Compose surfaces and interaction policies
util/       structured diagnostics and shared utilities
```

## Development

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for setup details.

Bootstrap a development environment:

```bash
./scripts/bootstrap-dev.sh
```

Run repository checks:

```bash
./scripts/check-static.sh
./scripts/check-data.sh
./scripts/check-release-quality.sh
```

Build a debug APK:

```bash
./scripts/build-debug.sh
```

Install it on a connected Android device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The repository's shell and release tooling are primarily qualified on Linux.

## Testing

Testing covers protocol behavior, room state, playback coordination, transfer integrity, storage,
Android integration, and the release API matrix.

See [docs/TESTING.md](docs/TESTING.md) for details.

## Documentation

- [Development setup](docs/DEVELOPMENT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Architecture decisions](docs/decisions/README.md)
- [Invariants](docs/INVARIANTS.md)
- [Protocol](docs/PROTOCOL.md)
- [Security model](docs/SECURITY.md)
- [Privacy policy](docs/PRIVACY_POLICY.md)
- [Structured diagnostics](docs/LOGGING.md)
- [Testing](docs/TESTING.md)
- [Roadmap](docs/ROADMAP.md)
- [Release qualification](docs/RELEASE_QUALIFICATION.md)
- [Release evidence](docs/release-evidence/README.md)
- [Local release](docs/LOCAL_RELEASE.md)
- [Changelog](CHANGELOG.md)
- [Support](SUPPORT.md)

## Contributing

Contributions are welcome. Bug fixes, tests, documentation improvements, Android/OEM compatibility
reports, and changes that preserve Unison's local-first design are especially useful.

Start with [CONTRIBUTING.md](CONTRIBUTING.md). Use
[GitHub Discussions](https://github.com/mmrzaf/unison/discussions) for questions and ideas and
[GitHub Issues](https://github.com/mmrzaf/unison/issues) for reproducible bugs.

## Security

Do **not** report security vulnerabilities through public GitHub issues. See
[.github/SECURITY.md](.github/SECURITY.md) for reporting instructions and
[docs/SECURITY.md](docs/SECURITY.md) for the application security model.

## Privacy

See the [Privacy Policy](docs/PRIVACY_POLICY.md).

## License

Unison is licensed under the [Apache License 2.0](LICENSE). Third-party components remain under their
respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
