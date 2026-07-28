# Local APK release

Unison is distributed as a locally signed APK. It has no app-store publishing workflow and produces
no Android App Bundle.

## One-time signing setup

```bash
./scripts/create-release-key.sh
```

Back up the generated key and passwords offline. The same key is required for every upgrade
installed over an existing copy.

## Build

Preload the Android SDK, Gradle distribution, and dependency cache on the build machine, then
disconnect it if desired:

```bash
./scripts/verify-offline-ready.sh
./scripts/build-release.sh
```

The script runs repository checks, JVM/core tests, database schema checks, Android unit tests,
release lint, R8/resource shrinking, APK signing verification when `apksigner` is available, and
SHA-256 generation.

## Output

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/release-SHA256SUMS.txt`

Verify the checksum before moving the APK to another device.
