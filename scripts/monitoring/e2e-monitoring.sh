***REMOVED***!/bin/bash
***REMOVED*** E2E Monitoring Tests Runner
***REMOVED*** Usage: bash e2e-monitoring.sh [--skip-remote]
***REMOVED***
***REMOVED*** Runs all monitoring proof scripts and reports PASS/FAIL summary.
***REMOVED*** Exit code 0 if all tests pass, non-zero if any fail.
***REMOVED***
***REMOVED*** Options:
***REMOVED***   --skip-remote: Skip remote access test (for local-only testing)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKIP_REMOTE=false

***REMOVED*** Parse arguments
for arg in "$@"; do
  case $arg in
    --skip-remote)
      SKIP_REMOTE=true
      shift
      ;;
  esac
done

echo "======================================================================="
echo " Scanium Monitoring E2E Tests"
echo "======================================================================="
echo ""

PASSED=0
FAILED=0
TESTS=()

***REMOVED*** Helper function to run a test
run_test() {
  local test_name="$1"
  local test_script="$2"
  shift 2
  local test_args=("$@")

  echo "-----------------------------------------------------------------------"
  echo "TEST: $test_name"
  echo "-----------------------------------------------------------------------"

  if bash "$SCRIPT_DIR/$test_script" "${test_args[@]}"; then
    echo "✅ PASSED: $test_name"
    PASSED=$((PASSED + 1))
    TESTS+=("✅ $test_name")
  else
    echo "❌ FAILED: $test_name"
    FAILED=$((FAILED + 1))
    TESTS+=("❌ $test_name")
  fi
  echo ""
}

***REMOVED*** Run all proof scripts
run_test "Metrics Pipeline (Backend → Mimir)" "prove-metrics.sh"
run_test "Logs Pipeline (Backend → Loki)" "prove-logs.sh"
run_test "Mobile Dashboard Wiring" "prove-mobile-dashboard-wiring.sh"
run_test "Grafana Dashboards Provisioned" "prove-grafana-dashboards.sh"

if [ "$SKIP_REMOTE" = false ]; then
  run_test "Remote Access (Cloudflared Tunnel)" "prove-remote-access.sh"
else
  echo "-----------------------------------------------------------------------"
  echo "SKIPPED: Remote Access (--skip-remote flag)"
  echo "-----------------------------------------------------------------------"
  TESTS+=("⏭️  Remote Access (skipped)")
  echo ""
fi

***REMOVED*** Summary
echo "======================================================================="
echo " E2E Test Summary"
echo "======================================================================="

for test in "${TESTS[@]}"; do
  echo "$test"
done

echo ""
echo "Total: $((PASSED + FAILED)) tests"
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo ""

if [ "$FAILED" -gt 0 ]; then
  echo "❌ E2E TESTS FAILED"
  echo "======================================================================="
  exit 1
else
  echo "✅ ALL E2E TESTS PASSED"
  echo "======================================================================="
  exit 0
fi
