#!/bin/bash
# scripts/ci/run_security.sh - Run CVE security scan locally
# Mirrors: .github/workflows/security-cve-scan.yml

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="$REPO_ROOT/tmp/ci/security"
LOG_FILE="$OUTPUT_DIR/security_scan.log"
CACHE_DIR="$OUTPUT_DIR/cache"

echo -e "${BLUE}=== Local CVE Security Scan ===${NC}"
echo "Repo root: $REPO_ROOT"
echo "Output: $OUTPUT_DIR"
echo ""

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR" "$CACHE_DIR"

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

# Check for NVD API key
check_nvd_key() {
    if [[ -n "${NVD_API_KEY:-}" ]]; then
        echo -e "${GREEN}NVD API key detected${NC} (faster downloads)"
        export DEPENDENCY_CHECK_NVD_API_KEY="$NVD_API_KEY"
    elif [[ -n "${DEPENDENCY_CHECK_NVD_API_KEY:-}" ]]; then
        echo -e "${GREEN}DEPENDENCY_CHECK_NVD_API_KEY detected${NC}"
    else
        echo -e "${YELLOW}No NVD API key set${NC} (slower anonymous rate limits)"
        echo "  Get key: https://nvd.nist.gov/developers/request-an-api-key"
        echo "  Set: export NVD_API_KEY=your-key"
    fi
    echo ""
}

# Run OWASP Dependency-Check
run_scan() {
    local scan_exit_code=0

    echo -e "${BLUE}--- Running OWASP Dependency-Check ---${NC}"
    echo "Command: ./gradlew dependencyCheckAnalyze --no-daemon --stacktrace"
    echo "Logging to: $LOG_FILE"
    echo ""
    echo -e "${YELLOW}Note: First run downloads NVD database (~2GB). This may take 10-30 minutes.${NC}"
    echo ""

    # Run the scan (continue on error to collect reports)
    if "$REPO_ROOT/gradlew" -p "$REPO_ROOT" dependencyCheckAnalyze --no-daemon --stacktrace 2>&1 | tee "$LOG_FILE"; then
        echo -e "${GREEN}[OK]${NC} Dependency check completed"
    else
        echo -e "${YELLOW}[WARN]${NC} Dependency check completed with warnings/errors"
        scan_exit_code=1
    fi
    echo ""

    return $scan_exit_code
}

# Check for HIGH/CRITICAL vulnerabilities (mirrors CI behavior)
check_vulnerabilities() {
    local json_report="$REPO_ROOT/androidApp/build/reports/dependency-check-report.json"

    echo -e "${BLUE}--- Checking for HIGH/CRITICAL vulnerabilities ---${NC}"

    if [[ ! -f "$json_report" ]]; then
        echo -e "${RED}[FAIL]${NC} Report not found: $json_report"
        return 1
    fi

    # Check if jq is available
    if ! command -v jq &>/dev/null; then
        echo -e "${YELLOW}[WARN]${NC} jq not installed; cannot parse vulnerability counts"
        echo "  Install: brew install jq (macOS) or apt install jq (Linux)"
        return 0
    fi

    # Count vulnerabilities (matching CI logic)
    HIGH_COUNT=$(jq '[.dependencies[]?.vulnerabilities[]? | select(.severity == "HIGH")] | length' "$json_report" 2>/dev/null || echo "0")
    CRITICAL_COUNT=$(jq '[.dependencies[]?.vulnerabilities[]? | select(.severity == "CRITICAL")] | length' "$json_report" 2>/dev/null || echo "0")
    TOTAL=$((HIGH_COUNT + CRITICAL_COUNT))

    echo "Found: $CRITICAL_COUNT CRITICAL, $HIGH_COUNT HIGH severity vulnerabilities"
    echo ""

    if [[ $TOTAL -gt 0 ]]; then
        echo -e "${RED}[FAIL]${NC} $TOTAL HIGH/CRITICAL vulnerabilities found"

        # Show summary of vulnerable dependencies
        echo ""
        echo "Affected dependencies:"
        jq -r '.dependencies[] | select(.vulnerabilities != null and .vulnerabilities != []) | select(.vulnerabilities[].severity == "HIGH" or .vulnerabilities[].severity == "CRITICAL") | "  - \(.fileName)"' "$json_report" 2>/dev/null | sort -u | head -20
        echo ""
        echo "Review the HTML report for details."
        return 1
    else
        echo -e "${GREEN}[PASS]${NC} No HIGH/CRITICAL vulnerabilities found"
        return 0
    fi
}

# Copy reports to output directory
copy_reports() {
    echo -e "${BLUE}=== Security Reports ===${NC}"
    echo ""

    local report_dir="$REPO_ROOT/androidApp/build/reports"

    # SARIF
    if [[ -f "$report_dir/dependency-check-report.sarif" ]]; then
        cp "$report_dir/dependency-check-report.sarif" "$OUTPUT_DIR/"
        echo "SARIF: $OUTPUT_DIR/dependency-check-report.sarif"
    fi

    # HTML
    if [[ -f "$report_dir/dependency-check-report.html" ]]; then
        cp "$report_dir/dependency-check-report.html" "$OUTPUT_DIR/"
        echo "HTML:  $OUTPUT_DIR/dependency-check-report.html"
    fi

    # JSON
    if [[ -f "$report_dir/dependency-check-report.json" ]]; then
        cp "$report_dir/dependency-check-report.json" "$OUTPUT_DIR/"
        echo "JSON:  $OUTPUT_DIR/dependency-check-report.json"
    fi

    echo ""
    echo "All reports copied to: $OUTPUT_DIR/"
}

# Main
setup_java
ensure_gradlew
check_nvd_key

START_TIME=$(date +%s)

# Run scan (don't exit on failure - we want to check results)
SCAN_RESULT=0
run_scan || SCAN_RESULT=$?

# Copy reports regardless of scan result
copy_reports

# Check vulnerabilities
VULN_RESULT=0
check_vulnerabilities || VULN_RESULT=$?

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${BLUE}=== Summary ===${NC}"
echo "Duration: ${DURATION}s"
echo "Log: $LOG_FILE"

# Exit with failure if vulnerabilities found (matching CI behavior)
if [[ $VULN_RESULT -ne 0 ]]; then
    echo -e "${RED}Result: FAIL (vulnerabilities found)${NC}"
    exit 1
elif [[ $SCAN_RESULT -ne 0 ]]; then
    echo -e "${YELLOW}Result: WARN (scan had issues but no HIGH/CRITICAL vulns)${NC}"
    exit 0
else
    echo -e "${GREEN}Result: PASS${NC}"
    exit 0
fi
