#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_ENV="${WAIO_ROOT_ENV:-$ROOT_DIR/.env}"
SERVER_ENV="${WAIO_SERVER_ENV:-$ROOT_DIR/server/.env}"
SESSION="${WAIO_DEV_SESSION:-waiotech}"
WAIT_TIMEOUT_SECONDS="${WAIO_WAIT_TIMEOUT_SECONDS:-30}"
POSTGRES_SERVICE="${WAIO_POSTGRES_SERVICE:-postgresql}"
REDIS_SERVICE="${WAIO_REDIS_SERVICE:-redis-server}"

info() { printf '==> %s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1${2:+. $2}"
}

require_file() {
  [ -f "$1" ] || die "Missing required file: $1${2:+. $2}"
}

require_dir() {
  [ -d "$1" ] || die "Missing required directory: $1${2:+. $2}"
}

load_root_env() {
  set -a
  if [ -f "$ROOT_DIR/.env.example" ]; then
    # shellcheck disable=SC1091
    . "$ROOT_DIR/.env.example"
  fi
  if [ -f "$ROOT_ENV" ]; then
    # shellcheck disable=SC1090
    . "$ROOT_ENV"
  fi
  set +a

  SESSION="${WAIO_DEV_SESSION:-waiotech}"
  WAIT_TIMEOUT_SECONDS="${WAIO_WAIT_TIMEOUT_SECONDS:-30}"
  POSTGRES_SERVICE="${WAIO_POSTGRES_SERVICE:-postgresql}"
  REDIS_SERVICE="${WAIO_REDIS_SERVICE:-redis-server}"
}

url_part() {
  python3 - "$1" "$2" <<'PY'
import sys
from urllib.parse import urlparse

url = urlparse(sys.argv[1])
parts = {
    "username": url.username or "",
    "password": url.password or "",
    "hostname": url.hostname or "",
    "port": str(url.port or ""),
    "database": url.path.lstrip("/").split("/", 1)[0],
}
print(parts[sys.argv[2]])
PY
}

load_server_env() {
  require_file "$SERVER_ENV" "Copy server/.env.example to server/.env"
  set -a
  # shellcheck disable=SC1090
  . "$SERVER_ENV"
  set +a

  [ -n "${DATABASE_URL:-}" ] || die "DATABASE_URL is required in $SERVER_ENV"
  [ -n "${REDIS_URL:-}" ] || die "REDIS_URL is required in $SERVER_ENV"

  POSTGRES_USER="${POSTGRES_USER:-$(url_part "$DATABASE_URL" username)}"
  POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-$(url_part "$DATABASE_URL" password)}"
  POSTGRES_HOST="${POSTGRES_HOST:-$(url_part "$DATABASE_URL" hostname)}"
  POSTGRES_PORT="${POSTGRES_PORT:-$(url_part "$DATABASE_URL" port)}"
  POSTGRES_DB="${POSTGRES_DB:-$(url_part "$DATABASE_URL" database)}"
  REDIS_HOST="${REDIS_HOST:-$(url_part "$REDIS_URL" hostname)}"
  REDIS_PORT="${REDIS_PORT:-$(url_part "$REDIS_URL" port)}"

  POSTGRES_PORT="${POSTGRES_PORT:-5432}"
  REDIS_PORT="${REDIS_PORT:-6379}"
  export POSTGRES_USER POSTGRES_PASSWORD POSTGRES_HOST POSTGRES_PORT POSTGRES_DB
  export REDIS_HOST REDIS_PORT
}

wait_for_postgres() {
  require_command pg_isready "Install postgresql-client"
  local elapsed=0
  until pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -d postgres >/dev/null 2>&1; do
    (( elapsed >= WAIT_TIMEOUT_SECONDS )) && die "PostgreSQL is not ready at $POSTGRES_HOST:$POSTGRES_PORT"
    sleep 1
    elapsed=$((elapsed + 1))
  done
}

wait_for_redis() {
  require_command redis-cli "Install redis-tools"
  local elapsed=0
  until redis-cli -u "$REDIS_URL" ping >/dev/null 2>&1; do
    (( elapsed >= WAIT_TIMEOUT_SECONDS )) && die "Redis is not ready at $REDIS_HOST:$REDIS_PORT"
    sleep 1
    elapsed=$((elapsed + 1))
  done
}

default_tag() {
  if [ -n "${1:-}" ]; then
    printf '%s' "$1"
  elif command -v git >/dev/null 2>&1 && git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git -C "$ROOT_DIR" rev-parse --short HEAD
  else
    date +%Y%m%d-%H%M%S
  fi
}
