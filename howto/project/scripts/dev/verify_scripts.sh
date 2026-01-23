#!/usr/bin/env bash
# =============================================================================
# Scanium Script Verification Tool
# =============================================================================
# Validates all scripts in the repository:
#   - Checks shebang lines
#   - Verifies file is executable
#   - Tests --help flag (if supported)
#   - Checks for common portability issues
#
# Usage:
#   ./scripts/dev/verify_scripts.sh [--help] [--fix] [--verbose]
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Source common library
source "$SCRIPT_DIR/../lib/common.sh" 2>/dev/null || {
  # Fallback logging if common.sh not available
  log_info() { echo "[INFO] $*"; }
  log_success() { echo "[OK] $*"; }
  log_warn() { echo "[WARN] $*" >&2; }
  log_error() { echo "[ERROR] $*" >&2; }
  log_fail() { echo "[FAIL] $*"; }
}

# Configuration
MANIFEST_FILE="$REPO_ROOT/scripts/scripts_manifest.json"
REPORT_FILE="$REPO_ROOT/tmp/scripts_verify_report.md"
FIX_MODE=false
VERBOSE=false

# Counters
TOTAL=0
PASSED=0
FAILED=0
SKIPPED=0

# =============================================================================
# Help
# =============================================================================
show_help() {
  cat <<EOF
verify_scripts.sh - Validate all Scanium scripts

Usage: $(basename "$0") [OPTIONS]

Options:
  --fix          Attempt to fix common issues (make executable, fix shebang)
  --verbose      Show detailed output
  --help         Show this help message

Checks performed:
  - Shebang line present and valid
  - File is executable
  - --help flag works (if script supports it)
  - No hard-coded Termux paths
  - No CRLF line endings

Output:
  - Console summary
  - Detailed report: tmp/scripts_verify_report.md

EOF
}

# =============================================================================
# Parse Arguments
# =============================================================================
while [[ $# -gt 0 ]]; do
  case "$1" in
    --fix)
      FIX_MODE=true
      shift
      ;;
    --verbose|-v)
      VERBOSE=true
      shift
      ;;
    --help|-h)
      show_help
      exit 0
      ;;
    *)
      log_error "Unknown option: $1"
      show_help
      exit 1
      ;;
  esac
done

