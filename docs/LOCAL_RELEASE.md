# Releasing Unison

Unison distributes signed APKs through GitHub Releases. Beta, RC, and stable versions use the same
production verification/signing pipeline.

## Source of truth

`gradle/libs.versions.toml` owns:

- `appVersionName`
- `appVersionCode`

Every installable update must use a strictly higher `versionCode`.

Version tags map directly to releases:

- `<semver>-beta.<N>` → `v<semver>-beta.<N>` → GitHub prerelease
- `<semver>-rc.<N>` → `v<semver>-rc.<N>` → GitHub prerelease
- `<semver>` → `v<semver>` → normal/latest GitHub release

Never retag a published version and never replace published assets. Fix forward with a new version.

## Release checklist

For each beta, RC, or stable release:

1. update `appVersionName` and increment `appVersionCode`;
2. add/update the matching `CHANGELOG.md` section;
3. create `docs/release-evidence/<version>.md` from `docs/release-evidence/TEMPLATE.md`;
4. run local verification;
5. commit the release source;
6. create and push the matching tag.

Example using the version currently declared in the repository:

```bash
VERSION="$(sed -n 's/^appVersionName = "\([^"]*\)"/\1/p' gradle/libs.versions.toml | head -1)"
git tag "v$VERSION"
git push origin "v$VERSION"
```

Publication is tag-triggered only.

After the tag is pushed, GitHub Actions performs the complete release gate: repository checks, full
release tests, API 30/33/36 instrumentation, signing, APK verification, source packaging, checksums,
provenance attestation, and immutable GitHub Release publication.

After publication, install/smoke-test the exact published APK and finish the concise per-version evidence
record. Do not duplicate generated checksums/certificate metadata unless needed for an investigation;
the release assets and Actions run already retain those facts.

## Repository secrets

The release workflow requires:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `ANDROID_SIGNING_CERT_SHA256` — the public SHA-256 fingerprint of the one approved release certificate

The build/sign job receives signing material without release-write permission. The publish job receives
release-write permission without signing material. `ANDROID_SIGNING_CERT_SHA256` is not secret; it pins
the intended certificate identity so accidentally replacing the keystore secret cannot silently change
who signs Unison.

## Local production-style build

Local release builds are useful before tagging:

```bash
./scripts/verify-offline-ready.sh
./scripts/build-release.sh
```

Expected output includes:

- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/release-SHA256SUMS.txt`

The local release gate verifies the signing certificate, debug/package/version/SDK metadata, alignment,
and APK size before reporting success.

A local build is not the published artifact. Human evidence that specifically claims to cover the
published release must use the exact GitHub-produced APK.

## Source packages

`scripts/archive.sh` is a working-tree backup helper and is not a public release source artifact.

Tagged releases use `scripts/package-source.sh`, which packages the exact tagged Git commit and validates
the archive before publication.

Example:

```bash
VERSION="$(sed -n 's/^appVersionName = "\([^"]*\)"/\1/p' gradle/libs.versions.toml | head -1)"
./scripts/package-source.sh "v$VERSION" dist
```

## Local signing setup

```bash
./scripts/create-release-key.sh
```

The script records the certificate SHA-256 fingerprint in local `keystore.properties`. Back up the
release key and passwords securely, and store the fingerprint independently. Configure the same
fingerprint as the repository secret `ANDROID_SIGNING_CERT_SHA256` before publishing. Every local and
GitHub production build verifies the final APK against that pinned identity.

## APK size gate

The local release script enforces the configured APK size limit. Override it only after intentionally
investigating and accepting an increase:

```bash
MAX_RELEASE_APK_BYTES=47185920 ./scripts/build-release.sh
python3 ./scripts/analyze-apk-size.py app/build/outputs/apk/release/app-release.apk
```
