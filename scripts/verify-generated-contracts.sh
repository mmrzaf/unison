#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_file "$ROOT_DIR/server/.venv/bin/python" "Run: just setup"
require_file "$ROOT_DIR/server/artifacts/openapi.json" "Run: just generate-api"

info "verifying server OpenAPI"
(
  cd "$ROOT_DIR/server"
  export SECRET_KEY="${SECRET_KEY:-$(.venv/bin/python -c 'import secrets; print(secrets.token_urlsafe(48))')}"
  export DATABASE_URL="${DATABASE_URL:-postgresql+asyncpg://waiotech:password@localhost:5432/waiotech}"
  .venv/bin/python -m app.commands.main openapi verify --output artifacts/openapi.json
)

info "verifying generated frontend clients"
(
  cd "$ROOT_DIR/dashboard"
  API_DOC_INPUT="$ROOT_DIR/server/artifacts/openapi.json" npm run verify:api
)
(
  cd "$ROOT_DIR/admin"
  API_DOC_INPUT="$ROOT_DIR/server/artifacts/openapi.json" npm run verify:api
)

info "verifying generated Permission and action contracts"
"$ROOT_DIR/server/.venv/bin/python" \
  "$ROOT_DIR/scripts/generate-permission-contract.py" --check
"$ROOT_DIR/server/.venv/bin/python" \
  "$ROOT_DIR/scripts/generate-action-contracts.py" --check
"$ROOT_DIR/server/.venv/bin/python" \
  "$ROOT_DIR/server/scripts/generate_taxonomy.py" --check
