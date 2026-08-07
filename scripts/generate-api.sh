#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_file "$ROOT_DIR/server/.venv/bin/python" "Run: just setup"

mkdir -p "$ROOT_DIR/.artifacts" "$ROOT_DIR/server/artifacts"
OPENAPI="$ROOT_DIR/.artifacts/openapi.json"

info "exporting OpenAPI"
(
  cd "$ROOT_DIR/server"
  export SECRET_KEY="${SECRET_KEY:-$(.venv/bin/python -c 'import secrets; print(secrets.token_urlsafe(48))')}"
  export DATABASE_URL="${DATABASE_URL:-postgresql+asyncpg://waiotech:password@localhost:5432/waiotech}"
  .venv/bin/python -m app.commands.main openapi export --output "$OPENAPI"
)
cp "$OPENAPI" "$ROOT_DIR/server/artifacts/openapi.json"

info "generating frontend clients"
(
  cd "$ROOT_DIR/dashboard"
  API_DOC_INPUT="$OPENAPI" npm run generate:api
)
(
  cd "$ROOT_DIR/admin"
  API_DOC_INPUT="$OPENAPI" npm run generate:api
)
"$ROOT_DIR/server/.venv/bin/python" "$ROOT_DIR/scripts/generate-permission-contract.py"
"$ROOT_DIR/server/.venv/bin/python" "$ROOT_DIR/scripts/generate-action-contracts.py"
"$ROOT_DIR/server/.venv/bin/python" "$ROOT_DIR/server/scripts/generate_taxonomy.py"

info "verifying generated contracts"
"$ROOT_DIR/scripts/verify-generated-contracts.sh"
