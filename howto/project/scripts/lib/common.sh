#!/usr/bin/env bash
# =============================================================================
# Scanium Common Script Library
# =============================================================================
# Shared helpers for all Scanium scripts. Source this file:
#   source "$(dirname "${BASH_SOURCE[0]}")/../lib/common.sh"
#
# Or from any script:
#   SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
#   source "$SCRIPT_DIR/../lib/common.sh" 2>/dev/null || source "$SCRIPT_DIR/lib/common.sh"
#
# Features:
#   - Platform detection (macOS/Linux/Termux)
#   - Colored logging with timestamps
#   - Command/environment checks
#   - Secret redaction
#   - Portable path resolution
#   - HTTP helpers
# =============================================================================

# Only set options if not already set by calling script
[[ -z "${COMMON_SH_LOADED:-}" ]] || return 0
COMMON_SH_LOADED=1

set -euo pipefail

# =============================================================================
# Platform Detection
# =============================================================================
is_macos() {
  [[ "$(uname -s)" == "Darwin" ]]
}

is_linux() {
  [[ "$(uname -s)" == "Linux" ]]
}

is_termux() {
  [[ -n "${TERMUX_VERSION:-}" ]] || [[ -d "/data/data/com.termux" ]]
}

is_ci() {
  [[ -n "${CI:-}" ]] || [[ -n "${GITHUB_ACTIONS:-}" ]]
}

get_platform() {
  if is_termux; then
    echo "termux"
  elif is_macos; then
    echo "macos"
  elif is_linux; then
    echo "linux"
  else
    echo "unknown"
  fi
}

# =============================================================================
# Color Support (auto-disable if not TTY or CI without color support)
# =============================================================================
if [[ -t 1 ]] && [[ -z "${NO_COLOR:-}" ]]; then
  COLOR_RED='\033[0;31m'
  COLOR_YELLOW='\033[0;33m'
  COLOR_GREEN='\033[0;32m'
  COLOR_BLUE='\033[0;34m'
  COLOR_CYAN='\033[0;36m'
  COLOR_BOLD='\033[1m'
  COLOR_DIM='\033[2m'
  COLOR_RESET='\033[0m'
else
  COLOR_RED=''
  COLOR_YELLOW=''
  COLOR_GREEN=''
  COLOR_BLUE=''
  COLOR_CYAN=''
  COLOR_BOLD=''
  COLOR_DIM=''
  COLOR_RESET=''
fi

# =============================================================================
# Logging Helpers
# =============================================================================
timestamp() {
  date -u '+%Y-%m-%dT%H:%M:%SZ'
}

log_info() {
  echo -e "${COLOR_BLUE}[INFO]${COLOR_RESET} $*"
}

log_warn() {
  echo -e "${COLOR_YELLOW}[WARN]${COLOR_RESET} $*" >&2
}

log_error() {
  echo -e "${COLOR_RED}[ERROR]${COLOR_RESET} $*" >&2
}

log_success() {
  echo -e "${COLOR_GREEN}[OK]${COLOR_RESET} $*"
}

log_fail() {
  echo -e "${COLOR_RED}[FAIL]${COLOR_RESET} $*"
}

log_debug() {
  [[ -n "${DEBUG:-}" ]] && echo -e "${COLOR_DIM}[DEBUG]${COLOR_RESET} $*"
}

die() {
  log_error "$@"
  exit 1
}

# =============================================================================
# Command & Environment Checks
# =============================================================================
require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" &>/dev/null; then
    die "Required command not found: $cmd"
  fi
}

require_env() {
  local var="$1"
  if [[ -z "${!var:-}" ]]; then
    die "Required environment variable not set: $var"
  fi
}

has_cmd() {
  command -v "$1" &>/dev/null
}

# =============================================================================
# Repo Root Detection (portable)
# =============================================================================
get_repo_root() {
  if has_cmd git && git rev-parse --show-toplevel &>/dev/null; then
    git rev-parse --show-toplevel
  else
    # Fallback: search upward for .git directory
    local dir="$PWD"
    while [[ "$dir" != "/" ]]; do
      if [[ -d "$dir/.git" ]]; then
        echo "$dir"
        return 0
      fi
      dir="$(dirname "$dir")"
    done
    die "Could not find repository root"
  fi
}

# Cached repo root
_REPO_ROOT=""
ensure_repo_root() {
  if [[ -z "$_REPO_ROOT" ]]; then
    _REPO_ROOT="$(get_repo_root)"
  fi
  echo "$_REPO_ROOT"
}

