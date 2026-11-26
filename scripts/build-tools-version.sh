#!/usr/bin/env bash
# Prints the Android build-tools version pinned in gradle/libs.versions.toml, the single source
# shared by Gradle, release verification, and CI SDK installation.
set -euo pipefail
cd "$(dirname "$0")/.."
version="$(sed -n 's/^buildTools = "\([0-9.]*\)"$/\1/p' gradle/libs.versions.toml)"
[[ -n "$version" ]] || { echo "buildTools is not pinned in gradle/libs.versions.toml" >&2; exit 1; }
printf '%s\n' "$version"
