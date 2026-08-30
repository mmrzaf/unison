# Unison

[![Android CI](https://github.com/mmrzaf/unison/actions/workflows/android.yml/badge.svg)](https://github.com/mmrzaf/unison/actions/workflows/android.yml)
[![CodeQL](https://github.com/mmrzaf/unison/actions/workflows/codeql.yml/badge.svg)](https://github.com/mmrzaf/unison/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-brightgreen.svg)](docs/PHYSICAL_DEVICE_QUALIFICATION.md)

Unison is a **local-first Android app for synchronized music playback across nearby phones**. One
phone temporarily coordinates the room; every listener plays a verified local copy of the same track.
There is no account, cloud backend, hosted relay, analytics service, advertising SDK, or required
Internet connection.

> **Public alpha:** `1.2.0-alpha.1` is an early 1.2 prerelease. It uses the signed release build
> path intended for stable releases while focused compatibility feedback is collected before beta.

## Download the alpha

Download the latest prerelease APK from [GitHub Releases](https://github.com/mmrzaf/unison/releases).
Unison supports Android 11+ (`minSdk 30`). APKs are sideloaded; stable and prerelease builds can update one
another when signed by the same release key and when `versionCode` increases.

Before installing an alpha, read the release notes and verify the published SHA-256 checksum if you want
to validate the downloaded artifact independently.

## How it works

1. **Create a room** on one nearby phone, or join a discovered room with its four-digit code.
2. **Add music** from the local library, playlists, share sheet, or file picker.
3. **Play together.** Missing tracks are transferred directly over the private LAN, verified by
   SHA-256, and become playable only after verified commit.

The product promise is deliberately narrow: get nearby phones to a verified common playable state,
then keep one shared playback experience synchronized with as little ongoing work as possible.

## Product contract

- Application ID: `com.darius.unison`
- Version: `1.2.0-alpha.1` (`versionCode` 4)
- Wire protocol: **2 only**
- Room database schema: **1 only**
- Runtime floor: Android 11 (`minSdk 30`, `targetSdk 33`); release qualification covers Android 11,
  13, and 16
- Local-only: no account, cloud backend, analytics, advertising, billing, hosted API, or relay
- No compatibility handshake, protocol negotiation, or persisted room session
- Public addresses and DNS joins are rejected
- Audio identity is SHA-256; a received file becomes playable only after full verification

The 1.2 alpha line is expected to upgrade forward into later 1.2 alphas, betas/RCs, and stable `1.2.0` while
Protocol 2 and database schema 1 remain unchanged. The signed 1.1.x/1.0.x release line is also a
supported installation source for the 1.2 release line.

## Alpha status and known limitations

- Nearby/private-network operation only; there is no Internet relay.
- Every listener ultimately needs the exact track bytes. Unison transfers missing content locally.
- Android/OEM Wi-Fi, background, audio-focus, and output-route behavior can vary; device-specific
  reports are especially valuable during alpha.
- `targetSdk 33` is intentionally retained for the 1.2 line and qualified separately from
  `compileSdk 36`; a target-SDK upgrade is not mixed into this stabilization cycle.
- Alpha releases are prereleases. Use [Issues](https://github.com/mmrzaf/unison/issues) for reproducible
  bugs and [Discussions](https://github.com/mmrzaf/unison/discussions) for questions and ideas.

## Experience

Unison has two primary surfaces:

1. **Home:** create/join nearby rooms, browse All Music, manage playlists, and import audio.
2. **Room:** shared playback, listeners, queue, media readiness, and diagnostics.

Room media readiness is explicit:

- **Not ready** → tap to prepare/transfer the song.
- **Preparing** → current verified transfer progress is shown without pretending the song can play.
- **Ready** → tap to play it for the room.

Temporary connectivity loss enters bounded recovery and pauses local synchronized output. If room
authority/connectivity cannot be recovered, the room ends truthfully instead of leaving zombie
listeners or stale playback behind.

## Architecture in one minute

- One serialized room actor owns canonical room mutations.
- Any asynchronous work that may mutate current room state carries enough immutable provenance to
  prove that it still belongs to the authoritative room/session/connection when consumed.
- `RoomReducer` owns deterministic canonical state transitions.
- `TransferCoordinator` owns transfer demand, admission, active routes, and route retry/backoff.
- `TransferManager` executes authenticated resumable byte transfer and verified commit.
- `RoomMediaReadinessPolicy` derives runtime readiness outside canonical history.
- `PlayerExecutor` is the only Media3 mutation authority.
- Clock synchronization and local playback correction remain device-local and bounded.
- Structured diagnostics are local-only and intentionally causal enough to explain failures.

Read [Architecture](docs/ARCHITECTURE.md), [Invariants](docs/INVARIANTS.md), and
[Protocol 2](docs/PROTOCOL.md) before changing room, playback, transfer, storage, or wire behavior.

## Development

The full development and bootstrap guide is in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
The supported release/tooling environment is Linux; Android Studio/Gradle development may work on
other platforms, but the repository shell/release toolchain is qualified on Linux.

Typical first-time setup:

```bash
./scripts/bootstrap-dev.sh
```

Then run repository checks:

```bash
./scripts/check-static.sh
./scripts/check-data.sh
./scripts/check-release-quality.sh
```

Build/install a debug APK during normal development:

```bash
./scripts/build-debug.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`develop` is the normal development branch. Release-style builds are created from immutable tags such
as `v1.2.0-alpha.1`; a release build does not imply a stable release.

## Contributing

Contributions are welcome, especially focused fixes, tests, documentation, Android/OEM compatibility
reports, and improvements that preserve the local-first product contract. Start with
[CONTRIBUTING.md](CONTRIBUTING.md) and [docs/ROADMAP.md](docs/ROADMAP.md).

Security issues should **not** be filed as public bug reports. See [.github/SECURITY.md](.github/SECURITY.md).

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

- [Development setup](docs/DEVELOPMENT.md)
- [GitHub/project setup](docs/GITHUB_SETUP.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Architecture decisions](docs/decisions/README.md)
- [Invariants](docs/INVARIANTS.md)
- [Protocol 2](docs/PROTOCOL.md)
- [Security model](docs/SECURITY.md)
- [Privacy policy](docs/PRIVACY_POLICY.md)
- [Structured diagnostics](docs/LOGGING.md)
- [Testing](docs/TESTING.md)
- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Roadmap](docs/ROADMAP.md)
- [Release qualification](docs/RELEASE_QUALIFICATION.md)
- [Physical-device qualification](docs/PHYSICAL_DEVICE_QUALIFICATION.md)
- [Release evidence](docs/release-evidence/README.md)
- [Local release](docs/LOCAL_RELEASE.md)
- [Changelog](CHANGELOG.md)

## License

Unison is licensed under the [Apache License 2.0](LICENSE). Third-party components remain under their
respective licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
