#!/usr/bin/env bash
set -euo pipefail

# Build debug APK on remote Mac via SSH, then pull to phone Downloads

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/remote_env"

# SSH options for mobile network resilience
SSH_OPTS=(
    -o ServerAliveInterval=30
    -o ServerAliveCountMax=3
    -o ConnectTimeout=15
    -o BatchMode=yes
)

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# Load remote_env
if [[ ! -f "$ENV_FILE" ]]; then
    log_error "Remote environment not configured."
    echo ""
    echo "Setup instructions:"
    echo "  1. cp $SCRIPT_DIR/remote_env.example $SCRIPT_DIR/remote_env"
    echo "  2. Edit remote_env with your Mac's Tailscale IP/hostname"
    echo "  3. Ensure SSH key is set up: ssh-keygen -t ed25519"
    echo "  4. Add public key to Mac: ~/.ssh/authorized_keys"
    echo ""
    exit 1
fi

# shellcheck source=/dev/null
source "$ENV_FILE"

# Validate required variables
if [[ -z "${MAC_SSH_HOST:-}" || -z "${MAC_SSH_USER:-}" ]]; then
    log_error "MAC_SSH_HOST and MAC_SSH_USER must be set in remote_env"
    exit 1
fi

MAC_SSH="${MAC_SSH_USER}@${MAC_SSH_HOST}"
MAC_REPO_DIR="${MAC_REPO_DIR:-~/dev/scanium}"
PHONE_APK_DIR="${PHONE_APK_DIR:-/storage/emulated/0/Download/scanium-apk}"
DRY_RUN="${DRY_RUN:-0}"

# Flavor Selection Menu
echo ""
echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Select Build Flavor                  ║${NC}"
echo -e "${BLUE}╠════════════════════════════════════════╣${NC}"
echo -e "${BLUE}║  1) Dev    (com.scanium.app.dev)       ║${NC}"
echo -e "${BLUE}║  2) Beta   (com.scanium.app.beta)      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""
read -p "Enter your choice [1-2] (default: 1): " FLAVOR_CHOICE

case "${FLAVOR_CHOICE:-1}" in
  1)
    FLAVOR="dev"
    FLAVOR_CAPITALIZED="Dev"
    GRADLE_TASK="assembleDevDebug"
    ;;
  2)
    FLAVOR="beta"
    FLAVOR_CAPITALIZED="Beta"
    GRADLE_TASK="assembleBetaDebug"
    ;;
  *)
    log_error "Invalid choice. Exiting."
    exit 1
    ;;
esac

echo ""
log_info "Selected: ${FLAVOR_CAPITALIZED} flavor"
log_info "Gradle task: ${GRADLE_TASK}"
echo ""

run_cmd() {
    if [[ "$DRY_RUN" == "1" ]]; then
        echo "[DRY_RUN] $*"
    else
        "$@"
    fi
}

# Test SSH connection
log_info "Testing SSH connection to $MAC_SSH..."
if ! run_cmd ssh "${SSH_OPTS[@]}" "$MAC_SSH" "echo 'Connected to Mac'"; then
    log_error "SSH connection failed."
    echo ""
    echo "Troubleshooting:"
    echo "  - Verify Tailscale is connected on both devices"
    echo "  - Check MAC_SSH_HOST in remote_env (try: tailscale status)"
    echo "  - Ensure Remote Login is enabled on Mac"
    echo "  - Test manually: ssh ${MAC_SSH}"
    exit 1
fi

# Build APK on Mac (try :androidApp first, then fall back to :app)
log_info "Building ${FLAVOR_CAPITALIZED} debug APK on Mac..."
echo ""

GRADLE_LOG="tmp/termux_remote_gradle.log"

BUILD_SCRIPT='
cd '"$MAC_REPO_DIR"'

# Force JDK 17 (project requirement)
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"

# Parse ANDROID_SDK_ROOT from local.properties
if [[ -f "local.properties" ]]; then
    SDK_DIR=$(grep "^sdk.dir=" local.properties | cut -d= -f2-)
    if [[ -n "$SDK_DIR" ]]; then
        export ANDROID_SDK_ROOT="$SDK_DIR"
        export ANDROID_HOME="$SDK_DIR"
    fi
