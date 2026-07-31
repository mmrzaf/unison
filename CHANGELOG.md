# Changelog

## 1.0.0

Unified production foundation:

- consolidated application versioning at `1.0.0` / code 1;
- reset the development baseline to wire protocol 1 and Room database schema 1; future schema changes migrate from version 1;
- removed hosted release/store automation, host-specific, and automatic JDK-download configuration;
- standardized APK-only local signing and offline build commands;
- hardened content-addressed storage against same-size corruption;
- bounded playlist files, lines, entries, room commands, metadata, and inbound connections;
- tightened local invitation validation and transfer authorization consumption;
- removed pending admission state leaks and bounded cleanup retries;
- retained only local LAN/hotspot networking with no remote service integration;
- expanded offline core validation coverage;
- hardened navigation, room-code failures, overlapping operation state, and cancellation handling;
- added connection and peer-transfer feedback plus confirmations for destructive actions;
- preserved file-picker intent across Activity recreation and improved long-track/unknown-duration
  playback UI;
- made nearby-room discovery an explicit bounded scan with isolated listener/resolution lifecycles;
- added duplicate-installation identity recovery, bounded join retries, and terminal reconnect cleanup;
- removed music-thumbnail extraction, image caching, image workers, and image UI; Android system
  media controls use one fixed dark Unison brand tile for reliable contrast;
- consolidated playback recovery around persistent play intent, drift correction, seek cooldown, and
  emergency recovery;
- separated short operations from active room sessions and added source sanity checks plus expanded
  deterministic coverage.
