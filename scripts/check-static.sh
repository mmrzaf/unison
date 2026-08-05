#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 ./scripts/check-source-tree.py
python3 ./scripts/check-kotlin-source.py
echo STATIC_CHECK_OK
