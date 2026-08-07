# Structured diagnostics

Unison has one diagnostic pipeline for application events, room troubleshooting, device captures, and
release soak analysis. There is no parallel legacy text logger.

## Record format

Durable logs are newline-delimited JSON (`NDJSON`): one complete JSON object per line. The record
shape follows the stable OpenTelemetry Log Data Model concepts without including an OpenTelemetry
SDK or any network exporter:

- RFC 3339 UTC `timestamp` and `observedTimestamp`;
- `severityText` and OpenTelemetry-aligned `severityNumber` (`DEBUG=5`, `INFO=9`, `WARN=13`,
  `ERROR=17`);
- stable dotted `eventName`;
- optional human `body`;
- `resource.service.name=unison`;
- `instrumentationScope.name` for the emitting component;
- typed `attributes` for machine-readable event data;
- optional structured exception type/message.

`schemaVersion` is currently `1`. Event names and attribute keys are lowercase, stable identifiers;
dynamic values belong in attributes rather than being encoded into the event name.

## Categories and event names

Every event belongs to one bounded category: `app`, `room`, `network`, `discovery`, `playback`,
`sync`, `transfer`, `storage`, or `security`.

Event names describe what happened, for example:

- `room.transport.status`;
- `playback.command.executing`;
- `playback.play.requested`;
- `playback.play.started`;
- `playback.state.changed`;
- `sync.speed_adjustment`;
- `sync.hard_seek`;
- `network.socket.route_selected`;
- `transfer.track.failed`.

Important values such as command ID, queue item ID, phase, latency, drift, peer ID, retry number, and
duration are attributes. This lets the room console and qualification scripts filter the same data
without parsing prose.

## Room scope and privacy

Starting a room creates a random local diagnostic session ID. Every event emitted while that room is
active receives the session ID, a SHA-256-derived short hash of the room ID, and the local room role.
The raw room ID and network endpoint addresses are not persisted. Peer IDs used for correlation are
truncated before logging.

The logger sanitizes message/attribute strings and automatically redacts keys that indicate secrets,
tokens, passphrases, PINs, passwords, credentials, authorization proofs, or key material. Content URIs
and application/storage paths are redacted. Exceptions store only sanitized type and message; stack
traces are not persisted.

Diagnostics never leave the device automatically and are not analytics or telemetry. The user can
view the current room's retained records and explicitly copy the filtered view as NDJSON.

## Performance and bounds

Logging is never allowed to block playback or networking:

- one dedicated writer thread owns disk persistence;
- its pending queue is bounded to 1,024 events and discards the oldest pending diagnostic under
  overload;
- the in-memory viewer ring is bounded to 5,000 recent events;
- sync diagnostics are sampled/rate-limited before entering the logger;
- normal INFO/DEBUG records are buffered and flushed in batches;
- WARN/ERROR records flush promptly;
- files rotate at 2 MiB with two retained rotations (about 6 MiB maximum durable diagnostics);
- rotation, serialization, and file I/O never run on the playback/room actor path.

A `room.session.ended` record includes pending/dropped diagnostic counts. Release soak analysis treats
dropped diagnostics as a failed observability gate because it indicates either excessive logging or
resource pressure.

## Room log console

`Room actions → Room logs` opens a live room-scoped console. It subscribes to the diagnostic revision
flow only while visible, so normal room UI does not observe the log list.

The console provides:

- newest-first lazy rendering;
- All / Info+ / Warn+ / Errors severity filters;
- category filters;
- text search over event name, component, body, and attributes;
- expandable structured attributes and exception summary;
- bounded clipboard export of the current filtered view as chronological NDJSON.

## Device capture and analysis

For a connected Android device:

```bash
./scripts/capture-playback-log.sh unison-playback.ndjson
```

`DiagnosticLog` writes the same schema to Logcat. To stay below Android's per-entry transport limit,
Logcat records bound long body/error strings and, only for unusually large events, drop non-critical
attributes while preserving room, command, transport, playback, and sync analyzer fields. The
app-private NDJSON file and room console retain the complete sanitized event. The capture script uses
raw Logcat format so every captured line remains valid NDJSON.

Analyze a capture:

```bash
./scripts/analyze-playback-log.py unison-playback.ndjson --strict
```

The analyzer understands schema 1 directly. It checks canonical item storms, queue/player switching,
transport/preparation completion, scheduled-command lateness, playback failures, transition circuit
breakers, structured-log validity, sync correction statistics, and diagnostic drops.
