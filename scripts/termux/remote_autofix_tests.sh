***REMOVED***!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

***REMOVED*** Run autofix_tests.sh on remote Mac via SSH over Tailscale
***REMOVED*** Pulls back test artifacts to phone Downloads

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
PHONE_ARTIFACT_DIR="${PHONE_ARTIFACT_DIR:-~/storage/downloads/scanium-ci}"
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

***REMOVED*** Run autofix_tests.sh on Mac
log_info "Running autofix_tests.sh on Mac..."
echo ""

if ! run_cmd ssh "${SSH_OPTS[@]}" "$MAC_SSH" "cd $MAC_REPO_DIR && ./scripts/dev/autofix_tests.sh test"; then
    log_warn "autofix_tests.sh exited with non-zero status (tests may still be failing)"
fi

***REMOVED*** Create artifact directory on phone
run_cmd mkdir -p "$PHONE_ARTIFACT_DIR"

***REMOVED*** Pull artifacts from Mac
log_info "Pulling test artifacts..."

ARTIFACTS=(
    "tmp/gradle_test.log"
    "tmp/test_failures.txt"
)

for artifact in "${ARTIFACTS[@]}"; do
    remote_path="$MAC_REPO_DIR/$artifact"
    local_path="$PHONE_ARTIFACT_DIR/$(basename "$artifact")"

    ***REMOVED*** Check if file exists on Mac
    if run_cmd ssh "${SSH_OPTS[@]}" "$MAC_SSH" "test -f $remote_path" 2>/dev/null; then
        log_info "Pulling $artifact..."
        run_cmd scp "${SSH_OPTS[@]}" "$MAC_SSH:$remote_path" "$local_path"
        log_info "  -> $local_path"
    else
        log_warn "Artifact not found: $artifact"
    fi
done

echo ""
log_info "Done. Artifacts in: $PHONE_ARTIFACT_DIR"
