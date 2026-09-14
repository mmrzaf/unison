#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 ./scripts/check-source-tree.py
echo STATIC_CHECK_OK
