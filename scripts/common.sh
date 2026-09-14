#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export ROOT_DIR
info() { printf '==> %s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1${2:+. $2}"
}
configured_gradle_user_home() {
  if [[ -n "${UNISON_GRADLE_USER_HOME:-}" ]]; then
    printf '%s\n' "$UNISON_GRADLE_USER_HOME"
  elif [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
    printf '%s\n' "${GRADLE_USER_HOME:-$HOME/.gradle}"
  else
    printf '%s\n' "$ROOT_DIR/.gradle-user-home"
  fi
}

run_gradle() {
  local gradle_user_home
  gradle_user_home="$(configured_gradle_user_home)"
  mkdir -p "$gradle_user_home"
  GRADLE_USER_HOME="$gradle_user_home" "$ROOT_DIR/gradlew" "$@"
}
