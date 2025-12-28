#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

MAX_ATTEMPTS="${MAX_ATTEMPTS:-5}"

if [ "$#" -eq 0 ]; then
  TASKS=(test)
else
  TASKS=("$@")
fi

if ! command -v codex >/dev/null 2>&1; then
  echo "Error: codex CLI not found. Install/enable Codex CLI so the 'codex' command is available." >&2
  exit 1
fi

run_sanity_check() {
  local sanity_script="$ROOT/scripts/ci/gradle_sanity.sh"
  local log_file="$ROOT/tmp/gradle_sanity.log"

  if [ ! -x "$sanity_script" ]; then
    echo "Sanity check script not found at $sanity_script (skipping)"
    return 0
  fi

  mkdir -p "$ROOT/tmp"

  echo "Running Gradle sanity check..."
  # Capture output so failures are actionable even with `set -e`.
  if "$sanity_script" >"$log_file" 2>&1; then
    echo "Sanity check passed"
    return 0
  fi

  echo "FAIL: Gradle configuration check failed"
  echo "Log: $log_file"
  echo "---- Last 120 lines ----"
  tail -n 120 "$log_file" || true
  echo "------------------------"
  echo "Tip: run './gradlew :androidApp:tasks --stacktrace --info' to see full details."
  return 1
}

# Run sanity check before starting test-fix loop
run_sanity_check

for ((attempt=1; attempt<=MAX_ATTEMPTS; attempt++)); do
  printf "\n=== Attempt %s of %s ===\n" "$attempt" "$MAX_ATTEMPTS"
  if "$ROOT/scripts/dev/run_tests.sh" "${TASKS[@]}"; then
    echo "✅ Tests passed on attempt $attempt."
    exit 0
  fi

  "$ROOT/scripts/dev/extract_failures.sh"
  FAILURE_PACKET="$(cat "$ROOT/tmp/test_failures.txt")"

  PROMPT=$(cat <<EOF
You are working in the Scanium repo. Fix the failing tests described below. Make minimal changes strictly related to the failures. Do not refactor unrelated code or reformat broadly.

After each fix, run ./scripts/dev/run_tests.sh test locally if possible. If you cannot execute commands, still make best-effort fixes and explain what you changed.

Here are the failures:
${FAILURE_PACKET}
EOF
)

  codex exec --cd "$ROOT" --full-auto "$PROMPT"

  # Re-run sanity check after codex fix attempt
  run_sanity_check

done

echo "❌ Tests still failing after $MAX_ATTEMPTS attempts. See tmp/test_failures.txt for the latest failure packet." >&2
exit 1
