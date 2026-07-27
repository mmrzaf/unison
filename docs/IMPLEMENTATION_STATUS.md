# Implementation status

## Implemented

- Android application `com.darius.unison`, API 30 minimum, API 36 compile/target.
- Compose UI for onboarding, discovery, hotspot assistance, library, playlist detail/editing, room, queue, listeners, options, QR invitations, and storage management.
- File picker, share sheet, multiple-audio import, M3U/M3U8 import/export, and one-step folder resolution for relative playlist paths.
- Content-addressed app-private audio storage with SHA-256 deduplication, kept/temporary retention, 24-hour cleanup, visible kept/temporary totals, and confirmed user-controlled temporary cleanup.
- Room database and WorkManager cleanup.
- Android NSD discovery, direct QR fallback, and LocalOnlyHotspot support.
- Authenticated TCP room protocol and direct resumable peer-to-peer file transfer.
- Equal-permission room commands ordered by a silent coordinator with best-effort recovery.
- Media3 playback, foreground `MediaSessionService`, notification/lock-screen/headset controls routed through synchronized room commands, scheduled playback, clock sync, state reconciliation, and drift correction.
- Release signing configuration, APK-only build scripts, CI/release workflows, privacy policy, publication checklist, and Room schema export.
- Forty deterministic JVM core tests plus static repository checks.

## Required before public release

The Android debug APK and release variant build successfully, all 43 JVM tests pass, and
Android Lint completes for debug and release with no errors. Publication still requires building
with the permanent release key, verifying the signed APK, and completing the physical-device
matrix in `TESTING.md`.

## Deliberate limits

- no online URLs, streaming-service integration, Google Play services, cloud backend, analytics, ads, or internet rooms;
- no automatic Bluetooth output-latency calibration;
- no partition-tolerant distributed consensus;
- no automatic process-death room resurrection;
- no Android 17/API 37 local-network runtime-permission path yet;
- no built-in QR camera scanner.
