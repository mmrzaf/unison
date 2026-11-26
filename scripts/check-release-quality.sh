#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

python3 ./scripts/check-dependency-verification.py
python3 ./scripts/check-release-signing.py --self-test
python3 ./scripts/check-release-apk-metadata.py --self-test
python3 ./scripts/check-source-tree.py
./scripts/check-data.sh
python3 ./scripts/analyze-playback-log.py --self-test
python3 ./scripts/analyze-stability-log.py --self-test
python3 ./scripts/check-log-analyzer-fixtures.py
python3 ./scripts/benchmark-library-search.py \
  --sizes 100000 \
  --iterations 8 \
  --max-browse-p95-ms 10 \
  --max-search-p95-ms 50

# The network-lifecycle harness exercises Android routing/NSD/hotspot lifecycle seams that are not
# represented by ordinary JVM tests. Full compiler/unit/lint/build coverage runs through Gradle in CI.
./scripts/check-network-lifecycle-kotlin.sh

echo RELEASE_QUALITY_OK
