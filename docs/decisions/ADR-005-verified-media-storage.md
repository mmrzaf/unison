# ADR-005: Verified content-addressed media

**Status:** Accepted

Track identity is the SHA-256 digest of exact bytes. Imports/transfers become managed playable content
only after size/digest verification and atomic commit. Leases protect active readers; pending deletion
must eventually complete without deleting bytes underneath active use or valid republication.
