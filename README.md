# Unison

[![Android CI](https://github.com/mmrzaf/unison/actions/workflows/android.yml/badge.svg)](https://github.com/mmrzaf/unison/actions/workflows/android.yml)
[![CodeQL](https://github.com/mmrzaf/unison/actions/workflows/codeql.yml/badge.svg)](https://github.com/mmrzaf/unison/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-brightgreen.svg)](docs/PHYSICAL_DEVICE_QUALIFICATION.md)

Unison is a **local-first Android app for synchronized music playback across nearby phones**.

One phone coordinates the room while every listener plays a verified local copy of the same track. Unison works over the local network and does not require an account, cloud backend, hosted relay, analytics service, advertising SDK, or Internet connection.

## Features

- Synchronized playback across nearby Android devices
- Create or join rooms over the local network
- Discover nearby rooms automatically
- Add music from the local library, playlists, share sheet, or file picker
- Transfer missing tracks directly between devices
- Verify transferred audio with SHA-256 before playback
- Shared room queue and playback controls
- Local playlists and library management
- Resilient handling of temporary connectivity loss
- Local-only structured diagnostics
- No account, cloud service, analytics, advertising, or hosted relay

## Install

Unison requires **Android 11 or newer**.

Download the latest APK from:

**[GitHub Releases](https://github.com/mmrzaf/unison/releases)**

Because Unison is distributed as an APK, Android may ask you to allow installation from your browser or file manager.

Release notes, checksums, and other release artifacts are available alongside each release.

## How it works

1. **Create a room** on one phone or join a nearby room.
2. **Add music** from your device.
3. **Invite nearby listeners** to the room.
4. If another device is missing a track, Unison transfers it directly over the local network.
5. The received file is verified before it becomes playable.
6. Playback is coordinated across participating devices.

Every listener ultimately plays a local copy of the same audio rather than receiving a live audio stream from the host.

This keeps playback local and allows Unison to focus on maintaining a shared, synchronized room state across nearby devices.

## Media readiness

Tracks in a room have an explicit readiness state:

- **Not ready** — the device does not yet have a verified playable copy.
- **Preparing** — the track is being transferred or verified.
- **Ready** — the track is available for synchronized playback.

Unison does not mark transferred media as playable until verification has completed successfully.

## Local-first by design

Unison is designed for nearby devices on private networks.

It does not depend on:

- user accounts
- cloud storage
- hosted APIs
- Internet relays
- analytics services
- advertising networks
- billing services

Public network addresses and Internet-based room joining are intentionally outside the product model.

## Architecture

At a high level:

- A serialized room actor owns canonical room mutations.
- `RoomReducer` handles deterministic room state transitions.
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

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the full development environment and setup guide.

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

Testing covers protocol behavior, room state, playback coordination, transfer integrity, storage, Android integration, and supported device/API configurations.

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
- [Changelog](CHANGELOG.md)
- [Support](SUPPORT.md)

Release and project-maintenance documentation is also available under `docs/` for contributors and maintainers.

## Contributing

Contributions are welcome.

Bug fixes, tests, documentation improvements, Android/OEM compatibility reports, and changes that preserve Unison's local-first design are especially useful.

Start with [CONTRIBUTING.md](CONTRIBUTING.md).

For questions and ideas, use [GitHub Discussions](https://github.com/mmrzaf/unison/discussions).

For reproducible bugs, use [GitHub Issues](https://github.com/mmrzaf/unison/issues).

## Security

Please do **not** report security vulnerabilities through public GitHub issues.

See [.github/SECURITY.md](.github/SECURITY.md) for vulnerability reporting instructions and [docs/SECURITY.md](docs/SECURITY.md) for the application's security model.

## Privacy

Unison is designed around local communication and local storage.

See the [Privacy Policy](docs/PRIVACY_POLICY.md) for details.

## Support

For troubleshooting and support information, see [SUPPORT.md](SUPPORT.md).

## License

Unison is licensed under the [Apache License 2.0](LICENSE).

Third-party components remain under their respective licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
