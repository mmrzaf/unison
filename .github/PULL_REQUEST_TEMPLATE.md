## What changed

<!-- Concise description of the user-visible or engineering change. -->

## Why

<!-- Problem being solved and why this approach is appropriate. -->

## Invariants / architecture

- [ ] I considered the single canonical room-writer invariant.
- [ ] Any asynchronous work that can mutate current room state carries/validates sufficient immutable provenance at consume time.
- [ ] Player mutations still flow through the canonical playback/PlayerExecutor path.
- [ ] Transfer/storage verification and lease semantics remain fail-closed.

Describe any invariant intentionally changed:

## Compatibility

- Protocol 2 changed? **No / Yes — explain**
- Database schema 1 changed? **No / Yes — explain**
- Android permissions/targetSdk behavior changed? **No / Yes — explain**
- Security/privacy behavior changed? **No / Yes — explain**

## Verification

Tests added/updated:

Commands/checks run:

Physical-device or emulator testing required/performed:

## Release impact

- [ ] No release-note impact
- [ ] Changelog/release notes updated
- [ ] Requires physical qualification or release-evidence update
