# Unison 1.0.0 technical specification

## 1. Purpose

Unison synchronizes local music playback among nearby Android devices. It is a local-first application: each peer owns or receives an exact verified copy of a track, then plays it against a shared monotonic room timeline.

## 2. Platform

- Android application ID: `com.darius.unison`
- Application version: `1.0.0`, code 1
- Minimum SDK: 30
- Target and compile SDK: 36
- Kotlin, Jetpack Compose, AndroidX Room/Paging/WorkManager, Media3, coroutines, serialization, and ZXing core
- Local signed APK distribution only

AndroidX and build plugins are ordinary compile-time libraries. The installed application includes no Google Play Services, Firebase, account, billing, store delivery, analytics, advertising, or hosted API integration.

## 3. Functional requirements

### 3.1 Library

The user can import supported audio through Android's document picker or share sheet. Imports are copied into app-private content-addressed storage. Track identity is the lowercase SHA-256 digest of exact bytes. Duplicate bytes share one managed file. Metadata is sanitized and bounded. Files must be 1 byte to 1 GiB.

### 3.2 Playlists

Users can create, rename, delete, reorder, and play local playlists. M3U/M3U8 import supports content/file URIs, relative file paths, an optional SAF music tree, and local-library fallback. Input is limited to 4 MiB, 10,000 entries, and 8,192 characters per line. Export never exposes app-private paths.

### 3.3 Rooms

A device can create a room, advertise it through Android NSD, or create a LocalOnlyHotspot. Peers can discover the room or join from a validated `unison://join` invitation. All connected members can add and control by product design. One coordinator orders commands and supplies the canonical clock.

### 3.4 Queue and transport

The canonical queue supports 1,000 items. One command can add at most 100 validated track descriptors. Play, pause, seek, skip, item changes, shuffle, and repeat are represented as ordered mutations. Transport mutations are scheduled for a future coordinator monotonic timestamp.

### 3.5 Playback

Media3 ExoPlayer performs local playback. A small queue window avoids loading a large room queue into the player. A `MediaSessionService` exposes trusted play/pause/seek/skip commands through the synchronized command path and withholds unsafe local-only queue or speed mutation. Clock samples estimate coordinator offset; drift policy selects no correction, small speed adjustment, or hard seek.

### 3.6 Transfer

Missing upcoming audio is assigned to a source peer. A short-lived token binds track, source flow, and destination. Tokens are consumed atomically once validation succeeds. Transfers use a dedicated TCP channel, support resume offsets, enforce space and size limits, write through the managed store, and register only after complete SHA-256 verification.

### 3.7 Retention

Locally imported tracks are kept by default. Room-received tracks are temporary for 24 hours unless the user keeps them. WorkManager periodically removes expired sources and abandoned partial/artwork files. Failures retry a bounded number of times.

## 4. Local network boundary

The app accepts only loopback, link-local, or private site-local addresses. Public destination addresses are rejected for room and transfer sockets. Runtime source contains no HTTP endpoint. The manifest retains Android's `INTERNET` permission solely because raw private TCP sockets require it.

## 5. Protocol

Wire protocol version 1 is independent of application version 1.0.0. Handshakes negotiate that protocol, prove room PIN knowledge, exchange nonces, and protect delivery of the room secret with AES-GCM. Control frames are length-bounded and authenticated with HMAC-SHA-256. Envelopes carry room, term, sender, UUID, timestamp, and typed body. Malformed frames fail closed.

## 6. Security and privacy

Backups are disabled, cleartext HTTP is disabled, broad storage permission is not requested, signing material is local-only, and diagnostics redact common secret fields. No user data is uploaded because no upload service exists. Participating peers necessarily receive room metadata and audio assigned to them.

## 7. Reliability and performance

- staging/rename lifecycle for imported files;
- same-size existing files are digest-verified before reuse;
- partial transfer files are hidden until verification;
- bounded worker channels and socket admissions;
- coroutine cancellation checks during long I/O;
- Room transactions for source/track cleanup consistency;
- Paging for large libraries;
- moving Media3 queue window;
- replay filtering and sequence ordering;
- watchdogs, reconnect flow, preparation state, and coordinator recovery.

## 8. Build and release

GitHub CI validates the source and uploads short-lived debug and unsigned release APK artifacts, but does not sign or publish releases. There is no store publication or hosted release workflow. A local release machine must have JDK 21, Android SDK 36, the pinned Gradle distribution, and all Maven artifacts cached locally. `scripts/verify-offline-ready.sh` validates that prerequisite. `scripts/build-release.sh` runs offline checks, Android tests/lint, shrinking, local APK signing verification, and SHA-256 generation. No bundle artifact is produced.

## 9. Acceptance gates

A candidate is acceptable only after:

- `check-static.sh`, `check-core.sh`, and `check-data.sh` pass;
- offline Android unit tests, release lint, and release assembly pass;
- signed APK verification and checksum generation pass;
- at least two-device manual tests cover discovery, QR join, hotspot, synchronized controls, transfer interruption/resume, reconnection, background playback, cleanup, and local signed upgrade.
