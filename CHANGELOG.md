# Changelog

## Consolidated reliability and UX cleanup — 2026-07-28

- restored manual nearby-room discovery: Find rooms runs one eight-second scan, then stops until the
  user taps again;
- isolated every discovery listener and resolution so stale callbacks, cancellation, or one dead
  advertisement cannot terminate or interfere with another scan;
- added explicit searching and no-results states while keeping found rooms visible after the bounded
  scan;
- fixed duplicate-installation identity recovery without weakening room membership checks or normal
  reconnect identity;
- fixed received artwork reload, corrupt-cache self-healing, negative-cache invalidation, queue
  artwork, compact-player artwork, and Media3 metadata refresh;
- consolidated playback recovery around persistent play intent, gentle proportional drift
  correction, track-scoped seek cooldown, and emergency recovery;
- fixed transient snackbar races, cancellation swallowing, overly broad exception handling, terminal
  reconnect cleanup, verified-file database repair, and a duplicate UI-state argument that could
  break the Android build;
- separated short operations from active room sessions so manual discovery does not behave like a
  persistent foreground room;
- added Kotlin patch-regression checks and expanded deterministic core coverage to 72 tests.

## 1.0.0

Unified production foundation:

- consolidated application versioning at `1.0.0` / code 1;
- removed hosted release/store automation, host-specific, and automatic JDK-download configuration;
- standardized APK-only local signing and offline build commands;
- hardened content-addressed storage against same-size corruption;
- bounded playlist files, lines, entries, room commands, metadata, and inbound connections;
- tightened local invitation validation and transfer authorization consumption;
- removed pending admission state leaks and bounded cleanup retries;
- retained only local LAN/hotspot networking with no remote service integration;
- expanded offline core validation coverage;
- hardened navigation, invite failures, overlapping operation state, and cancellation handling;
- added connection and peer-transfer feedback plus confirmations for destructive actions;
- preserved file-picker intent across Activity recreation and improved long-track/unknown-duration
  playback UI;
