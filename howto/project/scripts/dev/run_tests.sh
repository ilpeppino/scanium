#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

mkdir -p "$ROOT/tmp"
LOG_PATH="$ROOT/tmp/gradle_test.log"

if [ "$#" -eq 0 ]; then
  set -- test
fi

set +e
"$ROOT/scripts/dev/gradle17.sh" "$@" 2>&1 | tee "$LOG_PATH"
STATUS=${PIPESTATUS[0]}
set -e

if [ "$STATUS" -eq 0 ]; then
  echo "✅ Tests passed. Log: $LOG_PATH"
else
  echo "❌ Tests failed. Log: $LOG_PATH"
fi

exit "$STATUS"
