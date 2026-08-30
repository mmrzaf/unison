# Security model

## Boundary

Unison protects room traffic between nearby devices on a private Wi-Fi network or Android
LocalOnlyHotspot. It provides authentication, confidentiality, integrity, replay resistance, bounded
resource use, and verified file identity. It does not protect a room from a device that has already
joined and is itself compromised.

## Admission

The room credential is exactly four digits and remains on the coordinator device. First admission
uses SRP-6a so the code and a reusable offline-testable password proof are not transmitted. The SRP
arithmetic is shared through `Srp6aCore` and release-tested against the RFC 5054 Appendix B vector;
production keeps its existing fixed RFC 3526 group-14 / SHA-256 Protocol-2 parameters. JVM
`BigInteger.modPow` is not specified as constant-time, so 1.2 explicitly accepts that residual timing
limitation within the local, short-lived-room threat model rather than introducing unreviewed custom
modular arithmetic. See [SRP_REVIEW_1.2.md](SRP_REVIEW_1.2.md). Authentication attempts are
rate-limited, concurrency-limited, and timed out.

Reconnect uses a fresh coordinator challenge. Proof and session-key derivation bind room ID, peer ID,
client nonce, and server nonce to the active random room secret. Connection replacement happens only
after successful proof verification. Final admission also captures the active room ID and process-local
session generation; the room actor rejects the connection before any peer-state mutation if either is
stale when the accepted socket reaches canonical state.

## Control traffic

- Protocol 2 uses strict decoding and exact version equality.
- Direction-specific AES-GCM keys protect every control frame.
- Headers and envelope context are authenticated.
- Message UUIDs and sequence checks reject replay and reordering outside allowed semantics.
- Remote envelopes retain their source `ControlConnection`; superseded sockets are rejected before
  replay state, liveness, or protocol dispatch can change.
- Room, term, sender, peer endpoint, size, timestamp, and metadata constraints fail closed.
- The coordinator serializes canonical mutations; peers cannot directly mutate another peer's player.

## File transfer

- Transfer requests require a short-lived, destination-bound, single-use authorization.
- A fresh nonce challenge proves possession of the authorization secret.
- The transfer header and every chunk are AES-GCM authenticated.
- Resume offset, expected size, record sequence, and final SHA-256 are verified.
- Files remain staged and invisible until complete verification and atomic commit.
- Active playback, queue, import, cleanup, and transfer work use leases or operation locks.
- Upload authorization never opens a repository path that can disappear before streaming: readability
  is resolved/repaired first, then `TRANSFER_UPLOAD` is acquired atomically against managed deletion.
- Transfer progress/completion/failure are session-generation fenced at their actual state-mutation
  boundary; callbacks from a dead room cannot publish readiness or failure into its successor.

## Storage

Managed audio is stored in application-private content-addressed paths. Track identity is the
lowercase SHA-256 digest of exact bytes. Imports and downloads write to staging files, verify size and
digest, then atomically commit. Android backup is disabled.

A logical delete never removes bytes underneath an active reader. If a managed file is leased, deletion
is recorded as a durable app-private pending-delete marker and completes when the last lease is released.
Publication uses a separate non-replacement-protecting lease so a valid new database/source reference can
be established safely while verified corruption repair remains possible. Startup cleanup cross-checks
pending markers against current managed database references before deleting recovered bytes.

The Room database is schema 1. It contains library and playlist data only. Active room secrets,
control keys, transfer keys, peer sockets, reconnect state, and canonical room sessions are memory
only and are cleared when the session ends.

## Network and platform controls

- No remote hostname or HTTP endpoint exists in production source.
- Public IP addresses and DNS joins are rejected.
- For authenticated participants, the control socket's observed remote address is the canonical peer
  host. Endpoint announcements may update the transfer port but cannot redirect the peer to an unrelated
  private-LAN address.
- Cleartext Android network traffic is disabled.
- Media-session controllers must be trusted by Android before receiving transport capability.
- Exported components are limited to the launcher/share activity and MediaSessionService contract.
- Diagnostics use one bounded structured NDJSON sink. Secret/token/PIN/password/proof/key-material
  attributes, content URIs, and private storage paths are redacted before persistence. Raw room IDs,
  file contents, reusable credentials, cryptographic keys, and stack traces are not logged.
- Diagnostic files rotate at about 6 MiB total, are app-private, and have no automatic network
  exporter. Room-log copy is an explicit user action.

## Out of scope

- malicious code running on an admitted phone;
- a user intentionally sharing the four-digit code;
- operating-system compromise;
- traffic analysis such as observing that local devices are communicating;
- availability against a local attacker able to jam or disconnect the network.
