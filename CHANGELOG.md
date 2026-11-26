# Changelog

## 1.2.0

Unison: local-first, synchronized music playback across nearby Android phones.

### Features

- Create a room on one phone; nearby phones discover it and join with a four-digit room code.
- Every listener plays a verified local copy of the same track; Unison never streams audio between
  phones. Missing tracks transfer directly over the local network and are SHA-256 verified before they
  become playable.
- Shared room queue and transport (play, pause, seek, skip, repeat, shuffle, move, remove) decided
  by the coordinator and applied at a synchronized moment on every phone.
- Continuous drift correction against the coordinator's clock, with automatic recovery after a
  temporary connectivity loss and a visible manual rejoin when the system takes audio away for good.
- Local library, playlists, M3U export, share-sheet and file-picker imports, and a configurable
  retention policy for received tracks.
- Local-only structured diagnostics; no accounts, cloud service, analytics, advertising or relay.

### Interface and accessibility

- Dialog fields take focus when opened and submit from the keyboard's Done key; blank names are
  rejected.
- Buttons and checkbox rows expose their roles to screen readers, drag handles meet the 48 dp touch
  target, and the room code is announced digit by digit.

### Reliability and stability

- One room actor owns all room state. Playback preparation, media readiness, transfer demands and
  recovery run as actor turns; the player effect queue only executes already-decided player work, and
  the clock mapping is published as an immutable snapshot. Locks remain only at genuinely
  concurrent boundaries (admission, the ingress fence, the session-job registry), and a source-tree
  check rejects new locks or concurrent maps in the room package.
- The peer transport is a port (`PeerNetwork`) with a socket implementation; the whole room runtime
  runs unchanged on a JVM multi-peer harness with virtual time.
- Leaving a room flushes the leave frame before the connection closes, so participants never
  reconnect to a room that has ended.
- Shutdown stops the actor before tearing down the state it owns; a sticky service restart
  re-evaluates the idle stop.
- The player waits for a scheduled command with a millisecond-rounded delay instead of spinning.
- Cache cleanup pages through the library, NSD resolution is serialized for the platform's single
  in-flight resolve, and a corrupt settings store is reported in diagnostics instead of silently
  regenerating the device identity.

### Compatibility

- Wire protocol: Protocol 1. Local data: format v1 (Room schema 1). These are fixed by this release.
- Android 11 (API 30) or newer; target API 33.
- Qualified on emulators and the JVM multi-peer harness. Hotspot, real-LAN, VPN and Bluetooth paths
  have not been exercised on physical devices for this release.
