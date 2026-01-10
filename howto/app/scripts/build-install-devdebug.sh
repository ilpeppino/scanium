***REMOVED***!/bin/bash
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Build and Install devDebug with Connectivity Smoke Test
***REMOVED***
***REMOVED*** Builds the devDebug variant, installs it on a connected device,
***REMOVED*** prints BuildConfig values, and performs a connectivity smoke test.
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   ./scripts/android/build-install-devdebug.sh
***REMOVED***
***REMOVED*** Options:
***REMOVED***   --skip-test    Skip the connectivity smoke test
***REMOVED***   --build-only   Only build, don't install or test
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

***REMOVED*** Options
SKIP_TEST=false
BUILD_ONLY=false

***REMOVED*** Parse args
while [[ $***REMOVED*** -gt 0 ]]; do
    case $1 in
        --skip-test) SKIP_TEST=true; shift ;;
        --build-only) BUILD_ONLY=true; shift ;;
        --help|-h)
            echo "Usage: $0 [--skip-test] [--build-only]"
            echo ""
            echo "Options:"
            echo "  --skip-test    Skip connectivity smoke test"
            echo "  --build-only   Only build, don't install or test"
            exit 0
            ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

cd "$REPO_ROOT"

***REMOVED*** Mask secrets
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
echo -e "${BLUE}  Scanium: Build & Install devDebug${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""

***REMOVED*** Step 1: Validate configuration
echo -e "${YELLOW}[1/5] Validating backend configuration...${NC}"
if ! ./gradlew -q :androidApp:validateBackendConfig 2>&1; then
    echo -e "${RED}Backend configuration invalid!${NC}"
    echo "Run: ./scripts/android/set-backend-cloudflare-dev.sh"
    exit 1
fi
echo ""

***REMOVED*** Step 2: Build
echo -e "${YELLOW}[2/5] Building devDebug...${NC}"
./gradlew :androidApp:assembleDevDebug
echo -e "${GREEN}Build successful!${NC}"
echo ""

***REMOVED*** Step 3: Print BuildConfig values
echo -e "${YELLOW}[3/5] BuildConfig values:${NC}"
BUILDCONFIG="$REPO_ROOT/androidApp/build/generated/source/buildConfig/dev/debug/com/scanium/app/BuildConfig.java"

if [ -f "$BUILDCONFIG" ]; then
    API_URL=$(grep 'SCANIUM_API_BASE_URL' "$BUILDCONFIG" | grep 'String' | head -1 | sed 's/.*"\(.*\)".*/\1/')
    API_KEY_VAL=$(grep 'SCANIUM_API_KEY' "$BUILDCONFIG" | grep 'String' | sed 's/.*"\(.*\)".*/\1/')

    echo ""
    echo "┌─────────────────────────────────────────────────────────────┐"
    echo "│  BuildConfig (devDebug)                                    │"
    echo "├─────────────────────────────────────────────────────────────┤"
    printf "│  %-22s %-36s│\n" "SCANIUM_API_BASE_URL:" "$API_URL"
    printf "│  %-22s %-36s│\n" "SCANIUM_API_KEY:" "$(mask_key "$API_KEY_VAL")"
    echo "└─────────────────────────────────────────────────────────────┘"
    echo ""
else
    echo -e "${YELLOW}BuildConfig file not found (unexpected)${NC}"
fi

if [ "$BUILD_ONLY" = true ]; then
    echo -e "${GREEN}Build complete (--build-only specified)${NC}"
    exit 0
fi

***REMOVED*** Step 4: Install
echo -e "${YELLOW}[4/5] Installing to device...${NC}"

***REMOVED*** Check for connected device
if ! adb devices 2>/dev/null | grep -q "device$"; then
    echo -e "${RED}No Android device connected!${NC}"
    echo "Connect a device and enable USB debugging."
    echo ""
    echo "APK location: androidApp/build/outputs/apk/dev/debug/"
    exit 1
fi

DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
echo "Target device: $DEVICE"

***REMOVED*** Find and install APK
APK_DIR="$REPO_ROOT/androidApp/build/outputs/apk/dev/debug"
APK_FILE=$(find "$APK_DIR" -name "*.apk" -type f | head -1)

if [ -z "$APK_FILE" ]; then
    echo -e "${RED}APK not found in $APK_DIR${NC}"
    exit 1
fi

echo "Installing: $(basename "$APK_FILE")"
adb install -r "$APK_FILE"
echo -e "${GREEN}Installation successful!${NC}"
echo ""

***REMOVED*** Step 5: Connectivity smoke test
if [ "$SKIP_TEST" = true ]; then
    echo -e "${YELLOW}[5/5] Skipping connectivity test (--skip-test)${NC}"
else
    echo -e "${YELLOW}[5/5] Connectivity smoke test...${NC}"

    ***REMOVED*** Extract backend URL from BuildConfig
    if [ -z "$API_URL" ]; then
        echo -e "${YELLOW}Cannot determine backend URL, skipping test${NC}"
    else
        HEALTH_URL="${API_URL}/health"
        echo "Testing: $HEALTH_URL"
        echo ""

        ***REMOVED*** Try curl from device
        if adb shell "command -v curl" &>/dev/null; then
            echo "Using curl on device..."
            HTTP_CODE=$(adb shell "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 10 --max-time 15 '$HEALTH_URL'" 2>/dev/null || echo "000")
            HTTP_CODE=$(echo "$HTTP_CODE" | tr -d '\r\n')
        elif adb shell "command -v wget" &>/dev/null; then
            echo "Using wget on device..."
            if adb shell "wget -q -O /dev/null --timeout=10 '$HEALTH_URL'" 2>/dev/null; then
                HTTP_CODE="200"
            else
                HTTP_CODE="000"
            fi
        else
            ***REMOVED*** Fallback: try with toybox
            echo "Using toybox wget..."
            if adb shell "toybox wget -q -O /dev/null '$HEALTH_URL'" 2>/dev/null; then
                HTTP_CODE="200"
            else
                HTTP_CODE="000"
            fi
        fi

        echo ""
        case "$HTTP_CODE" in
            200)
                echo -e "${GREEN}Health check: HTTP 200 OK${NC}"
                echo -e "${GREEN}Backend is reachable from device!${NC}"
                ;;
            000)
                echo -e "${RED}Health check: Connection failed${NC}"
                echo ""
                echo "Possible causes:"
                echo "  - Device not connected to network"
                echo "  - Backend not running"
                echo "  - Cloudflare tunnel down"
                echo "  - Firewall blocking connection"
                echo ""
                echo "Try manually:"
                echo "  adb shell curl -v $HEALTH_URL"
                ;;
            *)
                echo -e "${YELLOW}Health check: HTTP $HTTP_CODE${NC}"
                echo "Backend responded but may have issues"
                ;;
        esac
    fi
fi

echo ""
echo -e "${BLUE}=============================================${NC}"
echo -e "${GREEN}Done!${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""
echo "Next steps:"
echo "  1. Open Scanium app on device"
echo "  2. Test cloud classification"
echo "  3. Check logs: adb logcat | grep -i scanium"
