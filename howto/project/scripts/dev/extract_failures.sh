#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

LOG_PATH="$ROOT/tmp/gradle_test.log"
OUT_PATH="$ROOT/tmp/test_failures.txt"

if [ ! -f "$LOG_PATH" ]; then
  echo "Error: $LOG_PATH not found. Run ./scripts/dev/run_tests.sh first." >&2
  exit 1
fi

{
  echo "=== Failed Gradle tasks ==="
  rg -n "Task .* FAILED|FAILED" "$LOG_PATH" || true
  echo
  echo "=== Failure details (matched excerpts) ==="
  rg -n "There were failing tests|FAILURE: Build failed with an exception|Caused by:|AssertionError|assertion|expected:|at .*\([^)]+:[0-9]+\)|org.junit|kotlin.test|junit" "$LOG_PATH" || true
  echo
  echo "=== Failure tail (last 400 lines) ==="
  tail -n 400 "$LOG_PATH"
} > "$OUT_PATH"

if ! rg -q "FAILED|FAILURE: Build failed|There were failing tests|Caused by:" "$OUT_PATH"; then
  {
    echo
    echo "=== Fallback tail (last 300 lines) ==="
    tail -n 300 "$LOG_PATH"
  } >> "$OUT_PATH"
fi

echo "Failure packet written to $OUT_PATH"