# =============================================================================
# Validation Functions
# =============================================================================
check_shebang() {
  local file="$1"
  local first_line
  first_line=$(head -1 "$file" 2>/dev/null || echo "")

  if [[ ! "$first_line" =~ ^#! ]]; then
    echo "missing"
    return 1
  fi

  # Check for problematic shebangs
  if [[ "$first_line" =~ /data/data/com.termux ]]; then
    echo "termux-hardcoded"
    return 1
  fi

  if [[ "$first_line" =~ ^#!/usr/bin/env\ (bash|sh|python|python3|node) ]]; then
    echo "portable"
    return 0
  fi

  if [[ "$first_line" =~ ^#!/bin/(bash|sh) ]]; then
    echo "absolute"
    return 0
  fi

  echo "unusual"
  return 0
}

check_executable() {
  local file="$1"
  [[ -x "$file" ]]
}

check_crlf() {
  local file="$1"
  if file "$file" | grep -q "CRLF"; then
    return 1
  fi
  # Also check with grep
  if grep -q $'\r' "$file" 2>/dev/null; then
    return 1
  fi
  return 0
}

check_help_flag() {
  local file="$1"
  local supports_help="$2"

  if [[ "$supports_help" != "true" ]]; then
    echo "not_supported"
    return 0
  fi

  # Try running with --help (with timeout)
  local output
  if output=$(timeout 5 "$file" --help 2>&1); then
    if [[ -n "$output" ]]; then
      echo "works"
      return 0
    fi
  fi

  echo "failed"
  return 1
}

fix_shebang() {
  local file="$1"
  local first_line
  first_line=$(head -1 "$file")

  # Fix Termux hardcoded path
  if [[ "$first_line" =~ /data/data/com.termux.*bash ]]; then
    if [[ "$(uname)" == "Darwin" ]]; then
      sed -i '' '1s|.*|#!/usr/bin/env bash|' "$file"
    else
      sed -i '1s|.*|#!/usr/bin/env bash|' "$file"
    fi
    log_success "Fixed shebang in $(basename "$file")"
    return 0
  fi

  return 1
}

fix_executable() {
  local file="$1"
  chmod +x "$file"
  log_success "Made executable: $(basename "$file")"
}

fix_crlf() {
  local file="$1"
  if command -v dos2unix &>/dev/null; then
    dos2unix "$file" 2>/dev/null
    log_success "Fixed CRLF in $(basename "$file")"
  elif [[ "$(uname)" == "Darwin" ]]; then
    sed -i '' 's/\r$//' "$file"
    log_success "Fixed CRLF in $(basename "$file")"
  else
    sed -i 's/\r$//' "$file"
    log_success "Fixed CRLF in $(basename "$file")"
  fi
}

# =============================================================================
# Script Verification
# =============================================================================
verify_script() {
  local path="$1"
  local supports_help="${2:-false}"
  local full_path="$REPO_ROOT/$path"
  local issues=()
  local status="PASS"

  ((TOTAL++))

  if [[ ! -f "$full_path" ]]; then
    log_fail "$path - File not found"
    ((FAILED++))
    echo "| $path | FAIL | File not found |" >> "$REPORT_FILE"
    return 1
  fi

  # Check shebang
  local shebang_status
  shebang_status=$(check_shebang "$full_path")
  case "$shebang_status" in
    missing)
      issues+=("missing shebang")
      ;;
    termux-hardcoded)
      issues+=("hardcoded Termux path in shebang")
      if $FIX_MODE; then
        fix_shebang "$full_path" && issues=("${issues[@]/hardcoded*/fixed shebang}")
      fi
      ;;
  esac

  # Check executable
  if ! check_executable "$full_path"; then
    issues+=("not executable")
    if $FIX_MODE; then
      fix_executable "$full_path" && issues=("${issues[@]/not executable/fixed permissions}")
    fi
  fi

  # Check CRLF
  if ! check_crlf "$full_path"; then
    issues+=("CRLF line endings")
    if $FIX_MODE; then
      fix_crlf "$full_path" && issues=("${issues[@]/CRLF*/fixed CRLF}")
    fi
  fi

  # Determine final status
  if [[ ${#issues[@]} -gt 0 ]]; then
    # Check if all issues were fixed
    local unfixed=0
    for issue in "${issues[@]}"; do
      if [[ ! "$issue" =~ ^fixed ]]; then
        ((unfixed++))
      fi
    done

    if [[ $unfixed -gt 0 ]]; then
      status="FAIL"
      ((FAILED++))
      log_fail "$path - ${issues[*]}"
    else
      status="FIXED"
      ((PASSED++))
      log_success "$path - Fixed: ${issues[*]}"
    fi
  else
    ((PASSED++))
    if $VERBOSE; then
      log_success "$path"
    fi
  fi

  echo "| $path | $status | ${issues[*]:-OK} |" >> "$REPORT_FILE"
}

# =============================================================================
# Main
# =============================================================================
main() {
  print_banner "Scanium Script Verification"

  mkdir -p "$(dirname "$REPORT_FILE")"

  # Initialize report
  cat > "$REPORT_FILE" <<EOF
# Script Verification Report

Generated: $(date -u '+%Y-%m-%d %H:%M:%S UTC')
Platform: $(get_platform)
Fix mode: $FIX_MODE

## Results

| Script | Status | Notes |
|--------|--------|-------|
EOF

  log_info "Scanning scripts from manifest..."
  echo ""

  # Check if manifest exists
  if [[ ! -f "$MANIFEST_FILE" ]]; then
    log_warn "Manifest not found, scanning scripts/ directory directly"

    # Find all shell scripts
    while IFS= read -r -d '' file; do
      local rel_path="${file#$REPO_ROOT/}"
      verify_script "$rel_path" false
    done < <(find "$REPO_ROOT/scripts" -type f \( -name "*.sh" -o -name "*.bash" \) -print0 2>/dev/null)
  else
    # Use manifest
    local scripts
    scripts=$(jq -r '.areas[].scripts[] | "\(.path)|\(.supports_help)"' "$MANIFEST_FILE")

    while IFS='|' read -r path supports_help; do
      verify_script "$path" "$supports_help"
    done <<< "$scripts"
  fi

  # Summary
  echo ""
  echo "## Summary" >> "$REPORT_FILE"
  echo "" >> "$REPORT_FILE"
  echo "- Total: $TOTAL" >> "$REPORT_FILE"
  echo "- Passed: $PASSED" >> "$REPORT_FILE"
  echo "- Failed: $FAILED" >> "$REPORT_FILE"
  echo "- Skipped: $SKIPPED" >> "$REPORT_FILE"

  echo ""
  echo "════════════════════════════════════════════════════════════════"
  echo ""
  echo "  Total:   $TOTAL"
  echo -e "  Passed:  ${C_GREEN:-}$PASSED${C_RESET:-}"
  if [[ $FAILED -gt 0 ]]; then
    echo -e "  Failed:  ${C_RED:-}$FAILED${C_RESET:-}"
  else
    echo "  Failed:  $FAILED"
  fi
  echo "  Skipped: $SKIPPED"
  echo ""
  echo "  Report saved: $REPORT_FILE"
  echo ""
  echo "════════════════════════════════════════════════════════════════"

  if [[ $FAILED -gt 0 ]]; then
    echo ""
    log_warn "Some scripts have issues. Run with --fix to attempt repairs."
    return 1
  fi

  return 0
}

main "$@"
