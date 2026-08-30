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
./scripts/check-static.sh
./scripts/check-data.sh
python3 ./scripts/analyze-playback-log.py --self-test
python3 ./scripts/analyze-stability-log.py --self-test
python3 ./scripts/check-log-analyzer-fixtures.py
python3 ./scripts/benchmark-library-search.py --sizes 100000 --iterations 8 --max-p95-ms 50

# These checks compile focused Kotlin components with the repository-pinned toolchain. They require
# the Gradle distribution and dependency cache verified by verify-offline-ready.sh.
./scripts/check-hardening-kotlin.sh
./scripts/check-core.sh
./scripts/check-diagnostics.sh
./scripts/check-risky-kotlin.sh
./scripts/check-player-kotlin.sh
./scripts/check-session-player-kotlin.sh
./scripts/check-network-lifecycle-kotlin.sh

echo RELEASE_QUALITY_OK
