# Validation status

Validated in the local release environment on 2026-07-31:

- 352 deterministic debug unit tests in 71 suites: zero failures, errors, or skips;
- seven invite Compose instrumentation tests compiled into the Android test APK;
- debug and release Android lint: zero issues;
- debug APK and shrunk/resource-shrunk unsigned release APK assembly;
- release manifest metadata: `com.darius.unison`, version `1.0.0` (1), min SDK 30,
  target SDK 33, and `debuggable=false`;
- Spotless formatting checks for Kotlin, Kotlin DSL, XML, Markdown, scripts, and repository text;
- schema-1 export/query checks, static architecture/security checks, release-quality checks,
  playback-log analyzer self-test, and offline dependency readiness;
- normalized large-library search benchmark at 100,000 tracks below the 50 ms p95 gate.

The standalone dependency-free compiler harnesses could not execute because `kotlinc` is not
installed. Their scripts reported an explicit skip. Gradle's pinned Kotlin and Java compilers did
compile the production, unit-test, and Android-test sources successfully.

Android device validation is still required. This environment has no `adb`, emulator, or attached
physical device, so API 30–33 runtime behavior, notification controls, background transitions,
Bluetooth routing, multi-peer synchronization, soak behavior, and upgrade installation are not
claimed as passed. The requested `Pasted text(250).txt` reproduction log was also not present in the
repository, so the current-build strict log gate could only run its self-test.

## Commands executed

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew lintRelease
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
./scripts/check-static.sh
./scripts/check-core.sh
./scripts/check-data.sh
./scripts/check-risky-kotlin.sh
./scripts/check-player-kotlin.sh
./scripts/check-session-player-kotlin.sh
./scripts/check-network-lifecycle-kotlin.sh
./scripts/check-release-quality.sh
./scripts/verify-offline-ready.sh
./scripts/analyze-playback-log.py --self-test
./scripts/benchmark-library-search.py --max-p95-ms 50
```

## Required physical-device checks

Follow [Release qualification](RELEASE_QUALIFICATION.md) on Android 11, 12, and 13 before signing
and distribution. Retain the APK checksum and strict playback-log analysis with each device result.
