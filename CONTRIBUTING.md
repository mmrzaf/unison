# Contributing to Unison

Unison is intentionally small in product scope and serious about reliability. Contributions are
welcome when they make the nearby shared-listening experience clearer, more correct, more efficient,
or easier to maintain.

## Start here

Read:

1. [`README.md`](README.md)
2. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
3. [`docs/INVARIANTS.md`](docs/INVARIANTS.md)
4. [`docs/PROTOCOL.md`](docs/PROTOCOL.md) for wire changes
5. [`docs/TESTING.md`](docs/TESTING.md)

## Design expectations

- Prefer one owner for each lifecycle/state machine.
- Keep canonical room truth separate from transient local/network/transfer state.
- Make repeated desired state idempotent.
- Preserve useful transfer progress instead of creating churn.
- Keep user-visible semantics truthful: unavailable music prepares; READY music plays.
- Do not add compatibility/protocol negotiation, cloud services, analytics, accounts, or hidden hosted
  dependencies without an explicit product decision.
- Complexity is fine when it buys measurable correctness/recovery/efficiency. Avoid clever policy that
  lacks the information needed to make its decision.

## Before opening a pull request

Run at least:

```bash
./scripts/check-static.sh
./scripts/check-data.sh
python3 ./scripts/analyze-playback-log.py --self-test
python3 ./scripts/analyze-stability-log.py --self-test
```

If your local Gradle/Android toolchain is ready, also run:

```bash
./scripts/check-release-quality.sh
./gradlew --no-daemon testDebugUnitTest lintDebug :app:compileDebugAndroidTestKotlin
```

Add focused regression coverage for behavior changes. A small test that asserts an invariant is
preferred to a large test harness that duplicates production architecture.

## Protocol changes

Unison 1.2.0 uses strict Protocol 2. Do not increment the protocol merely because implementation code
changed. A new protocol version should correspond to a real incompatible wire-semantic improvement,
with docs/tests updated in the same change.

## Security

Never commit `keystore.properties`, keystores, signing secrets, local Android configuration, private
logs containing sensitive data, or generated release APKs. Use the repository release workflow's
GitHub secrets for signing.
