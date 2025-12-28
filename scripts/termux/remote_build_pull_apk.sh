***REMOVED***!/usr/bin/env bash
set -euo pipefail

***REMOVED*** Build debug APK on remote Mac via SSH, then pull to phone Downloads

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/remote_env"

***REMOVED*** SSH options for mobile network resilience
SSH_OPTS=(
    -o ServerAliveInterval=30
    -o ServerAliveCountMax=3
    -o ConnectTimeout=15
    -o BatchMode=yes
)

***REMOVED*** Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

***REMOVED*** Load remote_env
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

***REMOVED*** shellcheck source=/dev/null
source "$ENV_FILE"

***REMOVED*** Validate required variables
if [[ -z "${MAC_SSH_HOST:-}" || -z "${MAC_SSH_USER:-}" ]]; then
    log_error "MAC_SSH_HOST and MAC_SSH_USER must be set in remote_env"
    exit 1
fi

MAC_SSH="${MAC_SSH_USER}@${MAC_SSH_HOST}"
MAC_REPO_DIR="${MAC_REPO_DIR:-~/dev/scanium}"
PHONE_APK_DIR="${PHONE_APK_DIR:-~/storage/downloads/scanium-apk}"
DRY_RUN="${DRY_RUN:-0}"

run_cmd() {
    if [[ "$DRY_RUN" == "1" ]]; then
        echo "[DRY_RUN] $*"
    else
        "$@"
    fi
}

***REMOVED*** Test SSH connection
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

***REMOVED*** Build APK on Mac (try :androidApp first, then fall back to :app)
log_info "Building debug APK on Mac..."
echo ""

BUILD_SCRIPT='
cd '"$MAC_REPO_DIR"'
if ./gradlew :androidApp:assembleDebug 2>/dev/null; then
    echo "BUILD_SUCCESS"
elif ./gradlew assembleDebug 2>/dev/null; then
    echo "BUILD_SUCCESS"
else
    echo "BUILD_FAILED"
    exit 1
fi
'

if ! run_cmd ssh "${SSH_OPTS[@]}" "$MAC_SSH" "$BUILD_SCRIPT"; then
    log_error "APK build failed on Mac"
    exit 1
fi

***REMOVED*** Find newest debug APK on Mac
log_info "Locating debug APK..."

FIND_APK_SCRIPT='
cd '"$MAC_REPO_DIR"'
find . -path "*/build/outputs/apk/**/debug/*.apk" -type f 2>/dev/null | \
    xargs ls -t 2>/dev/null | \
    head -1
'

APK_PATH=$(run_cmd ssh "${SSH_OPTS[@]}" "$MAC_SSH" "$FIND_APK_SCRIPT")

if [[ -z "$APK_PATH" ]]; then
    log_error "No debug APK found on Mac"
    log_warn "Expected paths:"
    log_warn "  androidApp/build/outputs/apk/**/debug/*.apk"
    log_warn "  app/build/outputs/apk/**/debug/*.apk"
    exit 1
fi

log_info "Found APK: $APK_PATH"

***REMOVED*** Create APK directory on phone
run_cmd mkdir -p "$PHONE_APK_DIR"

***REMOVED*** Pull APK to phone
APK_NAME=$(basename "$APK_PATH")
LOCAL_APK="$PHONE_APK_DIR/$APK_NAME"

log_info "Pulling APK to phone..."
run_cmd scp "${SSH_OPTS[@]}" "$MAC_SSH:$MAC_REPO_DIR/$APK_PATH" "$LOCAL_APK"

echo ""
log_info "APK ready: $LOCAL_APK"
echo ""
echo "To install:"
echo "  1. Open Files app"
echo "  2. Navigate to Downloads/scanium-apk/"
echo "  3. Tap $APK_NAME to install"
