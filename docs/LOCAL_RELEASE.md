# Local APK release

Unison produces a locally signed APK. It has no store publishing workflow and no Android App Bundle.

## GitHub release workflow

Pushing a matching version tag (for example, `v1.0.1`) runs the
[`Publish Unison release` workflow](../.github/workflows/release.yml). It builds and verifies signed
release and debug APKs, uploads them with `SHA256SUMS.txt`, and creates or updates the GitHub
release. A manual run is also available from the Actions tab.

Configure these repository secrets before using it:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded release keystore.
- `ANDROID_KEYSTORE_PASSWORD`: release keystore password.
- `ANDROID_KEY_ALIAS`: release signing-key alias.
- `ANDROID_KEY_PASSWORD`: release signing-key password.

The tag must exactly match `appVersionName` in `gradle/libs.versions.toml`.

## Signing setup

```bash
./scripts/create-release-key.sh
```

Back up the generated key and passwords offline. A later supported release must use the same key to
install over earlier signed releases such as 1.0.0.

## Build

```bash
./scripts/verify-offline-ready.sh
./scripts/build-release.sh
```

The release script runs the complete repository gate, Android unit tests, release lint, shrinking,
APK assembly, mandatory `apksigner` verification, APK-size analysis, and SHA-256 generation.

## Output

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/release-SHA256SUMS.txt`

Install 1.0.1 over the signed 1.0.0 release or cleanly; pre-release database and protocol states
are not migrated or decoded.

## APK size gate

The release script prints a ZIP-level APK breakdown and fails above 45 MiB by default. Override the
limit only for an investigated, intentional increase:

```bash
MAX_RELEASE_APK_BYTES=47185920 ./scripts/build-release.sh
python3 ./scripts/analyze-apk-size.py app/build/outputs/apk/release/app-release.apk
```

Treat the compressed release APK as the distribution-size metric. Android Settings may report a
larger installed footprint because DEX/resources are expanded or compiled on device.
