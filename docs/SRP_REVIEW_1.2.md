# SRP-6a review for Unison 1.2

This note records the 1.2 release review of the four-digit room-code exchange implemented by
`PinPake`. It is intentionally narrow: it reviews the SRP-6a arithmetic and the way Unison wraps it;
it does not claim that JVM big-integer arithmetic is constant-time.

## What is fixed for 1.2

- The SRP arithmetic is isolated in `Srp6aCore` and is exercised against the published RFC 5054
  Appendix B vector. The test verifies `k`, `x`, `v`, `A`, `B`, `u`, and the client/server premaster
  secret using the vector's SHA-1/1024-bit parameters.
- Production `PinPake` uses the same arithmetic core with SHA-256 and a fixed 2048-bit safe-prime
  group: RFC 3526 MODP group 14 with generator 2. The group is compiled into the application and is
  never supplied or negotiated by a peer.
- Client and server reject public values that are zero modulo the modulus, and reject a zero
  scrambling parameter. Client/server private exponents are fresh non-zero 256-bit random values.
- The four-digit code is never transmitted. Unison-specific transcript binding, client/server HMAC
  proofs, HKDF session-key derivation, proof/session single use, admission timeouts, concurrency
  limits, and address/global failure throttling remain in `PinPake`/`AdmissionGuard` and retain their
  existing tests.
- Matching-code, wrong-code, replay/single-use, and public-value tests execute in the release
  hardening check after the arithmetic extraction.

The RFC 5054 vector is a conformance check for the standard SRP-6a equations, not a claim that Unison
uses RFC 5054's Appendix A group or its TLS ciphersuite. Unison's group and SHA-256 transcript wrapper
are a local protocol choice and remain wire-compatible with Protocol 2.

## Timing limitation

`java.math.BigInteger.modPow` is not specified as a constant-time primitive. Consequently Unison 1.2
does not claim constant-time SRP exponentiation. Constant-time comparison is still used for proof
verification, but that does not make the modular arithmetic constant-time.

For the 1.2 threat model this residual risk is accepted explicitly rather than addressed with a rushed
home-grown exponentiation implementation. Rooms are local/short-lived, the credential is ephemeral,
and online attempts are already bounded by `AdmissionGuard`; those controls reduce practical exposure
but do not erase a timing side channel.

There is no maintained constant-time SRP implementation in Unison's current dependency set that can be
substituted without a protocol/dependency change. Replacing SRP with a different PAKE would likewise
change the wire contract. If Unison's future threat model includes hostile low-noise measurement
environments, long-lived credentials, or Internet-reachable admission, the protocol should migrate to
a maintained, independently audited constant-time PAKE rather than growing custom cryptography here.

## 1.2 release decision

The 1.2 release gate requires:

1. `Srp6aCoreRfc5054Test` to match the RFC 5054 Appendix B arithmetic vector;
2. the existing `PinPakeTest` handshake/replay tests to remain green;
3. `AdmissionGuard` rate/concurrency protections to remain green;
4. no wire-format or key-derivation change from this refactor;
5. this timing limitation to remain documented in the security model.

Any change to the modulus, generator, digest, proof construction, transcript, or session-key derivation
must be treated as a cryptographic/protocol change and reviewed separately.
