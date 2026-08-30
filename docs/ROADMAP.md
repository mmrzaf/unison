# Roadmap

This roadmap describes direction, not promises or deadlines.

## 1.2 alpha and beta stabilization

- Run the full API 30/33/36 instrumentation matrix and physical-device qualification.
- Collect focused OEM/network/audio-route feedback from `1.2.0-alpha.1` before beta.
- Fix release-blocking regressions without broadening Protocol 2 or Room schema 1.
- Improve public documentation and contributor issue triage from real prerelease questions.

## 1.2 stable

- No open correctness/security/state-authority blockers.
- Full physical qualification and soak evidence attached to the exact candidate APK/source commit.
- Stable release built from an immutable `v1.2.0` tag through the same production workflow used by
  prereleases.

## After 1.2

- Improve `RoomRuntime` testability/decomposition while preserving one canonical writer.
- Revisit targetSdk independently from room/playback stabilization.
- Continue Android/OEM compatibility work based on real devices.
- Consider stronger artifact provenance/attestation and deterministic release evidence automation.

## Explicitly out of scope without a product decision

- Cloud backend or Internet relay
- Accounts or social graph
- Analytics/advertising SDKs
- Streaming-service integrations
- Hidden hosted dependencies
- Protocol negotiation/compatibility layers without a concrete interoperability requirement
