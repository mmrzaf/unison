#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command curl "Install curl"
require_command tar "Install tar"
require_command sha256sum "Install coreutils"

ACTIONLINT_VERSION="1.7.12"
ACTIONLINT_SHA256="8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8"
DEST="${1:-$HOME/.local/bin}"
mkdir -p "$DEST"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
URL="https://github.com/rhysd/actionlint/releases/download/v${ACTIONLINT_VERSION}/actionlint_${ACTIONLINT_VERSION}_linux_amd64.tar.gz"
curl --fail --location --proto '=https' --tlsv1.2 "$URL" -o "$TMP/actionlint.tar.gz"
printf '%s  %s\n' "$ACTIONLINT_SHA256" "$TMP/actionlint.tar.gz" | sha256sum -c -
tar -xzf "$TMP/actionlint.tar.gz" -C "$TMP" actionlint
install -m 0755 "$TMP/actionlint" "$DEST/actionlint"
printf 'Installed actionlint %s to %s\n' "$ACTIONLINT_VERSION" "$DEST/actionlint"
