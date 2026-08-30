# Diagnostic analyzer regression fixtures

These are deliberately small, sanitized traces derived from failure shapes observed during the 1.2.0
physical-device qualification run. They contain no raw room IDs, addresses, paths, credentials, or
media metadata.

- `good-phase4.ndjson`: expected healthy Phase 1–3 lifecycle: physical boundary handoff, split content
  readiness, bounded audio-focus rejoin, on-time playback, and clean teardown.
- `bad-natural-end-resurrection.ndjson`: Media3 reports `END_OF_MEDIA_ITEM`, but no physical boundary
  reaches canonical ownership before a `WRONG_PLAY_STATE` repair restarts the finished item.
- `bad-empty-readiness-cohort.ndjson`: connected room members repeatedly project an empty legacy
  readiness/playback cohort, reproducing the deadlock where verified content became unplayable.
- `bad-system-policy-inhibition.ndjson`: an unexplained local callback is promoted to generic
  `SYSTEM_POLICY` output inhibition.
- `bad-unavailable-command-spam.ndjson`: user playback commands repeatedly reach runtime even though
  the target requires preparation.
- `bad-auto-rejoin-stuck.ndjson`: transient audio focus clears, but the participant stays inhibited.
- `bad-unlocked-clock-projection.ndjson`: a participant computes a canonical position in an unlocked
  clock domain.
- `bad-teardown.ndjson`: room teardown leaves active transfers/jobs.
- `bad-arrival-late.ndjson`: a command is already materially late before PlayerExecutor receives it.
- `bad-executor-late.ndjson`: PlayerExecutor itself misses a materially scheduled execution time.
