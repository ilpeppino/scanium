***REMOVED***!/bin/bash
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Set Backend to Cloudflare (Development)
***REMOVED***
***REMOVED*** Configures local.properties to use the Cloudflare-exposed NAS backend
***REMOVED*** at https://scanium.gtemp1.com for devDebug builds.
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   export SCANIUM_API_KEY="your-api-key"
***REMOVED***   ./scripts/android/set-backend-cloudflare-dev.sh
***REMOVED***
***REMOVED*** Or provide URL override:
***REMOVED***   SCANIUM_API_BASE_URL_DEBUG=https://custom.example.com \
***REMOVED***   SCANIUM_API_KEY="your-key" \
***REMOVED***   ./scripts/android/set-backend-cloudflare-dev.sh
***REMOVED***
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

set -e

***REMOVED*** Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

***REMOVED*** Script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOCAL_PROPS="$REPO_ROOT/local.properties"

***REMOVED*** Default Cloudflare backend URL
DEFAULT_BACKEND_URL="https://scanium.gtemp1.com"

***REMOVED*** Get values from env or defaults
BACKEND_URL="${SCANIUM_API_BASE_URL_DEBUG:-$DEFAULT_BACKEND_URL}"
API_KEY="${SCANIUM_API_KEY:-}"

***REMOVED*** Mask API key for display
mask_key() {
    local key="$1"
    if [ -z "$key" ]; then
        echo "(not set)"
    elif [ ${***REMOVED***key} -le 8 ]; then
        echo "****"
    else
        echo "${key:0:8}..."
    fi
}

echo -e "${BLUE}=============================================${NC}"
echo -e "${BLUE}  Scanium: Set Backend to Cloudflare${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""

***REMOVED*** Validate API key
if [ -z "$API_KEY" ]; then
    echo -e "${YELLOW}Warning: SCANIUM_API_KEY not set${NC}"
    echo "  Set it with: export SCANIUM_API_KEY=\"your-api-key\""
    echo ""
fi

***REMOVED*** Function to update or add a property
update_property() {
    local key="$1"
    local value="$2"
    local file="$3"

    if grep -q "^${key}=" "$file" 2>/dev/null; then
        ***REMOVED*** Update existing (macOS sed compatible)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|^${key}=.*|${key}=${value}|" "$file"
        else
            sed -i "s|^${key}=.*|${key}=${value}|" "$file"
        fi
    else
        echo "${key}=${value}" >> "$file"
    fi
}

***REMOVED*** Create local.properties if it doesn't exist
if [ ! -f "$LOCAL_PROPS" ]; then
    echo "***REMOVED*** Scanium local configuration" > "$LOCAL_PROPS"
    echo "***REMOVED*** This file is NOT committed to git" >> "$LOCAL_PROPS"
    echo "" >> "$LOCAL_PROPS"
    echo -e "${GREEN}Created: local.properties${NC}"
fi

***REMOVED*** Update properties
echo -e "${YELLOW}Updating local.properties...${NC}"

***REMOVED*** Set debug URL (primary for devDebug builds)
update_property "scanium.api.base.url.debug" "$BACKEND_URL" "$LOCAL_PROPS"

***REMOVED*** Also set main URL as fallback
update_property "scanium.api.base.url" "$BACKEND_URL" "$LOCAL_PROPS"

***REMOVED*** Set API key if provided
if [ -n "$API_KEY" ]; then
    update_property "scanium.api.key" "$API_KEY" "$LOCAL_PROPS"
fi

echo ""
echo -e "${GREEN}Configuration saved!${NC}"
echo ""
echo "┌─────────────────────────────────────────────────────────────┐"
echo "│  Cloudflare Backend Configuration                          │"
echo "├─────────────────────────────────────────────────────────────┤"
printf "│  %-20s %-38s│\n" "Backend URL:" "$BACKEND_URL"
printf "│  %-20s %-38s│\n" "API Key:" "$(mask_key "$API_KEY")"
echo "└─────────────────────────────────────────────────────────────┘"
echo ""

***REMOVED*** Verify with Gradle
echo -e "${YELLOW}Verifying Gradle can read configuration...${NC}"
cd "$REPO_ROOT"
if ./gradlew -q :androidApp:validateBackendConfig 2>/dev/null; then
    echo ""
    echo -e "${GREEN}Configuration verified successfully!${NC}"
else
    echo -e "${YELLOW}Could not run Gradle validation (non-fatal)${NC}"
fi

echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Build and install:"
echo "     ./scripts/android/build-install-devdebug.sh"
echo ""
echo "  2. Or manually:"
echo "     ./gradlew :androidApp:installDevDebug"
echo ""
