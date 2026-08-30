# ADR-004: Local-only networking

**Status:** Accepted

Unison intentionally has no cloud backend, account service, Internet relay, public-address join path,
or hosted API. Nearby peers communicate over a selected private LAN or Android LocalOnlyHotspot.
Changing this boundary is a product/security decision, not an incremental networking feature.
