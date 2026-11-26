# Local data contract

Unison stores one local data format, **v1**. Unison 1.2.0 is the first release that installs it, so
there is no earlier format, migration, compatibility layer, or reset path.

## What v1 consists of

All of it lives in the app's private storage:

- `databases/unison.db`: the Room database at schema version 1. It holds `tracks`, `track_sources`,
  `playlists`, and `playlist_entries`. Track rows store normalized title/artist/album sort keys and the
  RECENT timestamp, so library paging uses composite SQLite indexes instead of expression sorts.
  `track_sources` records the retention (kept or temporary) of each track's managed copy.
- `files/tracks/<2 chars>/<SHA-256 track id>`: the content-addressed managed audio store, with its
  staging, resumable partials, and `.delete-pending` markers (see [Architecture](ARCHITECTURE.md#storage)).
- `files/datastore/unison_settings.preferences_pb`: local peer identity, display name, retention
  policy, and onboarding state. A corrupt file is replaced with defaults (a fresh peer identity) and
  that replacement is recorded in diagnostics.
- `files/diagnostics/`: bounded structured diagnostics (see [Logging](LOGGING.md)).

Active room state is memory-only and never persisted.

## Changing it

The exported schema in `app/schemas/` is the reviewed contract, and `scripts/check-data.sh` verifies
its identity hash and query plans. Any change to v1 after 1.2.0 must be a deliberately versioned format
change with a migration. Do not silently rename the database, reset data, add destructive Room
fallbacks, or introduce a second local-data namespace.

## Wire protocol

Local data and networking are independent contracts. Unison speaks exactly one strict wire contract,
[Protocol 1](PROTOCOL.md), with no negotiation, fallback decoder, or alternate wire mode.
