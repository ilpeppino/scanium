#!/bin/bash
# scripts/ci/local-ci.sh - Local CI runner (quota-free alternative to GitHub Actions)
#
# Usage:
#   ./scripts/ci/local-ci.sh doctor    # Check prerequisites
#   ./scripts/ci/local-ci.sh coverage  # Run coverage checks
#   ./scripts/ci/local-ci.sh security  # Run CVE security scan
#   ./scripts/ci/local-ci.sh all       # Run both
#   ./scripts/ci/local-ci.sh help      # Show help
#
# Options:
#   --act          Run via 'act' (GitHub Actions emulator) instead of direct tools
#   --no-color     Disable colored output
#
# Mirrors:
#   - .github/workflows/coverage.yml
#   - .github/workflows/security-cve-scan.yml

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="$REPO_ROOT/tmp/ci"

# Colors (can be disabled with --no-color)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

# Parse options
USE_ACT=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --act)
            USE_ACT=true
            shift
            ;;
        --no-color)
            RED='' GREEN='' YELLOW='' BLUE='' BOLD='' NC=''
            shift
            ;;
        -*)
            echo "Unknown option: $1"
            exit 1
            ;;
        *)
            break
            ;;
    esac
done

COMMAND="${1:-help}"

show_help() {
    cat <<EOF
${BOLD}Local CI Runner${NC} - Run GitHub Actions workflows locally

${BLUE}Usage:${NC}
  $0 <command> [options]

${BLUE}Commands:${NC}
  doctor    Check prerequisites (Java, Gradle, jq, etc.)
  coverage  Run coverage checks (mirrors coverage.yml)
  security  Run CVE security scan (mirrors security-cve-scan.yml)
  all       Run both coverage and security
  help      Show this help message

${BLUE}Options:${NC}
  --act       Run via 'act' GitHub Actions emulator (requires Docker)
  --no-color  Disable colored output

${BLUE}Examples:${NC}
  $0 doctor              # Verify prerequisites
  $0 coverage            # Run tests and coverage verification
  $0 security            # Run OWASP Dependency-Check CVE scan
  $0 all                 # Run full CI suite
  $0 --act coverage      # Run coverage via act (Docker)

${BLUE}Output:${NC}
  Coverage reports:  $OUTPUT_DIR/coverage/
  Security reports:  $OUTPUT_DIR/security/
  Logs:              $OUTPUT_DIR/*/

${BLUE}Workflow Mapping:${NC}
  coverage.yml:
    - ./gradlew clean test koverVerify
    - ./gradlew jacocoTestReport
    - Reports: Kover HTML, Jacoco HTML

  security-cve-scan.yml:
    - ./gradlew dependencyCheckAnalyze --no-daemon
    - Fail if HIGH/CRITICAL CVEs found
    - Reports: SARIF, HTML, JSON

${BLUE}Environment Variables:${NC}
  NVD_API_KEY                     Faster NVD database downloads for security scan
  DEPENDENCY_CHECK_NVD_API_KEY    Alternative name for NVD API key

EOF
}

run_doctor() {
    echo -e "${BLUE}${BOLD}=== Running Prerequisites Check ===${NC}"
    echo ""
    exec "$SCRIPT_DIR/doctor.sh"
}

run_coverage() {
    if [[ "$USE_ACT" == true ]]; then
        run_coverage_act
    else
        run_coverage_direct
    fi
}

run_coverage_direct() {
    echo -e "${BLUE}${BOLD}=== Running Coverage (Direct Mode) ===${NC}"
    echo ""
    "$SCRIPT_DIR/run_coverage.sh"
}

run_coverage_act() {
    echo -e "${BLUE}${BOLD}=== Running Coverage (Act Mode) ===${NC}"
    echo ""

    if ! command -v act &>/dev/null; then
        echo -e "${RED}Error: 'act' not installed${NC}"
        echo "Install: brew install act (macOS) or see https://nektosact.com"
        exit 1
    fi

    if ! docker info &>/dev/null; then
        echo -e "${RED}Error: Docker not running${NC}"
        exit 1
    fi

    cd "$REPO_ROOT"
    act -W .github/workflows/coverage.yml --artifact-server-path "$OUTPUT_DIR/coverage" -j coverage
}

run_security() {
    if [[ "$USE_ACT" == true ]]; then
        run_security_act
    else
        run_security_direct
    fi
}

run_security_direct() {
    echo -e "${BLUE}${BOLD}=== Running Security Scan (Direct Mode) ===${NC}"
    echo ""
    "$SCRIPT_DIR/run_security.sh"
}

run_security_act() {
    echo -e "${BLUE}${BOLD}=== Running Security Scan (Act Mode) ===${NC}"
    echo ""

    if ! command -v act &>/dev/null; then
        echo -e "${RED}Error: 'act' not installed${NC}"
        echo "Install: brew install act (macOS) or see https://nektosact.com"
        exit 1
    fi

    if ! docker info &>/dev/null; then
        echo -e "${RED}Error: Docker not running${NC}"
        exit 1
    fi

    cd "$REPO_ROOT"
    # Pass NVD API key if available
    local act_env=""
    if [[ -n "${NVD_API_KEY:-}" ]]; then
        act_env="-s NVD_API_KEY=$NVD_API_KEY"
    fi

    act -W .github/workflows/security-cve-scan.yml --artifact-server-path "$OUTPUT_DIR/security" -j dependency-check $act_env
}

run_all() {
    local coverage_result=0
    local security_result=0

    echo -e "${BLUE}${BOLD}=== Running Full CI Suite ===${NC}"
    echo ""

    # Run coverage
    echo -e "${BLUE}--- Coverage ---${NC}"
    if run_coverage; then
        coverage_result=0
    else
        coverage_result=1
    fi
    echo ""

    # Run security
    echo -e "${BLUE}--- Security ---${NC}"
    if run_security; then
        security_result=0
    else
        security_result=1
    fi
    echo ""

    # Summary
    echo -e "${BLUE}${BOLD}=== Final Summary ===${NC}"
    echo ""
    if [[ $coverage_result -eq 0 ]]; then
        echo -e "Coverage: ${GREEN}PASS${NC}"
    else
        echo -e "Coverage: ${RED}FAIL${NC}"
    fi

    if [[ $security_result -eq 0 ]]; then
        echo -e "Security: ${GREEN}PASS${NC}"
    else
        echo -e "Security: ${RED}FAIL${NC}"
    fi

    echo ""
    echo "Reports: $OUTPUT_DIR/"
    echo ""

    # Exit with failure if either failed
    if [[ $coverage_result -ne 0 || $security_result -ne 0 ]]; then
        exit 1
    fi
}

# Main dispatch
case "$COMMAND" in
    doctor)
        run_doctor
        ;;
    coverage)
        run_coverage
        ;;
    security)
        run_security
        ;;
    all)
        run_all
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        echo -e "${RED}Unknown command: $COMMAND${NC}"
        echo ""
        show_help
        exit 1
        ;;
esac
