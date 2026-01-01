***REMOVED***!/bin/bash
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Scanium Android Backend Configuration Script
***REMOVED***
***REMOVED*** Purpose: Configure the Android app to connect to a backend server.
***REMOVED***          Writes configuration to local.properties (never committed to git).
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   ./scripts/android-configure-backend-dev.sh [OPTIONS]
***REMOVED***
***REMOVED*** Options:
***REMOVED***   --url URL       Backend base URL (e.g., https://api.example.com)
***REMOVED***   --key KEY       API key for authentication
***REMOVED***   --debug-url URL Debug-specific URL (optional, for LAN development)
***REMOVED***   --help          Show this help message
***REMOVED***
***REMOVED*** Environment Variables (alternative to options):
***REMOVED***   SCANIUM_API_BASE_URL        Backend base URL
***REMOVED***   SCANIUM_API_BASE_URL_DEBUG  Debug-specific URL
***REMOVED***   SCANIUM_API_KEY             API key
***REMOVED***
***REMOVED*** Examples:
***REMOVED***   ***REMOVED*** Configure via Cloudflare hostname
***REMOVED***   ./scripts/android-configure-backend-dev.sh \
***REMOVED***       --url https://scanium-api.example.com \
***REMOVED***       --key your-api-key
***REMOVED***
***REMOVED***   ***REMOVED*** Configure with separate LAN URL for debug builds
***REMOVED***   ./scripts/android-configure-backend-dev.sh \
***REMOVED***       --url https://scanium-api.example.com \
***REMOVED***       --debug-url http://192.168.1.100:3000 \
***REMOVED***       --key your-api-key
***REMOVED***
***REMOVED***   ***REMOVED*** Configure via environment variables
***REMOVED***   export SCANIUM_API_BASE_URL=https://scanium-api.example.com
***REMOVED***   export SCANIUM_API_KEY=your-api-key
***REMOVED***   ./scripts/android-configure-backend-dev.sh
***REMOVED***
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

set -e

***REMOVED*** Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' ***REMOVED*** No Color

***REMOVED*** Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOCAL_PROPS="$REPO_ROOT/local.properties"

***REMOVED*** Mask API key for display (show first 8 chars)
mask_key() {
    local key="$1"
    if [ -z "$key" ]; then
        echo "(not set)"
    elif [ ${***REMOVED***key} -le 8 ]; then
        echo "***"
    else
        echo "${key:0:8}..."
    fi
}

***REMOVED*** Print usage
usage() {
    cat << 'EOF'
Usage: ./scripts/android-configure-backend-dev.sh [OPTIONS]

Options:
  --url URL       Backend base URL (e.g., https://api.example.com)
  --key KEY       API key for authentication
  --debug-url URL Debug-specific URL (optional, for LAN development)
  --help          Show this help message

Environment Variables (alternative to options):
  SCANIUM_API_BASE_URL        Backend base URL
  SCANIUM_API_BASE_URL_DEBUG  Debug-specific URL
  SCANIUM_API_KEY             API key

Examples:
  ***REMOVED*** Configure via Cloudflare hostname
  ./scripts/android-configure-backend-dev.sh \
      --url https://scanium-api.example.com \
      --key your-api-key

  ***REMOVED*** Configure with separate LAN URL for debug builds
  ./scripts/android-configure-backend-dev.sh \
      --url https://scanium-api.example.com \
      --debug-url http://192.168.1.100:3000 \
      --key your-api-key
EOF
    exit 0
}

***REMOVED*** Parse arguments
BACKEND_URL=""
DEBUG_URL=""
API_KEY=""

while [[ $***REMOVED*** -gt 0 ]]; do
    case $1 in
        --url)
            BACKEND_URL="$2"
            shift 2
            ;;
        --debug-url)
            DEBUG_URL="$2"
            shift 2
            ;;
        --key)
            API_KEY="$2"
            shift 2
            ;;
        --help|-h)
            usage
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            ;;
    esac
done

