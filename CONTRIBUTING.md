# Contributing to Unison

Unison is intentionally small in product scope and serious about reliability. Contributions are
welcome when they make nearby shared listening clearer, more correct, more efficient, more portable
across Android devices, or easier to maintain.

## Start here

1. Read [`README.md`](README.md).
2. Set up the project with [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).
3. Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and
   [`docs/INVARIANTS.md`](docs/INVARIANTS.md).
4. Read [`docs/PROTOCOL.md`](docs/PROTOCOL.md) before touching wire behavior.
5. Read [`docs/TESTING.md`](docs/TESTING.md) before changing lifecycle, playback, networking,
   transfer, or storage behavior.
6. Check [`docs/ROADMAP.md`](docs/ROADMAP.md) and existing Issues before starting large work.
7. Repository maintainers should also review [`docs/GITHUB_SETUP.md`](docs/GITHUB_SETUP.md) before
   changing public project/release settings.

Contributions target the `develop` branch. Release tags are immutable snapshots; do not base normal
feature work on a release tag.

## Product and design expectations

- Preserve one canonical room mutation owner.
- Any asynchronous work that can mutate current room state must carry enough immutable provenance to
  prove that it still belongs to the currently authoritative room/session/connection when consumed.
- Keep canonical room truth separate from transient local/network/transfer state.
- Make repeated desired state idempotent.
- Preserve useful transfer progress instead of creating churn.
- Keep user-visible semantics truthful: unavailable music prepares; READY music plays.
- Do not add cloud services, accounts, analytics, advertising, Internet relay, hidden hosted
  dependencies, protocol negotiation, or broad compatibility layers without an explicit product
  decision.
- Complexity is acceptable when it buys measurable correctness/recovery/efficiency. Avoid policy that
  lacks the information needed to make its decision.

## Development and testing

On a new Linux workstation, begin with:

```bash
./scripts/bootstrap-dev.sh
```

Before opening a pull request, run at least:

```bash
./scripts/check-static.sh
./scripts/check-data.sh
python3 ./scripts/analyze-playback-log.py --self-test
python3 ./scripts/analyze-stability-log.py --self-test
```

For behavior changes, also run the relevant focused tests plus:

```bash
./scripts/check-release-quality.sh
./gradlew --no-daemon testDebugUnitTest lintDebug :app:compileDebugAndroidTestKotlin
```

Android/framework behavior should be exercised with instrumentation rather than inferred from JVM
stubs. `connectedDebugAndroidTest` is the normal local entry point when an emulator/device is ready.
The release workflow executes instrumentation on API 30, 33, and 36.

Add focused regression coverage for behavior changes. A small test that asserts an invariant is
preferred to a large test harness that duplicates production architecture.

## Pull requests

Keep a pull request focused enough that its title can describe one coherent change. The PR template
asks for:

- what and why;
- affected invariants;
- protocol/schema/security/privacy impact;
- tests/checks run;
- whether physical-device qualification is required.

Do not mix repository-wide formatting, dependency upgrades, feature work, and correctness fixes in one
change unless they are inseparable.

## Protocol and schema changes

The 1.2 release line uses strict Protocol 2 and Room schema 1. Do not increment the protocol merely
because implementation code changed. A new protocol version must correspond to a real incompatible
wire-semantic improvement, with docs/tests updated in the same change. Database migration support is
also a deliberate product/release decision, not an incidental Room change.

## Security

Do not report vulnerabilities in a public Issue. Follow [`.github/SECURITY.md`](.github/SECURITY.md).
Never commit signing credentials, local Android configuration, private logs containing sensitive data,
or generated release APKs.

## Community

By participating, you agree to follow [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md). Questions and design
ideas belong in GitHub Discussions when they are not yet actionable bugs or implementation proposals.
