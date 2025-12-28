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

# Parse options
OPEN_BROWSER=true
for arg in "$@"; do
    case "$arg" in
        --no-open)
            OPEN_BROWSER=false
            ;;
    esac
done

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

    echo -e "${BLUE}--- Step 2: Generate Kover HTML reports ---${NC}"
    echo "Command: ./gradlew koverHtmlReport"
    echo ""

    # Generate Kover HTML reports
    if "$REPO_ROOT/gradlew" -p "$REPO_ROOT" koverHtmlReport 2>&1 | tee -a "$LOG_FILE"; then
        echo -e "${GREEN}[OK]${NC} Kover HTML reports generated"
    else
        echo -e "${YELLOW}[WARN]${NC} Kover HTML report generation failed (continuing)"
    fi
    echo ""

    echo -e "${BLUE}--- Step 3: Generate Jacoco HTML report (androidApp) ---${NC}"
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

# Open file in browser (cross-platform)
open_in_browser() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        return 1
    fi

    if [[ "$OSTYPE" == "darwin"* ]]; then
        open "$file"
    elif command -v xdg-open &>/dev/null; then
        xdg-open "$file"
    elif command -v sensible-browser &>/dev/null; then
        sensible-browser "$file"
    else
        echo -e "${YELLOW}Cannot auto-open browser. Open manually: $file${NC}"
        return 1
    fi
}

# Collect and display report locations
show_reports() {
    echo -e "${BLUE}=== Coverage Reports ===${NC}"
    echo ""

    local reports_to_open=()

    # Kover reports (shared modules)
    echo "Kover HTML reports (shared modules):"
    while IFS= read -r report; do
        if [[ -n "$report" ]]; then
            echo "  - $report"
            # Copy to output dir
            module=$(echo "$report" | sed "s|$REPO_ROOT/||" | cut -d'/' -f1-2 | tr '/' '_')
            mkdir -p "$OUTPUT_DIR/kover_$module"
            cp -r "$(dirname "$report")"/* "$OUTPUT_DIR/kover_$module/" 2>/dev/null || true
            reports_to_open+=("$report")
        fi
    done < <(find "$REPO_ROOT" -path "*/build/reports/kover/html/index.html" -type f 2>/dev/null)
    echo ""

    # Jacoco report (androidApp)
    JACOCO_REPORT="$REPO_ROOT/androidApp/build/reports/jacoco/testDebugUnitTest/html/index.html"
    if [[ -f "$JACOCO_REPORT" ]]; then
        echo "Jacoco HTML report (androidApp):"
        echo "  - $JACOCO_REPORT"
        mkdir -p "$OUTPUT_DIR/jacoco"
        cp -r "$(dirname "$JACOCO_REPORT")"/* "$OUTPUT_DIR/jacoco/" 2>/dev/null || true
        reports_to_open+=("$JACOCO_REPORT")
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

    # Open reports in browser
    if [[ "$OPEN_BROWSER" == true && ${#reports_to_open[@]} -gt 0 ]]; then
        echo ""
        echo -e "${BLUE}Opening reports in browser...${NC}"
        for report in "${reports_to_open[@]}"; do
            open_in_browser "$report"
            sleep 0.3  # Small delay to avoid overwhelming the browser
        done
    elif [[ ${#reports_to_open[@]} -gt 0 ]]; then
        echo ""
        echo -e "${BLUE}Tip:${NC} Run without --no-open to auto-open reports in browser"
    fi
}

# Print all HTML reports at the end
print_all_reports() {
    echo ""
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                         HTML COVERAGE REPORTS                             ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # Find all Kover reports
    echo -e "${BLUE}Kover Reports:${NC}"
    local kover_found=false
    while IFS= read -r report; do
        if [[ -n "$report" ]]; then
            kover_found=true
            echo "  $report"
        fi
    done < <(find "$REPO_ROOT" -path "*/build/reports/kover/html/index.html" -type f 2>/dev/null | sort)
    if [[ "$kover_found" == false ]]; then
        echo -e "  ${YELLOW}(none found)${NC}"
    fi
    echo ""

    # Find all Jacoco reports
    echo -e "${BLUE}Jacoco Reports:${NC}"
    local jacoco_found=false
    while IFS= read -r report; do
        if [[ -n "$report" ]]; then
            jacoco_found=true
            echo "  $report"
        fi
    done < <(find "$REPO_ROOT" -path "*/build/reports/jacoco/*/html/index.html" -type f 2>/dev/null | sort)
    if [[ "$jacoco_found" == false ]]; then
        echo -e "  ${YELLOW}(none found)${NC}"
    fi
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════════════════${NC}"
}

# Main
setup_java
ensure_gradlew

START_TIME=$(date +%s)
run_coverage
EXIT_CODE=$?
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${BLUE}=== Summary ===${NC}"
echo "Duration: ${DURATION}s"
echo "Log: $LOG_FILE"
if [[ $EXIT_CODE -eq 0 ]]; then
    echo -e "${GREEN}Result: PASS${NC}"
else
    echo -e "${RED}Result: FAIL${NC}"
fi

# Print all reports at the very end
print_all_reports

exit $EXIT_CODE
