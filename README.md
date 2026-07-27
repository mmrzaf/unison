# Unison

**Unison** is an offline Android app for listening to the same local music at the same time with friends. Each phone plays its own verified local copy; the network carries room commands, clock-sync messages, and upcoming audio files—not live audio.

- Application ID: `com.darius.unison`
- Minimum Android version: Android 11 / API 30
- Primary test baseline: Android 11, 12, and 13
- Forward compatibility target: Android 14–16
- No account, cloud backend, Google Play services, analytics, ads, or internet connection
- Shared Wi-Fi or Android LocalOnlyHotspot
- Android NSD/DNS-SD discovery with QR/direct fallback
- Authenticated TCP room control and resumable peer-to-peer audio transfer
- Media3 ExoPlayer and `MediaSessionService`

## User flow

1. Add audio through Android's file picker, share sheet, or M3U/M3U8 import.
2. Create a room or discover one on the local network.
3. Friends join using the room PIN or invitation QR.
4. Everyone can add music and use playback controls.
5. Upcoming tracks preload automatically and play locally against the shared room clock.
6. Music received from a room is temporary for 24 hours by default and can be kept explicitly.

Unison stores imported music in a content-addressed app-private library. The same SHA-256 track bytes are stored once and reused by playlists, room queues, and transfers. This favors dependable playback over depending on document-provider permissions or files that may move after import. M3U8 export contains references and metadata only; app-private paths are never exposed.

## Build

Requirements:

- Android Studio with Android Gradle Plugin 8.13.2 support
- JDK 21
- Android SDK 36

If the default Android dependency repositories are not reachable from the local network, enable the repository's optional
mirrors for that invocation with `-PuseIranMirrors=true` or `USE_IRAN_MIRRORS=true`.

```bash
./scripts/check-static.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Install the debug APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release build

Create and securely back up one permanent signing key:

```bash
./scripts/create-release-key.sh
```

Build signed publication artifacts:

```bash
./scripts/build-release.sh
```

Outputs:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/release-SHA256SUMS.txt`

Never commit `keystore.properties` or signing keys. Every update must use the same signing identity.

## Project structure

```text
app/src/main/java/com/darius/unison/
  app/        application container, settings, command bus, shared UI state
  library/    managed imports, library, playlists, M3U codec
  model/      domain and command models
  network/    NSD, hotspot, sockets, address policy, Wi-Fi locks
  playback/   Media3 adapter, system media controls, scheduler, room service
  protocol/   framing, handshakes, authentication, wire models
  room/       pure reducer, serialized engine, session runtime
  storage/    Room database, content-addressed files, cleanup
  sync/       monotonic clock and drift correction
  transfer/   authorized resumable peer-to-peer transfer
  ui/         Compose UI and permission flow
```

## Design rules

- The UI is peer-equal; the coordinator exists only to order commands and provide one room clock.
- System notification, lock-screen, headset, and in-app controls all issue synchronized room commands. The media session rejects untrusted controllers and does not expose local-only queue, repeat, shuffle, or speed changes.
- No command means “play now”; transport changes are scheduled on the shared monotonic timeline.
- Track identity is SHA-256 of exact bytes.
- Control state is deterministic and sequence-numbered.
- File and control traffic use separate connections.
- No broad storage permission is requested.
- Public internet addresses are rejected.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Protocol](docs/PROTOCOL.md)
- [Security model](docs/SECURITY.md)
- [Testing and device matrix](docs/TESTING.md)
- [GitHub release checklist](docs/GITHUB_RELEASE.md)
- [Privacy policy](docs/PRIVACY_POLICY.md)
- [Complete technical specification](docs/unison-technical-specification.md)

## Known limits

- Bluetooth devices add output latency that Android does not expose consistently. Unison synchronizes player timelines but cannot eliminate headset-internal latency.
- Coordinator recovery is best-effort during ordinary departure or transient loss; it is not partition-tolerant distributed consensus.
- Process death ends the active room. The last snapshot is diagnostic state, not automatic secret recovery.
- Android 17/API 37 local-network permission support must be added before targeting API 37.
