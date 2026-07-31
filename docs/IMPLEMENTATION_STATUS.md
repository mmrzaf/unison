# Implementation status

The current engineering tree includes:

- content-addressed local audio, Paging-backed library, playlists, and bounded M3U import/export;
- Android NSD discovery, private IPv4/IPv6 sockets, LocalOnlyHotspot, cancellation-friendly joining,
  reconnect pacing, and screen-off lifecycle ownership;
- one explicit four-digit SRP-6a room-code flow, peer-equal room controls, room-secret reconnect, encrypted directional control frames,
  replay protection, and encrypted/authenticated resumable file transfer;
- a deterministic 1,000-item collaborative queue with batched add/remove, atomic clear, Play Next,
  persistent shuffle, repeat, compact search, and drag-edge auto-scroll;
- bounded prefetch of current plus three upcoming unique tracks, two concurrent incoming downloads,
  obsolete-transfer cancellation, and active-file leases;
- Media3 playback with one system player-control notification, no general notification permission flow, bounded drift correction, latest-intent
  scheduled transport, terminal preparation timeouts, local audio-safety suppression, and coordinator recovery;
- serialized canonical state, high-frequency playback telemetry outside the actor, batched reconnect
  availability, bounded snapshots, and deterministic teardown;
- text-only UI and track metadata with no music-thumbnail extraction, cache, worker, image
  dependency, or in-app image presentation; Android system media controls receive one fixed Unison
  brand tile.

The Kotlin/JVM test and compile gates, schema checks, source sanity checks, static invariants, and
large-library benchmark run without Android SDK downloads. Android SDK compilation, lint, APK
assembly, and physical-device lifecycle/audio testing remain mandatory release gates.