fi

# Preflight: print environment info
echo "=== Remote Environment Preflight ==="
echo "JAVA_HOME=$JAVA_HOME"
java -version 2>&1 | head -2
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-<not set>}"
echo "Building: '"$GRADLE_TASK"'"
echo "===================================="

# Ensure tmp dir exists for logs
mkdir -p tmp

# Build with logging
if ./gradlew :androidApp:'"$GRADLE_TASK"' 2>&1 | tee '"$GRADLE_LOG"'; then
    echo "BUILD_SUCCESS"
elif ./gradlew '"$GRADLE_TASK"' 2>&1 | tee '"$GRADLE_LOG"'; then
    echo "BUILD_SUCCESS"
else
    echo ""
    echo "=== Last 200 lines of Gradle log ==="
    tail -200 '"$GRADLE_LOG"'
    echo "====================================="
    echo "BUILD_FAILED"
    exit 1
fi
'

if ! run_cmd ssh "${SSH_OPTS[@]}" "$MAC_SSH" "$BUILD_SCRIPT"; then
    log_error "APK build failed on Mac"
    log_info "Full log available at: $MAC_REPO_DIR/$GRADLE_LOG"
    exit 1
fi

# Find newest APK on Mac (robust search across all variants/flavors)
log_info "Locating APK..."

FIND_APK_SCRIPT='
cd '"$MAC_REPO_DIR"'

# Priority-based APK selection for arm64 devices (e.g., Samsung S24 Ultra):
#   1. arm64-v8a debug APK for selected flavor (preferred for modern 64-bit phones)
#   2. universal debug APK for selected flavor (if ever present)
#   3. newest debug APK for selected flavor by mtime (fallback)

APK_PATH=""
APK_ABI=""
FLAVOR="'"$FLAVOR"'"

# Priority 1: arm64-v8a debug APK for selected flavor
ARM64_APK=$(find . -path "*/build/outputs/apk/${FLAVOR}/*" -type f -name "*arm64-v8a*debug*.apk" 2>/dev/null | head -1)
if [[ -n "$ARM64_APK" ]]; then
    APK_PATH="$ARM64_APK"
    APK_ABI="arm64-v8a"
fi

# Priority 2: universal debug APK for selected flavor
if [[ -z "$APK_PATH" ]]; then
    UNIVERSAL_APK=$(find . -path "*/build/outputs/apk/${FLAVOR}/*" -type f -name "*universal*debug*.apk" 2>/dev/null | head -1)
    if [[ -n "$UNIVERSAL_APK" ]]; then
        APK_PATH="$UNIVERSAL_APK"
        APK_ABI="universal"
    fi
fi

# Priority 3: newest debug APK for selected flavor by mtime
if [[ -z "$APK_PATH" ]]; then
    NEWEST_APK=$(find . -path "*/build/outputs/apk/${FLAVOR}/*" -type f -name "*debug*.apk" 2>/dev/null | \
        xargs ls -t 2>/dev/null | head -1)
    if [[ -n "$NEWEST_APK" ]]; then
        APK_PATH="$NEWEST_APK"
        # Detect ABI from filename
        case "$NEWEST_APK" in
            *x86_64*)    APK_ABI="x86_64" ;;
            *x86*)       APK_ABI="x86" ;;
            *armeabi*)   APK_ABI="armeabi-v7a" ;;
            *arm64*)     APK_ABI="arm64-v8a" ;;
            *universal*) APK_ABI="universal" ;;
            *)           APK_ABI="unknown" ;;
        esac
    fi
fi

if [[ -n "$APK_PATH" ]]; then
    echo "APK_FOUND:$APK_PATH"
    echo "APK_ABI:$APK_ABI"
    exit 0
fi

# No APK found - gather diagnostics
echo "APK_NOT_FOUND"

# Check for AAB files
AAB_FILES=$(find . -path "*/build/outputs/bundle/*.aab" -type f 2>/dev/null)
if [[ -n "$AAB_FILES" ]]; then
    echo "AAB_FILES_FOUND:"
    echo "$AAB_FILES"
fi

