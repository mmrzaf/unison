# Validation status

Validated on 2026-07-27:

- repository, manifest, version, local-runtime, and development-marker policy checks;
- fresh-release Room schema version 1;
- 65 Android/JVM unit tests with zero failures, errors, or skips;
- strict debug and release Android Lint with zero issues (warnings are fatal);
- debug APK assembly;
- release Kotlin compilation, Compose mapping, R8 shrinking, resource optimization, and unsigned APK assembly;
- GitHub Actions workflow syntax and semantics checked with actionlint 1.7.12;
- protocol version 1 compatibility guard;
- concurrent content-addressed import commit stress coverage;
- session reset coverage for stale room state and independent hotspot retention.

Dependency-currency lint is intentionally excluded because Kotlin is pinned to 2.3.21 until the R8
generation bundled with AGP 8.13 supports Kotlin 2.4 metadata cleanly. Core and Lifecycle remain on
their last API-36-compatible releases until a deliberate API 37/AGP 9 migration. All actionable
source, API, deprecation, accessibility, correctness, and performance checks remain enabled.

## Remaining physical-device gates

No Android device or emulator was available in the review environment. A release candidate still
requires the device matrix in [Testing](TESTING.md), including two-device sync, discovery, hotspot,
QR join, transfer interruption/resume, media controls, background playback, and signed installation.

The standalone `check-core.sh` harness also reports `CORE_CHECK_SKIPPED` when `kotlinc` is absent;
the same production areas are covered by the passing Gradle unit suite in this environment.

A signed release additionally requires the private local key:

```bash
./scripts/verify-offline-ready.sh
./scripts/build-release.sh
```
