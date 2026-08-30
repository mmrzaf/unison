#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command find "Install findutils"
require_command sort "Install coreutils"
require_command tar "Install tar"
require_command grep "Install grep"
require_command sha256sum "Install coreutils"

DEST="${UNISON_ARCHIVE_DIR:-${HOME}/tmp/backup/unison}"
mkdir -p "$DEST"
DEST="$(cd -- "$DEST" && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
ARCHIVE="$DEST/unison-source-$STAMP.tar.gz"
TMP_ARCHIVE="$DEST/.unison-source-$STAMP.tmp.$$.tar.gz"

trap 'rm -f "$TMP_ARCHIVE"' EXIT

# Working-tree backup only: archive the filesystem exactly as it exists, excluding local state,
# secrets, dependencies, caches, generated output, and nested archives. Public tagged releases use
# package-source.sh so the distributed source is derived from one immutable Git commit.
(
  cd "$ROOT_DIR"

  find . \
    \( -type d \( \
      -name .git -o \
      -name .agents -o \
      -name .codex -o \
      -name .claude -o \
      -name .snip -o \
      -name .idea -o \
      -name .vscode -o \
      -name node_modules -o \
      -name .venv -o \
      -name venv -o \
      -name env -o \
      -name '*.egg-info' -o \
      -name __pycache__ -o \
      -name .pytest_cache -o \
      -name .mypy_cache -o \
      -name .ruff_cache -o \
      -name .hypothesis -o \
      -name .tox -o \
      -name .nox -o \
      -name .npm -o \
      -name .yarn -o \
      -name .pnpm-store -o \
      -name .cache -o \
      -name .astro -o \
      -name .parcel-cache -o \
      -name .vite -o \
      -name .nyc_output -o \
      -name .turbo -o \
      -name .next -o \
      -name .nuxt -o \
      -name .svelte-kit -o \
      -name .docusaurus -o \
      -name dist -o \
      -name build -o \
      -name out -o \
      -name target -o \
      -name htmlcov -o \
      -name coverage -o \
      -name test-results -o \
      -name playwright-report -o \
      -name report-artifacts -o \
      -name logs -o \
      -path './.artifacts' -o \
      -path './backup' -o \
      -path './.gradle' -o \
      -path './.kotlin' -o \
      -path './captures' -o \
      -path './dist' -o \
      -path './keystore' \
    \) -prune \) -o \
    \( \( -type f -o -type l \) \
      ! -name '.DS_Store' \
      ! -name 'Thumbs.db' \
      ! -name '.env' \
      ! -name '.snip.yaml' \
      ! \( -name '.env.*' ! -name '*.example' \) \
      ! -name '*.pem' \
      ! -name '*.key' \
      ! -name '*.p8' \
      ! -name '*.p12' \
      ! -name '*.pfx' \
      ! -name '*.jks' \
      ! -name '*.keystore' \
      ! -name '*.apk' \
      ! -name '*.aab' \
      ! -name '*.pyc' \
      ! -name '*.pyo' \
      ! -name '*.log' \
      ! -name '*.pid' \
      ! -name '*.pid.lock' \
      ! -name '*.db' \
      ! -name '*.sqlite' \
      ! -name '*.sqlite3' \
      ! -name '*.sqlite-journal' \
      ! -name '*.tsbuildinfo' \
      ! -name '.eslintcache' \
      ! -name '.stylelintcache' \
      ! -name '.coverage' \
      ! -name '.coverage-*' \
      ! -name 'coverage.xml' \
      ! -name '*.lcov' \
      ! -name '*.tar' \
      ! -name '*.tar.gz' \
      ! -name '*.tgz' \
      ! -name '*.zip' \
      ! -name '*.7z' \
      ! -name '*.rar' \
      ! -path './local.properties' \
      ! -path './keystore.properties' \
      -print0 \
    \) \
    | sort -z \
    | tar -czf "$TMP_ARCHIVE" --null --no-recursion --files-from=-
)

# Defense in depth: fail closed if a future exclusion edit lets signing/local secrets through.
if tar -tzf "$TMP_ARCHIVE" | grep -Eq '(^|/)(keystore\.properties|local\.properties)$|(^|/)keystore/|\.(jks|keystore|p12|pfx|pem|key)$'; then
  printf 'Refusing to create archive: sensitive signing/local material was included\n' >&2
  exit 1
fi

mv -f "$TMP_ARCHIVE" "$ARCHIVE"
trap - EXIT

read -r ARCHIVE_SHA256 _ < <(sha256sum "$ARCHIVE")
printf 'Created working-tree backup %s\n' "$ARCHIVE"
printf 'SHA256 %s\n' "$ARCHIVE_SHA256"
printf 'Tagged public source packages are created by scripts/package-source.sh\n'