# Show build/outputs directory structure for debugging
echo "BUILD_OUTPUTS_SUMMARY:"
find . -path "*/build/outputs" -type d 2>/dev/null | while read -r dir; do
    echo "  $dir:"
    find "$dir" -maxdepth 3 -type f -name "*.apk" -o -name "*.aab" 2>/dev/null | sed "s/^/    /"
done

exit 1
'

FIND_RESULT=$(run_cmd ssh "${SSH_OPTS[@]}" "$MAC_SSH" "$FIND_APK_SCRIPT" || true)

if echo "$FIND_RESULT" | grep -q "^APK_FOUND:"; then
    APK_PATH=$(echo "$FIND_RESULT" | grep "^APK_FOUND:" | head -1 | sed 's/^APK_FOUND://')
    APK_ABI=$(echo "$FIND_RESULT" | grep "^APK_ABI:" | head -1 | sed 's/^APK_ABI://')
    log_info "Found ${FLAVOR_CAPITALIZED} APK: $APK_PATH (ABI: $APK_ABI)"

    # Warn if not arm64-v8a (may be incompatible with modern phones)
    if [[ "$APK_ABI" != "arm64-v8a" && "$APK_ABI" != "universal" ]]; then
        log_warn "Selected APK is $APK_ABI - may be incompatible with arm64 devices (e.g., Samsung S24)"
    fi
else
    log_error "No ${FLAVOR_CAPITALIZED} APK found on Mac"
    echo ""

    # Parse and display diagnostics from remote
    if echo "$FIND_RESULT" | grep -q "AAB_FILES_FOUND:"; then
        log_warn "Found AAB bundle(s) instead of APK:"
        echo "$FIND_RESULT" | sed -n '/AAB_FILES_FOUND:/,/BUILD_OUTPUTS_SUMMARY:/p' | \
            grep -v "AAB_FILES_FOUND:\|BUILD_OUTPUTS_SUMMARY:" | sed 's/^/  /'
        echo ""
        log_warn "The build produced AAB bundles, not APKs."
        log_warn "Ensure Gradle task is assembleDebug, not bundleDebug."
    fi

    if echo "$FIND_RESULT" | grep -q "BUILD_OUTPUTS_SUMMARY:"; then
        log_warn "Build outputs found on Mac:"
        echo "$FIND_RESULT" | sed -n '/BUILD_OUTPUTS_SUMMARY:/,$p' | \
            grep -v "BUILD_OUTPUTS_SUMMARY:" | head -20
    fi

    exit 1
fi

# Create APK directory on phone
run_cmd mkdir -p "$PHONE_APK_DIR"

# Pull APK to phone with flavor-specific name
APK_NAME=$(basename "$APK_PATH")
# Rename APK to include flavor for clarity
FLAVOR_APK_NAME="scanium-${FLAVOR}-debug-${APK_ABI}.apk"
LOCAL_APK="$PHONE_APK_DIR/$FLAVOR_APK_NAME"

# Delete existing APK if present (fail fast if deletion fails)
if [[ -f "$LOCAL_APK" ]]; then
    log_info "Found existing APK: $FLAVOR_APK_NAME"
    log_info "Deleting existing APK..."
    if ! run_cmd rm -f "$LOCAL_APK"; then
        log_error "Failed to delete existing APK: $LOCAL_APK"
        exit 1
    fi
    # Verify deletion succeeded
    if [[ -f "$LOCAL_APK" ]]; then
        log_error "Existing APK still present after deletion attempt: $LOCAL_APK"
        exit 1
    fi
    log_info "Existing APK removed"
fi

log_info "Pulling ${FLAVOR_CAPITALIZED} APK to phone..."
run_cmd scp "${SSH_OPTS[@]}" "$MAC_SSH:$MAC_REPO_DIR/$APK_PATH" "$LOCAL_APK"

echo ""
log_info "${FLAVOR_CAPITALIZED} APK ready: $LOCAL_APK"
echo ""
echo "To install:"
echo "  1. Open Files app"
echo "  2. Navigate to Downloads/scanium-apk/"
echo "  3. Tap $FLAVOR_APK_NAME to install"
echo ""
echo "Build details:"
echo "  Flavor: ${FLAVOR_CAPITALIZED} (com.scanium.app.${FLAVOR})"
echo "  ABI: ${APK_ABI}"
echo "  Original: $APK_NAME"
