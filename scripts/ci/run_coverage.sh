#!/bin/bash
# scripts/ci/run_coverage.sh - Run coverage checks locally
# Mirrors: .github/workflows/coverage.yml

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="$REPO_ROOT/tmp/ci/coverage"
LOG_FILE="$OUTPUT_DIR/gradle_coverage.log"

echo -e "${BLUE}=== Local Coverage Check ===${NC}"
echo "Repo root: $REPO_ROOT"
echo "Output: $OUTPUT_DIR"
echo ""

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Setup JDK 17 on macOS if available
setup_java() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        if /usr/libexec/java_home -v 17 &>/dev/null; then
            export JAVA_HOME=$(/usr/libexec/java_home -v 17)
            echo -e "${GREEN}Using JDK 17:${NC} $JAVA_HOME"
        else
            echo -e "${YELLOW}JDK 17 not found; using default JAVA_HOME${NC}"
        fi
    fi
    java -version 2>&1 | head -1
    echo ""
}

# Ensure gradlew is executable
ensure_gradlew() {
    if [[ ! -x "$REPO_ROOT/gradlew" ]]; then
        echo "Making gradlew executable..."
        chmod +x "$REPO_ROOT/gradlew"
    fi
}

# Run coverage
run_coverage() {
    local exit_code=0

    echo -e "${BLUE}--- Step 1: Run tests and verify coverage thresholds ---${NC}"
    echo "Command: ./gradlew clean test koverVerify"
    echo "Logging to: $LOG_FILE"
    echo ""

    # Run tests with kover verification (this is the main CI check)
    if "$REPO_ROOT/gradlew" -p "$REPO_ROOT" clean test koverVerify 2>&1 | tee "$LOG_FILE"; then
        echo -e "${GREEN}[PASS]${NC} Tests passed and coverage thresholds met"
    else
        echo -e "${RED}[FAIL]${NC} Tests or coverage verification failed"
        exit_code=1
    fi
    echo ""

    echo -e "${BLUE}--- Step 2: Generate Jacoco HTML report (androidApp) ---${NC}"
    echo "Command: ./gradlew jacocoTestReport"
    echo ""

    # Generate Jacoco report (continue on error, matching CI behavior)
    if "$REPO_ROOT/gradlew" -p "$REPO_ROOT" jacocoTestReport 2>&1 | tee -a "$LOG_FILE"; then
        echo -e "${GREEN}[OK]${NC} Jacoco report generated"
    else
        echo -e "${YELLOW}[WARN]${NC} Jacoco report generation failed (continuing)"
    fi
    echo ""

    return $exit_code
}

# Collect and display report locations
show_reports() {
    echo -e "${BLUE}=== Coverage Reports ===${NC}"
    echo ""

    # Kover reports (shared modules)
    echo "Kover HTML reports (shared modules):"
    find "$REPO_ROOT" -path "*/build/reports/kover/html/index.html" -type f 2>/dev/null | while read -r report; do
        echo "  - $report"
        # Copy to output dir
        module=$(echo "$report" | sed "s|$REPO_ROOT/||" | cut -d'/' -f1-2 | tr '/' '_')
        mkdir -p "$OUTPUT_DIR/kover_$module"
        cp -r "$(dirname "$report")"/* "$OUTPUT_DIR/kover_$module/" 2>/dev/null || true
    done
    echo ""

    # Jacoco report (androidApp)
    JACOCO_REPORT="$REPO_ROOT/androidApp/build/reports/jacoco/testDebugUnitTest/html/index.html"
    if [[ -f "$JACOCO_REPORT" ]]; then
        echo "Jacoco HTML report (androidApp):"
        echo "  - $JACOCO_REPORT"
        mkdir -p "$OUTPUT_DIR/jacoco"
        cp -r "$(dirname "$JACOCO_REPORT")"/* "$OUTPUT_DIR/jacoco/" 2>/dev/null || true
    else
        echo -e "${YELLOW}Jacoco report not found${NC}"
    fi
    echo ""

    echo "Reports copied to: $OUTPUT_DIR/"
    ls -la "$OUTPUT_DIR/" 2>/dev/null || true
    echo ""

    echo -e "${BLUE}Expected thresholds:${NC}"
    echo "  - Shared modules (core-models, core-tracking): >= 85%"
    echo "  - androidApp: >= 75%"
}

# Main
setup_java
ensure_gradlew

START_TIME=$(date +%s)
run_coverage
EXIT_CODE=$?
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

show_reports

echo ""
echo -e "${BLUE}=== Summary ===${NC}"
echo "Duration: ${DURATION}s"
echo "Log: $LOG_FILE"
if [[ $EXIT_CODE -eq 0 ]]; then
    echo -e "${GREEN}Result: PASS${NC}"
else
    echo -e "${RED}Result: FAIL${NC}"
fi

exit $EXIT_CODE
