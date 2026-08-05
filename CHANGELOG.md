# Changelog

## 1.0.0

First production baseline.

- Two continuous application surfaces: Home and Room.
- Shared queue, room-wide transport controls, playlists, local imports, and nearby discovery.
- Canonical queue and playback revisions with stale-work rejection and automatic peer repair.
- Fresh reconnect challenge and transcript-bound authentication.
- Strict wire protocol 1 with no negotiation or fallback message shapes.
- Fresh Room schema 1 with no migrations or persisted room-session compatibility.
- SHA-256 content-addressed storage and authenticated resumable peer transfer.
- Bounded command, queue, metadata, socket, import, and transfer inputs.
- Media3 system controls routed through synchronized room commands.
- Local-only networking, no account, cloud service, telemetry, advertising, or billing.
