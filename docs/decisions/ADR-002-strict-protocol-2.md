# ADR-002: Strict Protocol 2 for the 1.2 line

**Status:** Accepted

The 1.2 release line has one incompatible-wire contract: Protocol 2. There is no negotiation, fallback
shape, or compatibility decoder. A future protocol number is justified only by a real incompatible
wire-semantic improvement, not by internal refactoring.
