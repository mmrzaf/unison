# ADR-001: One canonical room actor

**Status:** Accepted

Canonical room/session mutations are serialized through one `RoomRuntime` actor. Async work may
prepare results elsewhere, but any work that can mutate current room state must carry immutable
provenance and prove authority when the actor consumes it.

This avoids multiple competing lifecycle/state-machine owners. Decomposition may extract policies and
services, but must not introduce a second canonical writer.
