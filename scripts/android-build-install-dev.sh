#!/bin/bash
##############################################################################
# Scanium Android Build & Install Script (Dev Debug)
#
# Purpose: Build and install the devDebug variant of the Android app,
#          displaying the final BuildConfig values for verification.
#
# Usage:
#   ./scripts/android-build-install-dev.sh [OPTIONS]
#
# Options:
#   --build-only    Only build, don't install
#   --install-only  Only install (assumes already built)
#   --clean         Run clean before building
#   --help          Show this help message
#
# Requirements:
#   - Android device connected via USB (for install)
#   - Backend configured in local.properties or environment
#
# Example:
#   ./scripts/android-build-install-dev.sh
#   ./scripts/android-build-install-dev.sh --clean
#   ./scripts/android-build-install-dev.sh --build-only
#
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Options
BUILD=true
INSTALL=true
CLEAN=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --build-only)
            INSTALL=false
            shift
            ;;
        --install-only)
            BUILD=false
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --help|-h)
            cat << 'EOF'
Usage: ./scripts/android-build-install-dev.sh [OPTIONS]

Options:
  --build-only    Only build, don't install
  --install-only  Only install (assumes already built)
  --clean         Run clean before building
  --help          Show this help message

Requirements:
  - Android device connected via USB (for install)
  - Backend configured in local.properties or environment

Examples:
  ./scripts/android-build-install-dev.sh
  ./scripts/android-build-install-dev.sh --clean
  ./scripts/android-build-install-dev.sh --build-only
EOF
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

cd "$REPO_ROOT"

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Scanium Android Build & Install (devDebug)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Step 1: Validate configuration
echo -e "${YELLOW}Step 1: Validating backend configuration...${NC}"
./gradlew -q :androidApp:validateBackendConfig || {
    echo -e "${RED}Backend configuration validation failed.${NC}"
    echo ""
    echo "Run the configuration script first:"
    echo "  ./scripts/android-configure-backend-dev.sh --url YOUR_URL --key YOUR_KEY"
    exit 1
}

# Step 2: Clean if requested
if [ "$CLEAN" = true ]; then
    echo ""
    echo -e "${YELLOW}Step 2: Cleaning build...${NC}"
    ./gradlew :androidApp:clean
fi

# Step 3: Build
if [ "$BUILD" = true ]; then
    echo ""
    echo -e "${YELLOW}Step 3: Building devDebug variant...${NC}"
    ./gradlew :androidApp:assembleDevDebug

    echo ""
    echo -e "${GREEN}Build completed successfully!${NC}"
fi

# Step 4: Print BuildConfig values
echo ""
echo -e "${YELLOW}Step 4: Verifying BuildConfig values...${NC}"

BUILDCONFIG_PATH="$REPO_ROOT/androidApp/build/generated/source/buildConfig/dev/debug/com/scanium/app/BuildConfig.java"

if [ -f "$BUILDCONFIG_PATH" ]; then
    # Extract and display relevant BuildConfig fields
    API_URL=$(grep 'SCANIUM_API_BASE_URL' "$BUILDCONFIG_PATH" | grep 'String' | sed 's/.*"\(.*\)".*/\1/' | head -1)
    CLASSIFIER_URL=$(grep 'CLOUD_CLASSIFIER_URL' "$BUILDCONFIG_PATH" | grep 'String' | sed 's/.*"\(.*\)".*/\1/')
    API_KEY=$(grep 'SCANIUM_API_KEY' "$BUILDCONFIG_PATH" | grep 'String' | sed 's/.*"\(.*\)".*/\1/')

    # Mask API key
    if [ -z "$API_KEY" ]; then
        MASKED_KEY="(not set)"
    elif [ ${#API_KEY} -le 8 ]; then
        MASKED_KEY="***"
    else
        MASKED_KEY="${API_KEY:0:8}..."
    fi

    echo ""
    echo "┌─────────────────────────────────────────────────────────────┐"
    echo "│  BuildConfig Values (devDebug)                             │"
    echo "├─────────────────────────────────────────────────────────────┤"
    printf "│  %-22s %-36s│\n" "SCANIUM_API_BASE_URL:" "$API_URL"
    printf "│  %-22s %-36s│\n" "CLOUD_CLASSIFIER_URL:" "$CLASSIFIER_URL"
    printf "│  %-22s %-36s│\n" "SCANIUM_API_KEY:" "$MASKED_KEY"
    echo "└─────────────────────────────────────────────────────────────┘"
else
    echo -e "${YELLOW}BuildConfig not found. This is expected for first build.${NC}"
fi

# Step 5: Install
if [ "$INSTALL" = true ]; then
    echo ""
    echo -e "${YELLOW}Step 5: Installing to connected device...${NC}"

    # Check for connected device
    if ! adb devices | grep -q "device$"; then
        echo -e "${RED}No Android device connected.${NC}"
        echo "Connect a device via USB and enable USB debugging."
        echo ""
        echo "APK location: $REPO_ROOT/androidApp/build/outputs/apk/dev/debug/"
        exit 1
    fi

    ./gradlew :androidApp:installDevDebug

    echo ""
    echo -e "${GREEN}App installed successfully!${NC}"

    # Get device info
    DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
    echo "Installed to device: $DEVICE"
fi

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}Done!${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [ "$INSTALL" = true ]; then
    echo -e "${BLUE}Next steps:${NC}"
    echo "  1. Open the Scanium app on your device"
    echo "  2. Test backend connectivity via the app"
    echo "  3. Check logs: adb logcat | grep -i scanium"
    echo ""
    echo "  To verify from device:"
    echo "  ./scripts/dev/verify-backend-config.sh devDebug"
fi
