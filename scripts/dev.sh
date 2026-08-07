#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_root_env

copy_env_examples() {
  local env_file
  for env_file in .env server/.env dashboard/.env admin/.env website/.env; do
    if [ ! -f "$ROOT_DIR/$env_file" ] && [ -f "$ROOT_DIR/$env_file.example" ]; then
      cp "$ROOT_DIR/$env_file.example" "$ROOT_DIR/$env_file"
      info "created $env_file"
    fi
  done
  load_root_env
}

ensure_server_secret() {
  require_file "$SERVER_ENV" "Run: just setup"
  if ! grep -Eq '^SECRET_KEY=.{32,}$' "$SERVER_ENV"; then
    local generated_secret
    generated_secret="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
    SERVER_ENV_PATH="$SERVER_ENV" GENERATED_SECRET="$generated_secret" python3 - <<'PY_SECRET'
import os
from pathlib import Path

path = Path(os.environ["SERVER_ENV_PATH"])
lines = path.read_text(encoding="utf-8").splitlines()
replacement = f'SECRET_KEY={os.environ["GENERATED_SECRET"]}'
for index, line in enumerate(lines):
    if line.startswith("SECRET_KEY="):
        lines[index] = replacement
        break
else:
    lines.append(replacement)
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY_SECRET
    info "generated server/.env SECRET_KEY"
  fi
}

bootstrap() {
  require_command python3
  require_command npm
  copy_env_examples
  ensure_server_secret

  if [ ! -x "$ROOT_DIR/server/.venv/bin/python" ]; then
    python3 -m venv "$ROOT_DIR/server/.venv"
  fi

  "$ROOT_DIR/server/.venv/bin/python" -m pip install --upgrade pip wheel
  (
    cd "$ROOT_DIR/server"
    .venv/bin/python -m pip install -e '.[dev]'
  )
  npm --prefix "$ROOT_DIR/dashboard" ci
  npm --prefix "$ROOT_DIR/admin" ci
  if [ -f "$ROOT_DIR/website/package.json" ]; then
    require_file "$ROOT_DIR/website/package-lock.json" \
      "Generate and commit the Website lockfile before setup"
    npm --prefix "$ROOT_DIR/website" ci
  fi
  info "dependencies installed"
}

services_status() {
  require_command systemctl
  local service
  for service in "$POSTGRES_SERVICE" "$REDIS_SERVICE"; do
    if systemctl is-active --quiet "$service"; then
      printf '%-24s active\n' "$service"
    else
      printf '%-24s inactive\n' "$service"
    fi
  done
}

services_start() {
  require_command systemctl
  require_command sudo
  local service
  for service in "$POSTGRES_SERVICE" "$REDIS_SERVICE"; do
    systemctl is-active --quiet "$service" || sudo systemctl start "$service"
  done
  services_status
}

validate_local_identifier() {
  local value="$1"
  local label="$2"
  [[ "$value" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "$label must be a PostgreSQL-safe identifier"
}

db_setup() {
  require_command sudo
  require_command psql
  require_command createdb
  load_server_env
  wait_for_postgres

  case "$POSTGRES_HOST" in localhost|127.0.0.1|::1) ;; *) die "db setup is local-only; host is $POSTGRES_HOST" ;; esac
  validate_local_identifier "$POSTGRES_USER" POSTGRES_USER
  validate_local_identifier "$POSTGRES_DB" POSTGRES_DB

  local escaped_password="${POSTGRES_PASSWORD//\'/\'\'}"
  sudo -u postgres psql -v ON_ERROR_STOP=1 postgres <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$POSTGRES_USER') THEN
    CREATE ROLE "$POSTGRES_USER" LOGIN PASSWORD '$escaped_password';
  ELSE
    ALTER ROLE "$POSTGRES_USER" WITH LOGIN PASSWORD '$escaped_password';
  END IF;
END
\$\$;
SQL

  if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$POSTGRES_DB'" postgres | grep -q 1; then
    sudo -u postgres createdb -O "$POSTGRES_USER" "$POSTGRES_DB"
  fi
  PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'select 1' >/dev/null
  info "database ready: $POSTGRES_DB"
}

migrate() {
  require_file "$ROOT_DIR/server/.venv/bin/alembic" "Run: just setup"
  load_server_env
  wait_for_postgres
  (cd "$ROOT_DIR/server" && .venv/bin/alembic upgrade head)
}

reset_db() {
  copy_env_examples
  require_file "$ROOT_DIR/server/.venv/bin/waiotech" "Run: just setup"
  (cd "$ROOT_DIR/server" && .venv/bin/waiotech dev reset-reference --yes)
}

doctor() {
  local status=0
  local command path
  for command in git just tmux python3 npm systemctl sudo pg_isready psql redis-cli; do
    if command -v "$command" >/dev/null 2>&1; then
      printf 'ok       command  %s\n' "$command"
    else
      printf 'missing  command  %s\n' "$command" >&2
      status=1
    fi
  done

  for path in server/.env server/.venv/bin/python server/.venv/bin/alembic dashboard/node_modules admin/node_modules; do
    if [ -e "$ROOT_DIR/$path" ]; then
      printf 'ok       path     %s\n' "$path"
    else
      printf 'missing  path     %s\n' "$path" >&2
      status=1
    fi
  done

  if [ -f "$ROOT_DIR/website/package.json" ]; then
    if [ -d "$ROOT_DIR/website/node_modules" ]; then
      printf 'ok       path     %s\n' "website/node_modules"
    else
      printf 'missing  path     %s\n' "website/node_modules" >&2
      status=1
    fi
  fi

  if [ -f "$SERVER_ENV" ]; then
    load_server_env
    if pg_isready -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -d postgres >/dev/null 2>&1; then
      printf 'ok       postgres %s:%s\n' "$POSTGRES_HOST" "$POSTGRES_PORT"
    else
      printf 'failed   postgres %s:%s\n' "$POSTGRES_HOST" "$POSTGRES_PORT" >&2
      status=1
    fi
    if redis-cli -u "$REDIS_URL" ping >/dev/null 2>&1; then
      printf 'ok       redis    %s:%s\n' "$REDIS_HOST" "$REDIS_PORT"
    else
      printf 'failed   redis    %s:%s\n' "$REDIS_HOST" "$REDIS_PORT" >&2
      status=1
    fi
  fi

  [ "$status" -eq 0 ] || die "development environment is not ready"
  info "development environment is ready"
}

