# Roadmap

This roadmap describes direction, not promises or deadlines. Current candidate evidence belongs in
[`release-evidence/`](release-evidence/README.md); completed implementation work belongs in the
changelog rather than remaining as future roadmap work.

## 1.2.0 stabilization

1.2.0 is the first release anyone installs, so it is the baseline rather than an upgrade target.
The remaining work is stabilization, not features:

- keep the JVM multi-peer harness as the release gate and extend it with every field report;
- run the API 33 emulator suite locally and the API 30/33/36 matrix through the tag workflow;
- record the accepted limitation that hotspot, real-LAN, VPN, and Bluetooth paths are qualified on
  emulators only.

After 1.2.0, in order of value: qualify the radio paths on physical devices; move the player-facing
flows (rejoin, synchronization, queue refresh) into a playback director so the room actor can become
non-suspending (see ADR-007 for why this is not needed for stability today); then a `:core` JVM
module and a `RoomSession` decomposition.

## After 1.2

- Improve `RoomRuntime` testability/decomposition while preserving one canonical writer.
- Revisit targetSdk independently from room/playback stabilization.
- Continue Android/OEM compatibility work based on real devices.
- Consider stronger per-peer reconnect identity if the threat model expands to malicious admitted
  participants.

## Explicitly out of scope without a product decision

- Cloud backend or Internet relay
- Accounts or social graph
- Analytics/advertising SDKs
- Streaming-service integrations
- Hidden hosted dependencies
- Protocol negotiation/compatibility layers without a concrete interoperability requirement
