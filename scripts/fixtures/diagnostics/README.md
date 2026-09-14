# Diagnostic analyzer regression fixtures

These are deliberately small, sanitized traces derived from failure shapes observed during the 1.2.0
physical-device qualification run. They contain no raw room IDs, addresses, paths, credentials, or
media metadata.

- `good-room-lifecycle.ndjson`: expected healthy room lifecycle: physical boundary handoff, split content
  readiness, bounded audio-focus rejoin, on-time playback, and clean teardown.
- `bad-natural-end-resurrection.ndjson`: Media3 reports `END_OF_MEDIA_ITEM`, but no physical boundary
  reaches canonical ownership before a `WRONG_PLAY_STATE` repair restarts the finished item.
- `bad-empty-readiness-set.ndjson`: connected room members repeatedly project an empty
  content-readiness set, reproducing the deadlock where verified content became unplayable.
- `bad-system-policy-inhibition.ndjson`: an unexplained local callback is promoted to generic
  `SYSTEM_POLICY` output inhibition.
- `bad-unavailable-command-spam.ndjson`: current transport rejection events repeatedly report playback
  commands whose target still requires preparation.
- `bad-auto-rejoin-stuck.ndjson`: transient audio focus clears, but the participant stays inhibited.
- `bad-unlocked-clock-projection.ndjson`: a participant computes a canonical position in an unlocked
  clock domain.
- `bad-teardown.ndjson`: room teardown leaves active transfers/jobs.
- `bad-arrival-late.ndjson`: a command is already materially late before PlayerExecutor receives it.
- `bad-executor-late.ndjson`: PlayerExecutor itself misses a materially scheduled execution time.
- `bad-actor-handler-cancellation.ndjson`: a room-event handler throws `CancellationException` while
  the persistent actor owner is still active; this must be classified as a release failure, not normal
  owner cancellation.

- `good-transfer-policy-blocked-bounded.ndjson`: deterministic pre-connect Android policy denial. One route attempt fails with `POLICY_BLOCKED`, the route is suspended once, and no automatic
  retry storm follows.
- `bad-transfer-preconnect-retry-storm.ndjson`: sanitized bind-failure shape where socket provisioning
  fails before `transfer.download.connecting`; operation IDs prove four distinct attempts
  and repeated retries must still be rejected by the stability analyzer.
