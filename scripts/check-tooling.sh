#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"
cd "$ROOT_DIR"

require_command shellcheck "Install ShellCheck"
require_command actionlint "Install actionlint"
require_command python3 "Install Python 3"

mapfile -d '' shell_files < <(find scripts -type f -name '*.sh' -print0 | sort -z)
for file in "${shell_files[@]}"; do
  bash -n "$file"
done
# Source paths are resolved from BASH_SOURCE at runtime, so ShellCheck cannot follow them
# statically; the scripts are still syntax-checked and their shared helper is included above.
shellcheck -e SC1091 "${shell_files[@]}"
actionlint .github/workflows/*.yml
python3 -m compileall -q scripts
printf 'Repository shell, workflow, and Python tooling checks passed.\n'
