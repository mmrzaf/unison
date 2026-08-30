#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"
cd "$ROOT_DIR"

cat >&2 <<'EOF'
This intentionally rewrites Gradle dependency-verification metadata.
Run it only on a trusted network/resolution path, review every checksum change, and commit the result separately.
EOF

env -u USE_IRAN_MIRRORS ./gradlew --no-daemon --refresh-dependencies -PuseIranMirrors=false \
  --write-verification-metadata sha256 \
  resolveVerificationDependencies

printf 'Updated %s\n' "$ROOT_DIR/gradle/verification-metadata.xml"
