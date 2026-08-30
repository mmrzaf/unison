# Local APK release

Unison distributes a signed APK rather than an Android App Bundle. Normal development uses debug
builds from `develop`; production-style prerelease/stable artifacts are tied to immutable version tags.

## Version and tag model

`gradle/libs.versions.toml` is the source of truth for `appVersionName` and `appVersionCode`.

Examples:

- `1.2.0-alpha.1` → `v1.2.0-alpha.1` → GitHub prerelease
- `1.2.0-rc.1` → `v1.2.0-rc.1` → GitHub prerelease
- `1.2.0` → `v1.2.0` → normal/latest release

Every installable update must use a strictly higher `versionCode`. Never retag or replace assets for a
published version; make a new alpha/beta/RC/patch version instead.

The 1.2 release line keeps Protocol 2 and Room database schema 1. Signed 1.1.x/1.0.x installs may
upgrade into 1.2, and published 1.2 alphas/betas/RCs are expected to upgrade forward into later 1.2 builds
while those compatibility contracts remain unchanged.

## GitHub release workflow

Pushing the exact version tag runs [`Publish Unison release`](../.github/workflows/release.yml).
Publication is tag-triggered only; there is no manual workflow path that can assign an existing tag
name to a different checked-out commit.

The workflow:

1. runs the reusable repository/unit/lint/build verification gate;
2. executes Android instrumentation on API 30, 33, and 36;
3. proves the tag matches `appVersionName` and points at the exact workflow commit;
4. builds and verifies one signed release APK;
5. creates a deterministic source package from the tagged commit;
6. writes public release provenance/checksums and a GitHub/Sigstore build-provenance attestation;
7. transfers those immutable artifacts to a separate publish job;
8. refuses to overwrite an existing GitHub Release or its assets;
9. publishes `-alpha.*`/`-beta.*`/`-rc.*` as prereleases and stable versions as normal/latest releases.

Debug APKs remain CI artifacts and are not public release downloads.

Configure these repository secrets before using the workflow:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The build/sign job receives signing secrets but no GitHub release-write permission. The publish job has
release-write permission but never receives the signing material.

## Local signing setup

```bash
./scripts/create-release-key.sh
```

Back up the release key and passwords securely. Supported updates must use the signing identity expected
by already-installed production builds.

## Local production-style build

After first-time bootstrap and offline-readiness verification:

```bash
./scripts/verify-offline-ready.sh
./scripts/build-release.sh
```

The local release script runs the repository gate, Android unit/lint/build work, shrinking, APK signing
verification, size analysis, and SHA-256 generation according to the checked-in release contract.

Expected local output:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/release-SHA256SUMS.txt`

A locally built release APK is useful for pre-tag smoke testing, but a public alpha/beta/stable decision must
also test the exact APK produced by the GitHub tag workflow.

## Source packages

`scripts/archive.sh` is a safe working-tree backup helper. It is deliberately not the public release
source artifact because it packages the current uncommitted filesystem state.

Tagged releases use:

```bash
./scripts/package-source.sh v1.2.0-alpha.1 dist
```

That path packages the exact tagged Git commit, uses deterministic gzip metadata, validates the archive
without requiring `.git`, and produces `Unison-<version>-source.tar.gz`.

## APK size gate

The local release script prints a ZIP-level APK breakdown and fails above 45 MiB by default. Override
only after investigating and intentionally accepting the increase:

```bash
MAX_RELEASE_APK_BYTES=47185920 ./scripts/build-release.sh
python3 ./scripts/analyze-apk-size.py app/build/outputs/apk/release/app-release.apk
```

Treat the compressed release APK as the distribution-size metric; installed Android storage can be
larger because code/resources may be expanded or compiled on-device.
