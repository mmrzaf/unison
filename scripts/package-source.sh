#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command git "Git is required for deterministic release source packaging"
require_command gzip "Install gzip"
require_command sha256sum "Install coreutils"
require_command python3 "Install Python 3"

REF="${1:-HEAD}"
DEST="${2:-$ROOT_DIR/dist}"
mkdir -p "$DEST"
DEST="$(cd -- "$DEST" && pwd)"

VERSION_NAME="$(sed -n 's/^appVersionName = "\([^"]*\)"/\1/p' "$ROOT_DIR/gradle/libs.versions.toml" | head -1)"
[[ -n "$VERSION_NAME" ]] || die "Could not read appVersionName"
EXPECTED_TAG="v$VERSION_NAME"
REF_COMMIT="$(git -C "$ROOT_DIR" rev-parse "$REF^{commit}")"
TAG_COMMIT="$(git -C "$ROOT_DIR" rev-parse "refs/tags/$EXPECTED_TAG^{commit}")"
[[ "$REF_COMMIT" == "$TAG_COMMIT" ]] || die "$REF does not resolve to required tag $EXPECTED_TAG"

ARCHIVE="$DEST/Unison-${VERSION_NAME}-source.tar.gz"
TMP="$ARCHIVE.tmp.$$"
trap 'rm -f "$TMP"' EXIT

# git archive fixes the source set to one immutable commit; gzip -n removes gzip timestamp/name data.
git -C "$ROOT_DIR" archive --format=tar --prefix="unison-${VERSION_NAME}/" "$TAG_COMMIT" \
  | gzip -n -9 > "$TMP"
python3 "$ROOT_DIR/scripts/check-source-package.py" "$TMP"
mv -f "$TMP" "$ARCHIVE"
trap - EXIT
sha256sum "$ARCHIVE"
