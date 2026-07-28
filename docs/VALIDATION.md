# Validation status

Current source audit validated on 2026-07-28:

- repository, manifest, version, local-runtime, and development-marker policy checks;
- fresh-release Room schema version 1;
- 72 deterministic Kotlin/JVM core tests with zero failures;
- dependency-free Kotlin patch-regression checks for delimiter balance, duplicate named arguments,
  icon imports, and manual-discovery invariants;
- focused Kotlin compilation of the changed NSD, control-connection/server/client, and artwork-cache
  sources using local stubs;
- clean overlay application and repeat execution of static, core, and data checks;
- protocol version 1 compatibility guard and content-addressed storage checks.

The full Android Gradle build, Compose compilation, Android Lint, and APK assembly were not rerun in
this review environment because Gradle 8.14.5, Android SDK 36, and Maven artifacts were not
available
in the local offline cache. Run the following on the provisioned Android build machine before
release:

```bash
./scripts/verify-offline-ready.sh
./scripts/build-debug.sh
./scripts/build-release.sh
```

## Remaining physical-device gates

A release candidate still requires the three-device matrix in [Testing](TESTING.md), especially
manual discovery, joining a third device during playback, long-running drift behavior, artwork after
transfer, hotspot task-removal behavior, reconnect failure cleanup, media controls, and signed
installation.

A signed release additionally requires the private local key used for previous installations.
