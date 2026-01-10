***REMOVED***!/usr/bin/env bash
***REMOVED*** =============================================================================
***REMOVED*** Scanium Ops Common Library
***REMOVED*** =============================================================================
***REMOVED*** Shared helpers for ops scripts. Source this file:
***REMOVED***   source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
***REMOVED*** =============================================================================

set -euo pipefail

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Color support (auto-disable if not TTY)
***REMOVED*** -----------------------------------------------------------------------------
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

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Logging helpers
***REMOVED*** -----------------------------------------------------------------------------
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

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Command checks
***REMOVED*** -----------------------------------------------------------------------------
require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" &>/dev/null; then
    die "Required command not found: $cmd"
  fi
}

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Repo root detection
***REMOVED*** -----------------------------------------------------------------------------
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

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Safe file operations (only inside repo)
***REMOVED*** -----------------------------------------------------------------------------
safe_mkdir() {
  local dir="$1"
  local repo_root
  repo_root="$(ensure_repo_root)"

  ***REMOVED*** Resolve to absolute path
  local abs_dir
  abs_dir="$(cd "$repo_root" && mkdir -p "$dir" && cd "$dir" && pwd)"

  ***REMOVED*** Verify it's inside repo
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

  ***REMOVED*** Resolve to absolute path
  local abs_file
  abs_file="$(cd "$(dirname "$file")" 2>/dev/null && pwd)/$(basename "$file")" || return 0

  ***REMOVED*** Verify it's inside repo
  case "$abs_file" in
    "$repo_root"/*)
      rm -f "$abs_file"
      ;;
    *)
      die "Refusing to remove file outside repo: $file"
      ;;
  esac
}

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Redaction helper
***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Redacts sensitive information from stdin, writes to stdout.
***REMOVED*** Best-effort pattern matching for:
***REMOVED***   - Lines containing: API_KEY, TOKEN, SECRET, PASSWORD, SESSION_SIGNING_SECRET
***REMOVED***   - Headers: Authorization:, X-API-Key:
***REMOVED***   - URLs with embedded credentials (basic auth)
***REMOVED*** -----------------------------------------------------------------------------
redact_secrets() {
  ***REMOVED*** Use sed for POSIX compatibility (works on both macOS and Linux)
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

***REMOVED*** Redact a string directly (for single values)
redact_string() {
  echo "$1" | redact_secrets
}

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** HTTP helpers
***REMOVED*** -----------------------------------------------------------------------------
http_get() {
  local url="$1"
  local timeout="${2:-10}"
  local headers="${3:-}"

  local curl_args=(-s --max-time "$timeout")

  if [[ -n "$headers" ]]; then
    ***REMOVED*** Headers passed as "Header1: value1\nHeader2: value2"
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

***REMOVED*** -----------------------------------------------------------------------------
***REMOVED*** Utility functions
***REMOVED*** -----------------------------------------------------------------------------
truncate_string() {
  local str="$1"
  local max_len="${2:-200}"

  if [[ ${***REMOVED***str} -gt $max_len ]]; then
    echo "${str:0:$max_len}..."
  else
    echo "$str"
  fi
}

***REMOVED*** Check if a value is in an array
in_array() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

***REMOVED*** Parse a simple key=value or --key value argument
parse_arg() {
  local arg="$1"
  local next="${2:-}"

  case "$arg" in
    --*=*)
      echo "${arg***REMOVED****=}"
      ;;
    --*)
      echo "$next"
      ;;
    *)
      echo "$arg"
      ;;
  esac
}

***REMOVED*** Print help header
print_help_header() {
  local script_name="$1"
  local description="$2"

  cat <<EOF
$script_name - $description

Usage: $script_name [OPTIONS]

EOF
}
