# Testing strategy

## Automated checks

```bash
./scripts/check-static.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The JVM suite covers:

- M3U parsing and encoding;
- frame authentication and tamper rejection;
- PIN-based room-secret protection;
- pure reducer ordering and permission decisions;
- clock-offset estimation;
- playback correction thresholds;
- local-address enforcement.

## Required physical-device matrix

Emulators are insufficient for Wi-Fi, hotspot, vendor NSD, audio output, and sleep behavior. Test at least:

| Device | OS | Role |
|---|---:|---|
| Pixel or AOSP-like | Android 11 / API 30 | coordinator and guest |
| Samsung or another heavily customized device | Android 12 / API 31–32 | coordinator and guest |
| Pixel/Samsung | Android 13 / API 33 | coordinator and guest |
| one later device | Android 14–16 | compatibility smoke test |

Use at least four physical phones for room-scale testing.

## Core scenarios

### Connection

- shared home Wi-Fi discovery;
- QR direct join when NSD is unavailable;
- LocalOnlyHotspot creation, Wi-Fi QR join, and re-advertisement;
- wrong PIN and wrong protocol rejection;
- screen off and Activity recreation;
- transient Wi-Fi loss and reconnect;
- coordinator leaves and deterministic recovery.

### Library

- MP3, M4A/AAC, FLAC, WAV, OGG/Opus;
- file-picker and share imports copied into managed storage; identical bytes deduplicated by SHA-256;
- share-sheet import copied into managed storage;
- duplicate-byte detection across different filenames;
- M3U/M3U8 with BOM, comments, `EXTINF`, content URI, absolute path, relative path, folder-resolution retry, wrong-folder fallback, and unresolved entries;
- temporary keep/remove and 24-hour cleanup.

### Transfer

- current, next, and preload priority;
- peer B sourcing directly to peer C;
- disconnect mid-transfer and resume;
- source authorization acknowledgment race;
- wrong token, expired token, wrong destination, wrong hash;
- low disk space and 1 GiB limit;
- source file removed after queueing.

### Synchronization

- join clock warm-up before first play;
- play, pause, seek, previous, next;
- rapid competing friend commands;
- natural transition to the next Media3 item;
- drift correction without audible repeated seeks;
- reconnect during playback and periodic state recovery;
- wired headphones, phone speaker, and several Bluetooth headset models.

## Acceptance targets

- No live audio streaming.
- Four phones can remain in one room for at least two hours.
- Queue commands converge to the same sequence and snapshot.
- No interruption of the current track when future tracks finish downloading.
- Typical same-LAN headphone timeline difference remains subjectively suitable for singing along; exact acoustic latency is hardware dependent.
- Interrupted track transfers resume and verify successfully.
