# Development setup

The full repository/release shell toolchain is officially qualified on Linux. Android Studio and
Gradle development may work on macOS or Windows, but release scripts assume GNU/Linux utilities.

## Required tools

- Git
- Bash
- JDK 21 for running Gradle
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Android Platform Tools (`adb`)
- Python 3
- GNU coreutils/findutils, `grep`, `tar`, `gzip`, `curl`

The Android/Kotlin application bytecode targets Java 17 even though Gradle itself is qualified with
JDK 21.

## First-time bootstrap

Set `ANDROID_HOME` or `ANDROID_SDK_ROOT`, put `adb` on `PATH`, then run:

```bash
./scripts/bootstrap-dev.sh
```

Bootstrap is the intentionally network-enabled phase. It prepares the Gradle distribution/cache,
project dependencies, standalone Kotlin-check dependencies, and Android-test compilation inputs.
Afterward:

```bash
./scripts/verify-offline-ready.sh
```

should confirm the machine can execute the deterministic offline build path.

## Regional Maven mirrors

Public repository defaults use Google's/Maven Central/Gradle Plugin Portal repositories. Developers
who need the optional Iran mirror path can opt in locally without changing the repository:

```properties
# ~/.gradle/gradle.properties
useIranMirrors=true
```

or per invocation:

```bash
./gradlew -PuseIranMirrors=true ...
```

GitHub Actions intentionally use official repositories.

## Normal development

`develop` is the normal branch. Use debug APKs while iterating:

```bash
./scripts/build-debug.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Do not create tags for ordinary debug builds. Release-style signed APKs are associated with immutable
prerelease/stable tags such as `v1.2.0-alpha.1` or `v1.2.0`.

## Repository checks

Fast checks:

```bash
./scripts/check-static.sh
./scripts/check-data.sh
```

Full repository-quality gate:

```bash
./scripts/check-release-quality.sh
```

On an offline developer machine that cannot access the trusted repositories and does not yet have
the reviewed metadata, run the non-dependency portions explicitly:

```bash
UNISON_SKIP_LOCAL_DEPENDENCY_VERIFICATION=true ./scripts/check-release-quality.sh
```

This is a local development escape hatch only. GitHub Actions and release publication always require
`gradle/verification-metadata.xml`; it must be generated and reviewed on a trusted resolution path.

Android unit/lint/build checks:

```bash
./gradlew --no-daemon --stacktrace   testDebugUnitTest lintDebug lintRelease   assembleDebug assembleRelease   :app:compileDebugAndroidTestKotlin
```

With a device/emulator ready:

```bash
./gradlew --no-daemon --stacktrace connectedDebugAndroidTest
```

See [Testing](TESTING.md) for the behavioral and physical-device strategy.

## Dependency verification

The repository includes `scripts/refresh-dependency-verification.sh` for intentionally regenerating
Gradle dependency checksums. Run it only from a trusted dependency-resolution path, review every
metadata change, and commit checksum changes separately from unrelated features.

Do not accept a changed dependency checksum simply because Gradle generated it. After metadata is
present, validate its basic release contract with:

```bash
python3 ./scripts/check-dependency-verification.py
```

If the development machine cannot reach those official repositories, run the manually triggered
**Refresh dependency verification metadata** GitHub Actions workflow. It uploads the generated file
as an artifact; review its complete diff, then commit `gradle/verification-metadata.xml` before
merging or tagging.

## Formatting and tooling

Spotless defines the formatting baseline. Before enforcing or applying repository-wide formatting,
inspect the size of the proposed diff and keep formatting-only changes separate from behavior changes.

Shell/Python/workflow lint entry point:

```bash
./scripts/check-tooling.sh
```

It expects `shellcheck` and `actionlint`; CI installs a pinned actionlint release.

## Signing

Local signing setup is documented in [Local release](LOCAL_RELEASE.md). Never commit local signing
configuration or generated APKs.