***REMOVED*** Fall back to environment variables if not provided as arguments
BACKEND_URL="${BACKEND_URL:-$SCANIUM_API_BASE_URL}"
DEBUG_URL="${DEBUG_URL:-$SCANIUM_API_BASE_URL_DEBUG}"
API_KEY="${API_KEY:-$SCANIUM_API_KEY}"

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Scanium Backend Configuration${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

***REMOVED*** Validate required inputs
if [ -z "$BACKEND_URL" ]; then
    echo -e "${RED}Error: Backend URL is required.${NC}"
    echo ""
    echo "Provide via --url option or SCANIUM_API_BASE_URL environment variable."
    echo ""
    echo "Example:"
    echo "  ./scripts/android-configure-backend-dev.sh --url https://api.example.com --key your-key"
    echo ""
    exit 1
fi

***REMOVED*** Validate URL format
if [[ ! "$BACKEND_URL" =~ ^https?:// ]]; then
    echo -e "${RED}Error: Invalid URL format: $BACKEND_URL${NC}"
    echo "URL must start with http:// or https://"
    exit 1
fi

if [ -n "$DEBUG_URL" ] && [[ ! "$DEBUG_URL" =~ ^https?:// ]]; then
    echo -e "${RED}Error: Invalid debug URL format: $DEBUG_URL${NC}"
    echo "URL must start with http:// or https://"
    exit 1
fi

***REMOVED*** Create or update local.properties
echo -e "${YELLOW}Updating local.properties...${NC}"
echo ""

***REMOVED*** Ensure local.properties exists
if [ ! -f "$LOCAL_PROPS" ]; then
    echo "***REMOVED*** Scanium local configuration" > "$LOCAL_PROPS"
    echo "***REMOVED*** This file is NOT committed to git" >> "$LOCAL_PROPS"
    echo "" >> "$LOCAL_PROPS"
fi

***REMOVED*** Function to update or add a property
update_property() {
    local key="$1"
    local value="$2"
    local file="$3"

    if grep -q "^${key}=" "$file" 2>/dev/null; then
        ***REMOVED*** Update existing property (macOS-compatible sed)
        if [[ "$OSTYPE" == "darwin"* ]]; then
            sed -i '' "s|^${key}=.*|${key}=${value}|" "$file"
        else
            sed -i "s|^${key}=.*|${key}=${value}|" "$file"
        fi
    else
        ***REMOVED*** Add new property
        echo "${key}=${value}" >> "$file"
    fi
}

***REMOVED*** Update properties
update_property "scanium.api.base.url" "$BACKEND_URL" "$LOCAL_PROPS"

if [ -n "$DEBUG_URL" ]; then
    update_property "scanium.api.base.url.debug" "$DEBUG_URL" "$LOCAL_PROPS"
fi

if [ -n "$API_KEY" ]; then
    update_property "scanium.api.key" "$API_KEY" "$LOCAL_PROPS"
fi

***REMOVED*** Print summary
echo -e "${GREEN}Configuration saved to local.properties${NC}"
echo ""
echo "┌─────────────────────────────────────────────────────────────┐"
echo "│  Configuration Summary                                     │"
echo "├─────────────────────────────────────────────────────────────┤"
printf "│  %-20s %-38s│\n" "Backend URL:" "$BACKEND_URL"
if [ -n "$DEBUG_URL" ]; then
    printf "│  %-20s %-38s│\n" "Debug URL:" "$DEBUG_URL"
else
    printf "│  %-20s %-38s│\n" "Debug URL:" "(uses Backend URL)"
fi
printf "│  %-20s %-38s│\n" "API Key:" "$(mask_key "$API_KEY")"
echo "└─────────────────────────────────────────────────────────────┘"
echo ""

***REMOVED*** Verify configuration
echo -e "${YELLOW}Verifying configuration...${NC}"
echo ""

***REMOVED*** Run Gradle validation task
if command -v ./gradlew &> /dev/null; then
    if ./gradlew -q :androidApp:validateBackendConfig 2>/dev/null; then
        echo ""
        echo -e "${GREEN}Configuration validated successfully!${NC}"
    else
        echo -e "${YELLOW}Could not run Gradle validation (this is OK for initial setup)${NC}"
    fi
fi

echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Build and install the app:"
echo "     ./scripts/android-build-install-dev.sh"
echo ""
echo "  2. Or build manually:"
echo "     ./gradlew :androidApp:installDevDebug"
echo ""
echo "  3. Verify connectivity from device:"
echo "     ./scripts/dev/verify-backend-config.sh devDebug"
echo ""
