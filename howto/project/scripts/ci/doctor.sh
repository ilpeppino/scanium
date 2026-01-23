#!/bin/bash
# scripts/ci/doctor.sh - Verify prerequisites for local CI

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

echo -e "${BLUE}=== Local CI Prerequisites Check ===${NC}"
echo "Repo root: $REPO_ROOT"
echo ""

ERRORS=0
WARNINGS=0

check_ok() { echo -e "${GREEN}[OK]${NC} $1"; }
check_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; WARNINGS=$((WARNINGS + 1)); }
check_fail() { echo -e "${RED}[FAIL]${NC} $1"; ERRORS=$((ERRORS + 1)); }
check_info() { echo -e "${BLUE}[INFO]${NC} $1"; }

# 1. Java/JDK 17
echo -e "${BLUE}--- Java ---${NC}"
if command -v java &>/dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -1)
    echo "  $JAVA_VER"

    if java -version 2>&1 | grep -q 'version "17'; then
        check_ok "JDK 17 detected"
    elif java -version 2>&1 | grep -q 'version "21'; then
        check_warn "JDK 21 detected; workflows use JDK 17"
        echo "       macOS fix: export JAVA_HOME=\$(/usr/libexec/java_home -v 17)"
    else
        check_warn "JDK version may not match CI (expects 17)"
    fi

    # Check for JDK 17 availability on macOS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if /usr/libexec/java_home -v 17 &>/dev/null; then
            JDK17_HOME=$(/usr/libexec/java_home -v 17)
            check_info "JDK 17 available at: $JDK17_HOME"
        else
            check_warn "JDK 17 not installed; install with: brew install temurin@17"
        fi
    fi
else
    check_fail "Java not found"
    echo "       Install: brew install temurin@17 (macOS) or apt install openjdk-17-jdk (Linux)"
fi
echo ""

# 2. Gradle wrapper
echo -e "${BLUE}--- Gradle ---${NC}"
if [[ -x "$REPO_ROOT/gradlew" ]]; then
    check_ok "Gradle wrapper found and executable"
    # Don't run ./gradlew --version here as it downloads dependencies
else
    if [[ -f "$REPO_ROOT/gradlew" ]]; then
        check_warn "Gradle wrapper exists but not executable"
        echo "       Fix: chmod +x $REPO_ROOT/gradlew"
    else
        check_fail "Gradle wrapper not found"
    fi
fi
echo ""

# 3. jq (for security scan JSON parsing)
echo -e "${BLUE}--- jq (JSON parser) ---${NC}"
if command -v jq &>/dev/null; then
    JQ_VER=$(jq --version 2>/dev/null || echo "unknown")
    check_ok "jq installed ($JQ_VER)"
else
    check_warn "jq not found (needed for security scan vulnerability counting)"
    echo "       Install: brew install jq (macOS) or apt install jq (Linux)"
fi
echo ""

# 4. Docker (optional for act mode)
echo -e "${BLUE}--- Docker (optional) ---${NC}"
if command -v docker &>/dev/null; then
    if docker info &>/dev/null 2>&1; then
        DOCKER_VER=$(docker --version)
        check_ok "Docker available ($DOCKER_VER)"
    else
        check_warn "Docker installed but daemon not running"
    fi
else
    check_info "Docker not installed (optional, needed for --act mode)"
fi
echo ""

# 5. act (optional)
echo -e "${BLUE}--- act (optional GitHub Actions runner) ---${NC}"
if command -v act &>/dev/null; then
    ACT_VER=$(act --version 2>/dev/null || echo "unknown")
    check_ok "act installed ($ACT_VER)"
else
    check_info "act not installed (optional)"
    echo "       Install: brew install act (macOS) or see https://nektosact.com"
fi
echo ""

# 6. Environment variables
echo -e "${BLUE}--- Environment Variables ---${NC}"
if [[ -n "${NVD_API_KEY:-}" ]]; then
    check_ok "NVD_API_KEY set (faster CVE database downloads)"
else
    check_info "NVD_API_KEY not set (CVE scan will use slower anonymous rate)"
    echo "       Get key: https://nvd.nist.gov/developers/request-an-api-key"
    echo "       Set: export NVD_API_KEY=your-key"
fi
echo ""

# 7. Output directories
echo -e "${BLUE}--- Output Directories ---${NC}"
OUTPUT_DIR="$REPO_ROOT/tmp/ci"
if [[ -d "$OUTPUT_DIR" ]]; then
    check_ok "Output directory exists: $OUTPUT_DIR"
else
    mkdir -p "$OUTPUT_DIR/coverage" "$OUTPUT_DIR/security"
    check_ok "Created output directory: $OUTPUT_DIR"
fi
echo ""

# Summary
echo -e "${BLUE}=== Summary ===${NC}"
if [[ $ERRORS -gt 0 ]]; then
    echo -e "${RED}$ERRORS error(s), $WARNINGS warning(s)${NC}"
    echo "Fix errors before running local CI."
    exit 1
elif [[ $WARNINGS -gt 0 ]]; then
    echo -e "${YELLOW}$WARNINGS warning(s)${NC}"
    echo "Local CI should work but may differ from GitHub Actions."
    exit 0
else
    echo -e "${GREEN}All checks passed!${NC}"
    echo ""
    echo "Run local CI:"
    echo "  ./scripts/ci/local-ci.sh coverage   # Run coverage checks"
    echo "  ./scripts/ci/local-ci.sh security   # Run CVE scan"
    echo "  ./scripts/ci/local-ci.sh all        # Run both"
    exit 0
fi