pane_command() {
  printf 'printf "\\033]2;%s\\033\\\\"; %s' "$1" "$2"
}

start() {
  require_command tmux
  require_command npm
  require_file "$ROOT_DIR/server/.venv/bin/uvicorn" "Run: just setup"
  require_dir "$ROOT_DIR/dashboard/node_modules" "Run: just setup"
  require_dir "$ROOT_DIR/admin/node_modules" "Run: just setup"
  if [ -f "$ROOT_DIR/website/package.json" ]; then
    require_dir "$ROOT_DIR/website/node_modules" "Run: just setup"
  fi
  services_start
  load_server_env
  wait_for_postgres
  wait_for_redis
  migrate

  if tmux has-session -t "$SESSION" >/dev/null 2>&1; then
    info "session '$SESSION' is already running; use: just attach"
    return
  fi

  local dev_host="${WAIO_DEV_HOST:-127.0.0.1}"
  local bind_host="${WAIO_BIND_HOST:-127.0.0.1}"
  local backend_port="${WAIO_BACKEND_PORT:-8000}"
  local dashboard_port="${WAIO_DASHBOARD_PORT:-3000}"
  local admin_port="${WAIO_ADMIN_PORT:-3001}"
  local api_proxy="${WAIO_API_PROXY_TARGET:-http://127.0.0.1:$backend_port}"
  local logs="$ROOT_DIR/logs"
  mkdir -p "$logs"

  local env_cmd="set -a; . '$SERVER_ENV'; set +a"
  local backend="cd '$ROOT_DIR/server' && $env_cmd; exec .venv/bin/uvicorn app.main:app --host '$bind_host' --port '$backend_port' --reload"
  local dashboard="cd '$ROOT_DIR/dashboard' && exec env VITE_DEV_API_PROXY_TARGET='$api_proxy' npm run dev -- --host '$bind_host' --port '$dashboard_port'"
  local admin="cd '$ROOT_DIR/admin' && exec npm run dev -- --host '$bind_host' --port '$admin_port'"

  tmux new-session -d -s "$SESSION" -n backend -c "$ROOT_DIR" "$(pane_command backend "$backend 2>&1 | tee '$logs/backend.log'")"
  tmux new-window -t "$SESSION" -n dashboard -c "$ROOT_DIR" "$(pane_command dashboard "$dashboard")"
  tmux new-window -t "$SESSION" -n admin -c "$ROOT_DIR" "$(pane_command admin "$admin")"

  if [ "${RUN_WORKER:-false}" = "true" ]; then
    local worker="cd '$ROOT_DIR/server' && $env_cmd; exec .venv/bin/python -m arq app.workers.arq_worker.WorkerSettings"
    tmux new-window -t "$SESSION" -n worker -c "$ROOT_DIR" "$(pane_command worker "$worker 2>&1 | tee '$logs/worker.log'")"
  fi
  if [ -f "$ROOT_DIR/website/package.json" ]; then
    local website_port="${WAIO_WEBSITE_PORT:-3002}"
    local website="cd '$ROOT_DIR/website' && exec npm run dev -- --host '$bind_host' --port '$website_port'"
    tmux new-window -t "$SESSION" -n website -c "$ROOT_DIR" "$(pane_command website "$website")"
  fi

  tmux select-window -t "$SESSION:backend" >/dev/null
  printf 'API:       http://%s:%s\nDashboard: http://%s:%s\nAdmin:     http://%s:%s\n' \
    "$dev_host" "$backend_port" "$dev_host" "$dashboard_port" "$dev_host" "$admin_port"
  if [ -f "$ROOT_DIR/website/package.json" ]; then
    printf 'Website:   http://%s:%s\n' "$dev_host" "${WAIO_WEBSITE_PORT:-3002}"
  fi
  printf 'Attach:    just attach\n'
}

attach() {
  require_command tmux
  exec tmux attach-session -t "$SESSION"
}

stop() {
  require_command tmux
  tmux kill-session -t "$SESSION" 2>/dev/null || true
  info "application processes stopped"
}

status() {
  require_command tmux
  tmux list-windows -t "$SESSION" -F '#{window_index}: #{window_name} #{pane_current_command}' 2>/dev/null || printf 'not running\n'
}

setup() {
  bootstrap
  services_start
  db_setup
  migrate
  doctor
}

case "${1:-}" in
  setup) setup ;;
  doctor) doctor ;;
  migrate) migrate ;;
  reset-db) reset_db ;;
  start) start ;;
  attach) attach ;;
  stop) stop ;;
  status) status ;;
  *) die "Usage: $0 {setup|doctor|migrate|reset-db|start|attach|stop|status}" ;;
esac
