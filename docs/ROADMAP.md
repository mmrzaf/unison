# Roadmap

This roadmap describes direction, not promises or deadlines. Current candidate evidence belongs in
[`release-evidence/`](release-evidence/README.md); completed implementation work belongs in the
changelog rather than remaining as future roadmap work.

## 1.2.0-beta.8 qualification

The Beta 8 implementation baseline is feature-frozen. Remaining work is release qualification:

- run the authoritative GitHub verification workflow, including JVM/lint/build checks and API 33
  instrumentation;
- run the tag-triggered API 30/33/36 release instrumentation matrix;
- verify the approved release signing identity and exact signed release APK through the production
  release gate;
- exercise the required router Wi-Fi, LocalOnlyHotspot, private/no-Internet Wi-Fi, VPN allow/block,
  transfer-recovery, Bluetooth/audio-route, background/screen-off, and coordinator-loss scenarios on
  physical devices;
- run an exact-artifact smoke test and the selected soak scenarios against the GitHub-produced APK;
- record accepted known issues, reviewer/date, and final disposition in
  `docs/release-evidence/1.2.0-beta.8.md`.

No new product feature or broad architecture refactor should enter Beta 8 while those gates are open.

## 1.2 stable

- No open correctness, security, state-authority, storage-integrity, or release-signing blockers.
- Beta 8-and-later supported local data survives upgrades to the stable build.
- Full automated and physical qualification is attached to the exact candidate APK/source commit.
- Stable release is built from an immutable `v1.2.0` tag through the same production workflow used by
  prereleases.

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
