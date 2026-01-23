#!/usr/bin/env bash
# =============================================================================
# Scanium Ops Common Library
# =============================================================================
# Shared helpers for ops scripts. Source this file:
#   source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Color support (auto-disable if not TTY)
# -----------------------------------------------------------------------------
if [[ -t 1 ]]; then
  COLOR_RED='\033[0;31m'
  COLOR_YELLOW='\033[0;33m'
  COLOR_GREEN='\033[0;32m'
  COLOR_BLUE='\033[0;34m'
  COLOR_RESET='\033[0m'
else
  COLOR_RED=''
  COLOR_YELLOW=''
  COLOR_GREEN=''
  COLOR_BLUE=''
  COLOR_RESET=''
fi

# -----------------------------------------------------------------------------
# Logging helpers
# -----------------------------------------------------------------------------
timestamp() {
  date -u '+%Y-%m-%dT%H:%M:%SZ'
}

log_info() {
  echo -e "${COLOR_BLUE}[$(timestamp)] INFO:${COLOR_RESET} $*"
}

log_warn() {
  echo -e "${COLOR_YELLOW}[$(timestamp)] WARN:${COLOR_RESET} $*" >&2
}

log_error() {
  echo -e "${COLOR_RED}[$(timestamp)] ERROR:${COLOR_RESET} $*" >&2
}

log_success() {
  echo -e "${COLOR_GREEN}[$(timestamp)] PASS:${COLOR_RESET} $*"
}

log_fail() {
  echo -e "${COLOR_RED}[$(timestamp)] FAIL:${COLOR_RESET} $*"
}

die() {
  log_error "$@"
  exit 1
}

# -----------------------------------------------------------------------------
# Command checks
# -----------------------------------------------------------------------------
require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" &>/dev/null; then
    die "Required command not found: $cmd"
  fi
}

# -----------------------------------------------------------------------------
# Repo root detection
# -----------------------------------------------------------------------------
get_repo_root() {
  git rev-parse --show-toplevel 2>/dev/null || die "Not inside a git repository"
}

REPO_ROOT=""
ensure_repo_root() {
  if [[ -z "$REPO_ROOT" ]]; then
    REPO_ROOT="$(get_repo_root)"
  fi
  echo "$REPO_ROOT"
}

# -----------------------------------------------------------------------------
# Safe file operations (only inside repo)
# -----------------------------------------------------------------------------
safe_mkdir() {
  local dir="$1"
  local repo_root
  repo_root="$(ensure_repo_root)"

  # Resolve to absolute path
  local abs_dir
  abs_dir="$(cd "$repo_root" && mkdir -p "$dir" && cd "$dir" && pwd)"

  # Verify it's inside repo
  case "$abs_dir" in
    "$repo_root"/*)
      echo "$abs_dir"
      ;;
    *)
      die "Refusing to create directory outside repo: $dir"
      ;;
  esac
}

safe_rmfile() {
  local file="$1"
  local repo_root
  repo_root="$(ensure_repo_root)"

  # Resolve to absolute path
  local abs_file
  abs_file="$(cd "$(dirname "$file")" 2>/dev/null && pwd)/$(basename "$file")" || return 0

  # Verify it's inside repo
  case "$abs_file" in
    "$repo_root"/*)
      rm -f "$abs_file"
      ;;
    *)
      die "Refusing to remove file outside repo: $file"
      ;;
  esac
}

# -----------------------------------------------------------------------------
# Redaction helper
# -----------------------------------------------------------------------------
# Redacts sensitive information from stdin, writes to stdout.
# Best-effort pattern matching for:
#   - Lines containing: API_KEY, TOKEN, SECRET, PASSWORD, SESSION_SIGNING_SECRET
#   - Headers: Authorization:, X-API-Key:
#   - URLs with embedded credentials (basic auth)
# -----------------------------------------------------------------------------
redact_secrets() {
  # Use sed for POSIX compatibility (works on both macOS and Linux)
  sed -E \
    -e 's/(API_KEY|APIKEY|api_key|apikey)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(TOKEN|token)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(SECRET|secret)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(PASSWORD|password|passwd)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(SESSION_SIGNING_SECRET)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(Authorization)[[:space:]]*:[[:space:]]*[^[:space:]"'\'']+/\1: [REDACTED]/gi' \
    -e 's/(X-API-Key)[[:space:]]*:[[:space:]]*[^[:space:]"'\'']+/\1: [REDACTED]/gi' \
    -e 's/(Bearer)[[:space:]]+[A-Za-z0-9_.\-]+/Bearer [REDACTED]/gi' \
    -e 's|://[^:/@]+:[^@]+@|://[REDACTED]:[REDACTED]@|g' \
    -e 's/"(api_key|apikey|token|secret|password|authorization)"[[:space:]]*:[[:space:]]*"[^"]*"/"\1": "[REDACTED]"/gi'
}

# Redact a string directly (for single values)
redact_string() {
  echo "$1" | redact_secrets
}

# -----------------------------------------------------------------------------
# HTTP helpers
# -----------------------------------------------------------------------------
http_get() {
  local url="$1"
  local timeout="${2:-10}"
  local headers="${3:-}"

  local curl_args=(-s --max-time "$timeout")

  if [[ -n "$headers" ]]; then
    # Headers passed as "Header1: value1\nHeader2: value2"
    while IFS= read -r header; do
      [[ -n "$header" ]] && curl_args+=(-H "$header")
    done <<< "$headers"
  fi

  curl "${curl_args[@]}" "$url" 2>/dev/null
}

http_get_status() {
  local url="$1"
  local timeout="${2:-10}"
  local headers="${3:-}"

  local curl_args=(-s -o /dev/null -w '%{http_code}' --max-time "$timeout")

  if [[ -n "$headers" ]]; then
    while IFS= read -r header; do
      [[ -n "$header" ]] && curl_args+=(-H "$header")
    done <<< "$headers"
  fi

  curl "${curl_args[@]}" "$url" 2>/dev/null || echo "000"
}

http_get_with_body() {
  local url="$1"
  local timeout="${2:-10}"
  local headers="${3:-}"
  local tmp_body
  tmp_body=$(mktemp)

  local curl_args=(-s -w '%{http_code}' --max-time "$timeout" -o "$tmp_body")

  if [[ -n "$headers" ]]; then
    while IFS= read -r header; do
      [[ -n "$header" ]] && curl_args+=(-H "$header")
    done <<< "$headers"
  fi

  local status
  status=$(curl "${curl_args[@]}" "$url" 2>/dev/null || echo "000")

  echo "$status"
  cat "$tmp_body" 2>/dev/null || true
  rm -f "$tmp_body"
}

# -----------------------------------------------------------------------------
# Utility functions
# -----------------------------------------------------------------------------
truncate_string() {
  local str="$1"
  local max_len="${2:-200}"

  if [[ ${#str} -gt $max_len ]]; then
    echo "${str:0:$max_len}..."
  else
    echo "$str"
  fi
}

# Check if a value is in an array
in_array() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

# Parse a simple key=value or --key value argument
parse_arg() {
  local arg="$1"
  local next="${2:-}"

  case "$arg" in
    --*=*)
      echo "${arg#*=}"
      ;;
    --*)
      echo "$next"
      ;;
    *)
      echo "$arg"
      ;;
  esac
}

# Print help header
print_help_header() {
  local script_name="$1"
  local description="$2"

  cat <<EOF
$script_name - $description

Usage: $script_name [OPTIONS]

EOF
}
