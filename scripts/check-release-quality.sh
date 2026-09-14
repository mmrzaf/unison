#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
  python3 ./scripts/check-dependency-verification.py
elif [[ "${UNISON_SKIP_LOCAL_DEPENDENCY_VERIFICATION:-}" == "true" ]]; then
  echo 'Skipping dependency-verification metadata check for this local offline run.' >&2
  echo 'GitHub Actions and release publication always require reviewed SHA-256 metadata.' >&2
else
  python3 ./scripts/check-dependency-verification.py
fi
python3 ./scripts/check-release-signing.py --self-test
python3 ./scripts/check-release-apk-metadata.py --self-test
./scripts/check-static.sh
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