# =============================================================================
# Portable Path Resolution
# =============================================================================
# Portable realpath alternative (works on macOS without coreutils)
portable_realpath() {
  local path="$1"
  if has_cmd realpath; then
    realpath "$path" 2>/dev/null && return 0
  fi
  if has_cmd greadlink; then
    greadlink -f "$path" 2>/dev/null && return 0
  fi
  # Python fallback
  if has_cmd python3; then
    python3 -c "import os; print(os.path.realpath('$path'))" 2>/dev/null && return 0
  fi
  if has_cmd python; then
    python -c "import os; print(os.path.realpath('$path'))" 2>/dev/null && return 0
  fi
  # Manual resolution
  if [[ -d "$path" ]]; then
    (cd "$path" && pwd)
  elif [[ -f "$path" ]]; then
    local dir file
    dir="$(dirname "$path")"
    file="$(basename "$path")"
    echo "$(cd "$dir" && pwd)/$file"
  else
    echo "$path"
  fi
}

# =============================================================================
# Portable sed -i (handles macOS vs Linux difference)
# =============================================================================
portable_sed_i() {
  local pattern="$1"
  local file="$2"

  if is_macos; then
    sed -i '' "$pattern" "$file"
  else
    sed -i "$pattern" "$file"
  fi
}

# =============================================================================
# Secret Redaction
# =============================================================================
redact_secrets() {
  sed -E \
    -e 's/(API_KEY|APIKEY|api_key|apikey)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(TOKEN|token)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(SECRET|secret)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(PASSWORD|password|passwd)[[:space:]]*[=:][[:space:]]*[^[:space:]"'\'']+/\1=[REDACTED]/gi' \
    -e 's/(Authorization)[[:space:]]*:[[:space:]]*[^[:space:]"'\'']+/\1: [REDACTED]/gi' \
    -e 's/(X-API-Key)[[:space:]]*:[[:space:]]*[^[:space:]"'\'']+/\1: [REDACTED]/gi' \
    -e 's/(Bearer)[[:space:]]+[A-Za-z0-9_.\-]+/Bearer [REDACTED]/gi' \
    -e 's|://[^:/@]+:[^@]+@|://[REDACTED]:[REDACTED]@|g'
}

# =============================================================================
# HTTP Helpers
# =============================================================================
http_get_status() {
  local url="$1"
  local timeout="${2:-10}"
  curl -s -o /dev/null -w '%{http_code}' --max-time "$timeout" "$url" 2>/dev/null || echo "000"
}

http_get() {
  local url="$1"
  local timeout="${2:-10}"
  curl -s --max-time "$timeout" "$url" 2>/dev/null
}

# =============================================================================
# Utility Functions
# =============================================================================
truncate_string() {
  local str="$1"
  local max_len="${2:-200}"
  if [[ ${#str} -gt $max_len ]]; then
    echo "${str:0:$max_len}..."
  else
    echo "$str"
  fi
}

# Check if running in interactive mode
is_interactive() {
  [[ -t 0 ]] && [[ -t 1 ]]
}

# Prompt for confirmation (returns 0 for yes, 1 for no)
confirm() {
  local prompt="${1:-Continue?}"
  local default="${2:-n}"

  if ! is_interactive; then
    [[ "$default" == "y" ]]
    return $?
  fi

  local reply
  read -p "$prompt [y/n] " -n 1 -r reply
  echo
  [[ "$reply" =~ ^[Yy]$ ]]
}

# =============================================================================
# Script Header/Banner
# =============================================================================
print_banner() {
  local title="$1"
  local width="${2:-60}"
  local line
  line=$(printf '%*s' "$width" '' | tr ' ' '=')

  echo ""
  echo -e "${COLOR_CYAN}${line}${COLOR_RESET}"
  echo -e "${COLOR_CYAN}  ${COLOR_BOLD}${title}${COLOR_RESET}"
  echo -e "${COLOR_CYAN}${line}${COLOR_RESET}"
  echo ""
}

# =============================================================================
# Export for subshells
# =============================================================================
export -f is_macos is_linux is_termux is_ci get_platform 2>/dev/null || true
export -f log_info log_warn log_error log_success log_fail log_debug die 2>/dev/null || true
export -f require_cmd require_env has_cmd 2>/dev/null || true
export -f get_repo_root ensure_repo_root 2>/dev/null || true
export -f portable_realpath portable_sed_i 2>/dev/null || true
export -f redact_secrets 2>/dev/null || true
export -f http_get_status http_get 2>/dev/null || true
