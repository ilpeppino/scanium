***REMOVED***!/usr/bin/env bash

***REMOVED*** Deterministic build + install + verify script for devDebug variant
***REMOVED*** Ensures the installed APK always matches the current git HEAD SHA
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   ./scripts/dev/install_dev_debug.sh [--uninstall]
***REMOVED***
***REMOVED*** Options:
***REMOVED***   --uninstall    Uninstall existing app before installing

set -euo pipefail

***REMOVED*** Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' ***REMOVED*** No Color

UNINSTALL=false
if [[ "${1:-}" == "--uninstall" ]]; then
    UNINSTALL=true
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

APP_MODULE="androidApp"
VARIANT="devDebug"
FLAVOR="dev"
BUILD_TYPE="debug"
APPLICATION_ID="com.scanium.app.dev"

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Scanium Dev Build + Install + Verify${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

***REMOVED*** Step 1: Compute expected SHA
echo -e "\n${BLUE}[1/8] Computing expected git SHA...${NC}"
EXPECTED_SHA=$(git rev-parse --short HEAD)
if [[ -z "$EXPECTED_SHA" ]]; then
    echo -e "${RED}ERROR: Failed to get git SHA${NC}"
    exit 1
fi
echo -e "Expected SHA: ${GREEN}$EXPECTED_SHA${NC}"

***REMOVED*** Step 2: Check for connected device
echo -e "\n${BLUE}[2/8] Checking for connected device...${NC}"
if ! adb devices | grep -q 'device$'; then
    echo -e "${RED}ERROR: No device connected. Connect a device and enable USB debugging.${NC}"
    adb devices -l
    exit 1
fi

DEVICE_SERIAL=$(adb devices | grep 'device$' | head -1 | awk '{print $1}')
echo -e "Device: ${GREEN}$DEVICE_SERIAL${NC}"

***REMOVED*** Step 3: Detect device ABI
echo -e "\n${BLUE}[3/8] Detecting device ABI...${NC}"
DEVICE_ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r\n')
if [[ -z "$DEVICE_ABI" ]]; then
    echo -e "${RED}ERROR: Failed to detect device ABI${NC}"
    exit 1
fi
echo -e "Device ABI: ${GREEN}$DEVICE_ABI${NC}"

***REMOVED*** Step 4: Stop gradle daemons and clean
echo -e "\n${BLUE}[4/8] Stopping gradle daemons and cleaning...${NC}"
./gradlew --stop
./gradlew clean --console=plain
echo -e "${GREEN}Gradle daemons stopped and build outputs cleaned${NC}"

***REMOVED*** Step 5: Build the APK (force rebuild to ensure current git SHA)
echo -e "\n${BLUE}[5/8] Building $VARIANT variant...${NC}"
echo -e "${YELLOW}Note: Using --rerun-tasks to ensure git SHA matches current HEAD${NC}"
./gradlew ":$APP_MODULE:assemble$VARIANT" --no-daemon --console=plain --rerun-tasks

***REMOVED*** Step 6: Locate the APK deterministically
echo -e "\n${BLUE}[6/8] Locating APK...${NC}"
APK_OUTPUT_DIR="$PROJECT_ROOT/$APP_MODULE/build/outputs/apk/$FLAVOR/$BUILD_TYPE"
APK_FILE="$APK_OUTPUT_DIR/$APP_MODULE-$FLAVOR-$DEVICE_ABI-$BUILD_TYPE.apk"

if [[ ! -f "$APK_FILE" ]]; then
    echo -e "${RED}ERROR: APK not found at expected path:${NC}"
    echo -e "${RED}  $APK_FILE${NC}"
    echo -e "\n${YELLOW}Available APKs in output directory:${NC}"
    ls -lh "$APK_OUTPUT_DIR" || echo "Directory not found"
    exit 1
fi

APK_SIZE=$(du -h "$APK_FILE" | awk '{print $1}')
echo -e "APK: ${GREEN}$APK_FILE${NC} (${APK_SIZE})"

***REMOVED*** Step 7: Optionally uninstall, then install
if [[ "$UNINSTALL" == "true" ]]; then
    echo -e "\n${BLUE}[7/8] Uninstalling existing app...${NC}"
    if adb shell pm list packages | grep -q "^package:$APPLICATION_ID\$"; then
        adb uninstall "$APPLICATION_ID" || true
        echo -e "${GREEN}Uninstalled $APPLICATION_ID${NC}"
    else
        echo -e "${YELLOW}App not installed, skipping uninstall${NC}"
    fi
else
    echo -e "\n${BLUE}[7/8] Installing APK (upgrade)...${NC}"
fi

echo -e "${BLUE}Installing $APK_FILE...${NC}"
adb install -r "$APK_FILE"
echo -e "${GREEN}Install completed${NC}"

***REMOVED*** Step 8: Verify installed SHA matches expected SHA
echo -e "\n${BLUE}[8/8] Verifying installed build SHA...${NC}"

***REMOVED*** Clear logcat buffer
adb logcat -c

***REMOVED*** Trigger the debug broadcast receiver (explicit component required for Android 8+)
adb shell am broadcast -n "$APPLICATION_ID/com.scanium.app.debug.BuildInfoReceiver" -a com.scanium.app.DEBUG_BUILD_INFO > /dev/null 2>&1 || {
    echo -e "${RED}ERROR: Failed to trigger debug broadcast${NC}"
    echo -e "${YELLOW}This may indicate the BuildInfoReceiver is not registered.${NC}"
    exit 1
}

***REMOVED*** Wait briefly for logcat output
sleep 1

***REMOVED*** Read the build info from logcat
BUILD_INFO=$(adb logcat -d -s BuildInfoReceiver:I | grep "BUILD_INFO|" | tail -1)

if [[ -z "$BUILD_INFO" ]]; then
    echo -e "${RED}ERROR: Failed to read build info from device${NC}"
    echo -e "${YELLOW}Logcat output (BuildInfoReceiver):${NC}"
    adb logcat -d -s BuildInfoReceiver:*
    echo -e "\n${YELLOW}Diagnostics:${NC}"
    echo -e "  Expected broadcast action: com.scanium.app.DEBUG_BUILD_INFO"
    echo -e "  Application ID: $APPLICATION_ID"
    echo -e "  Installed packages matching scanium:"
    adb shell pm list packages | grep scanium || echo "  None found"
    exit 1
fi

echo -e "${GREEN}Received build info from device${NC}"

***REMOVED*** Parse the build info
***REMOVED*** Format: BUILD_INFO|packageName|versionName|versionCode|flavor|buildType|gitSha|buildTime
INSTALLED_PACKAGE=$(echo "$BUILD_INFO" | cut -d'|' -f2)
INSTALLED_VERSION_NAME=$(echo "$BUILD_INFO" | cut -d'|' -f3)
INSTALLED_VERSION_CODE=$(echo "$BUILD_INFO" | cut -d'|' -f4)
INSTALLED_FLAVOR=$(echo "$BUILD_INFO" | cut -d'|' -f5)
INSTALLED_BUILD_TYPE=$(echo "$BUILD_INFO" | cut -d'|' -f6)
INSTALLED_SHA=$(echo "$BUILD_INFO" | cut -d'|' -f7)
INSTALLED_BUILD_TIME=$(echo "$BUILD_INFO" | cut -d'|' -f8)

echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Verification Results${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "Package:      ${GREEN}$INSTALLED_PACKAGE${NC}"
echo -e "Version:      ${GREEN}$INSTALLED_VERSION_NAME ($INSTALLED_VERSION_CODE)${NC}"
echo -e "Flavor:       ${GREEN}$INSTALLED_FLAVOR${NC}"
echo -e "Build Type:   ${GREEN}$INSTALLED_BUILD_TYPE${NC}"
echo -e "Build Time:   ${GREEN}$INSTALLED_BUILD_TIME${NC}"
echo -e "Expected SHA: ${GREEN}$EXPECTED_SHA${NC}"
echo -e "Installed SHA: ${GREEN}$INSTALLED_SHA${NC}"

***REMOVED*** Compare SHAs
if [[ "$INSTALLED_SHA" == "$EXPECTED_SHA" ]]; then
    echo -e "\n${GREEN}✓ SUCCESS: Installed SHA matches expected SHA${NC}"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    exit 0
else
    echo -e "\n${RED}✗ FAILURE: SHA mismatch!${NC}"
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "\n${YELLOW}Diagnostics:${NC}"
    echo -e "  APK installed from: $APK_FILE"
    echo -e "  Device package path:"
    adb shell pm path "$APPLICATION_ID" || echo "  Failed to query"
    echo -e "\n${YELLOW}Possible causes:${NC}"
    echo -e "  1. The build used stale git information (Gradle config cache issue)"
    echo -e "  2. Wrong APK was installed (check APK_FILE path above)"
    echo -e "  3. Installed package was from a different commit"
    echo -e "\n${YELLOW}Recommended actions:${NC}"
    echo -e "  1. Run: ./gradlew --stop"
    echo -e "  2. Clean build outputs: ./gradlew clean"
    echo -e "  3. Re-run this script with --uninstall flag"
    exit 1
fi
