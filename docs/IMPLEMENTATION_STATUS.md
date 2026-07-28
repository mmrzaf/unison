# Implementation status

The current Unison engineering tree includes:

- local library, playlists, cancellable/observable M3U import, explicit ambiguous-match review, bounded M3U export, and content-addressed managed audio;
- local room discovery, QR/direct join, and LocalOnlyHotspot support;
- coordinator-local invite PINs, reconnect proof, directional authenticated control framing, and
  encrypted room-secret delivery;
- deterministic collaborative queue and scheduled transport state;
- resumable, authorized peer-to-peer transfer with SHA-256 verification, explicit cancellation, and
  active-file leases;
- Media3 playback, wake mode, media-session controls, affine clock synchronization, filtered drift
  correction, discontinuity recovery, and coordinator recovery;
- serialized room-event ownership, validated bounded snapshots, separated control-traffic queues,
  replay/term/sequence protection, and focused peer/routing/admission/role components and legacy snapshot cleanup;
- one injected Room database for UI/runtime/workers, Paging persistence, temporary retention,
  operation-scoped SAF grants, and lease-aware local cleanup;
- Android 13+ notification-permission handling and understandable denial behavior;
- independently published structural, playback, and transfer state;
- Compose UI split by library, playlist, room, and shared-component boundaries, with a focused
  flow-composition ViewModel and delegated room/playlist/import actions;
- background/cached QR generation plus bounded artwork memory, disk usage, and retry work.

The finalized deterministic core and static suites pass in the source-review environment. Full
Android compilation and physical-device lifecycle tests require a provisioned Android/Gradle build
machine.
