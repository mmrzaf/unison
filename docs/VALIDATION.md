# Validation record

## Completed in this workspace

- Parsed all Android XML resources and the manifest.
- Validated workflow YAML and shell-script syntax.
- Verified that every third-party GitHub Action major-version reference used by the workflows exists.
- Ran repository policy checks for SDK levels, permission minimization, stale project names, and tracked signing material.
- Aligned Kotlin 2.3 with Android Gradle Plugin 8.13.2 and Java 17 application bytecode.
- Compiled the debug and release Android variants with JDK 21 and Android SDK 36.
- Executed all 43 Gradle JVM tests successfully.
- Ran Android Lint for debug and release with no errors. The remaining dependency upgrade notices
  are deliberate API-37/AGP-9.1 migration items, not compatible in-place upgrades.
- Built `app-debug.apk`.
- Ran the APK-only release script end to end with a temporary one-day key, produced a signed
  release APK, and verified its APK Signature Scheme v2 signature. The temporary key, signing
  properties, APK, and checksum were removed after validation.

Run the reproducible non-Android checks with:

```bash
./scripts/check-static.sh
./scripts/check-core.sh
```

## Not completed in this workspace

The permanent release signing key is intentionally unavailable, so the signed release APK and
its final signature verification are not claimed. Emulator and physical-device results are also
not claimed.

Before publication, run:

```bash
./gradlew --no-daemon testDebugUnitTest lintRelease assembleRelease
```

Then complete `docs/TESTING.md` and `docs/PLAY_STORE_RELEASE.md` with signed release artifacts.
