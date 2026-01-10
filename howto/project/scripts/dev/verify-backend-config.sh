***REMOVED***!/bin/bash
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** Scanium Backend Configuration Verification Script
***REMOVED***
***REMOVED*** Purpose: Verify that the Android app is correctly configured to reach
***REMOVED***          the backend in both LAN and remote modes.
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   ./scripts/dev/verify-backend-config.sh [variant]
***REMOVED***
***REMOVED*** Arguments:
***REMOVED***   variant - Optional build variant to check (devDebug, devRelease, betaDebug, betaRelease)
***REMOVED***             If not specified, checks all variants
***REMOVED***
***REMOVED*** Requirements:
***REMOVED***   - Android device connected via USB with ADB
***REMOVED***   - App built (at least once)
***REMOVED***   - local.properties configured
***REMOVED***
***REMOVED*** Example:
***REMOVED***   ./scripts/dev/verify-backend-config.sh devDebug
***REMOVED***
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

set -e  ***REMOVED*** Exit on error

***REMOVED*** Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' ***REMOVED*** No Color

***REMOVED*** Counters
PASS=0
FAIL=0
WARN=0

***REMOVED*** Helper functions
print_header() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_pass() {
    echo -e "${GREEN}✓ $1${NC}"
    ((PASS++))
}

print_fail() {
    echo -e "${RED}✗ $1${NC}"
    ((FAIL++))
}

print_warn() {
    echo -e "${YELLOW}⚠ $1${NC}"
    ((WARN++))
}

print_info() {
    echo -e "  $1"
}

***REMOVED*** Parse arguments
VARIANT="${1:-all}"

print_header "Scanium Backend Configuration Verification"

echo "Checking configuration for: ${VARIANT}"
echo "Date: $(date)"
echo ""

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** 1. Check Prerequisites
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

print_header "1. Prerequisites"

***REMOVED*** Check if adb is installed
if command -v adb &> /dev/null; then
    print_pass "adb is installed"
else
    print_fail "adb is not installed. Install Android SDK Platform Tools."
    exit 1
fi

***REMOVED*** Check if device is connected
if adb devices | grep -q "device$"; then
    DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
    print_pass "Android device connected: $DEVICE"
else
    print_fail "No Android device connected. Connect device via USB and enable USB debugging."
    exit 1
fi

***REMOVED*** Check if local.properties exists
if [ -f "local.properties" ]; then
    print_pass "local.properties exists"
else
    print_fail "local.properties not found. Create it in the repository root."
    exit 1
fi

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** 2. Check Configuration Values
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

print_header "2. Configuration Values in local.properties"

***REMOVED*** Read configuration
API_BASE_URL=$(grep "^scanium.api.base.url=" local.properties 2>/dev/null | cut -d= -f2- || echo "")
API_BASE_URL_DEBUG=$(grep "^scanium.api.base.url.debug=" local.properties 2>/dev/null | cut -d= -f2- || echo "")
API_KEY=$(grep "^scanium.api.key=" local.properties 2>/dev/null | cut -d= -f2- || echo "")

if [ -n "$API_BASE_URL" ]; then
    print_pass "scanium.api.base.url is set"
    print_info "Value: $API_BASE_URL"
else
    print_fail "scanium.api.base.url is not set"
fi

if [ -n "$API_BASE_URL_DEBUG" ]; then
    print_pass "scanium.api.base.url.debug is set"
    print_info "Value: $API_BASE_URL_DEBUG"
else
    print_warn "scanium.api.base.url.debug is not set (will fall back to scanium.api.base.url)"
fi

if [ -n "$API_KEY" ]; then
    print_pass "scanium.api.key is set"
    print_info "Value: ${API_KEY:0:8}..." ***REMOVED*** Show only first 8 chars
else
    print_warn "scanium.api.key is not set"
fi

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** 3. Check BuildConfig for Each Variant
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

check_buildconfig_for_variant() {
    local variant=$1
    local flavor=${variant%Debug}
    flavor=${flavor%Release}
    local buildType=${variant***REMOVED***$flavor}
    buildType=$(echo "$buildType" | tr '[:upper:]' '[:lower:]')

    print_header "3. BuildConfig for $variant"

    local buildconfig_path="androidApp/build/generated/source/buildConfig/$flavor/$buildType/com/scanium/app/BuildConfig.java"

    if [ ! -f "$buildconfig_path" ]; then
        print_warn "BuildConfig not found for $variant. Build it first:"
        print_info "./gradlew :androidApp:assemble${variant^}"
        return
    fi

    ***REMOVED*** Extract SCANIUM_API_BASE_URL
    local base_url=$(grep "SCANIUM_API_BASE_URL" "$buildconfig_path" | grep "String" | sed 's/.*"\(.*\)".*/\1/')

    if [ -n "$base_url" ]; then
        print_pass "SCANIUM_API_BASE_URL resolved"
        print_info "Value: $base_url"

        ***REMOVED*** Check if URL is appropriate for build type
        if [[ "$buildType" == "debug" ]]; then
            if [[ "$base_url" =~ ^http://.*$ ]]; then
                print_pass "Debug build uses HTTP (OK for LAN)"
            elif [[ "$base_url" =~ ^https://.*$ ]]; then
                print_warn "Debug build uses HTTPS (consider using LAN HTTP for faster dev)"
            else
                print_fail "Invalid URL format: $base_url"
            fi
        else
            if [[ "$base_url" =~ ^https://.*$ ]]; then
                print_pass "Release build uses HTTPS (required)"
            elif [[ "$base_url" =~ ^http://.*$ ]]; then
                print_fail "Release build uses HTTP (INSECURE - will fail on device)"
            else
                print_fail "Invalid URL format: $base_url"
            fi
        fi
    else
        print_fail "SCANIUM_API_BASE_URL is empty in BuildConfig"
    fi

    ***REMOVED*** Extract CLOUD_CLASSIFIER_URL (legacy)
    local classifier_url=$(grep "CLOUD_CLASSIFIER_URL" "$buildconfig_path" | grep "String" | sed 's/.*"\(.*\)".*/\1/')
    if [ -n "$classifier_url" ]; then
        print_info "CLOUD_CLASSIFIER_URL: $classifier_url"
    fi

    ***REMOVED*** Store base_url for later network tests
    eval "${variant}_URL=\"$base_url\""
}

if [ "$VARIANT" = "all" ]; then
    for v in devDebug devRelease betaDebug betaRelease; do
        check_buildconfig_for_variant "$v"
    done
else
    check_buildconfig_for_variant "$VARIANT"
fi

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** 4. Check Network Connectivity from Device
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

print_header "4. Network Connectivity from Device"

***REMOVED*** Function to test URL from device
test_url_from_device() {
    local url=$1
    local label=$2

    if [ -z "$url" ]; then
        print_warn "$label: URL not configured, skipping"
        return
    fi

    ***REMOVED*** Extract host and port from URL
    local host=$(echo "$url" | sed -E 's|^https?://([^:/]+).*|\1|')
    local port=$(echo "$url" | sed -E 's|^https?://[^:]+:([0-9]+).*|\1|')

    ***REMOVED*** Default ports
    if [[ "$url" =~ ^https:// ]] && [ "$port" = "$url" ]; then
        port=443
    elif [[ "$url" =~ ^http:// ]] && [ "$port" = "$url" ]; then
        port=80
    fi

    print_info "Testing: $url"

    ***REMOVED*** Test ping (for LAN connectivity check)
    if [[ "$url" =~ ^http://.*$ ]]; then
        if adb shell "ping -c 1 -W 2 $host" &> /dev/null; then
            print_pass "$label: Device can ping $host"
        else
            print_warn "$label: Device cannot ping $host (might be firewall/ICMP blocked)"
        fi
    fi

    ***REMOVED*** Test HTTP/HTTPS endpoint
    local health_url="${url}/health"
    if adb shell "command -v curl" &> /dev/null; then
        local http_code=$(adb shell "curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 '$health_url' 2>/dev/null" || echo "000")

        if [ "$http_code" = "200" ]; then
            print_pass "$label: Backend /health returned 200 OK"
        elif [ "$http_code" = "000" ]; then
            print_fail "$label: Failed to connect to backend (timeout or connection refused)"
        elif [ "$http_code" = "404" ]; then
            print_warn "$label: Backend responded with 404 (health endpoint may not exist)"
        else
            print_warn "$label: Backend responded with HTTP $http_code"
        fi
    else
        print_warn "$label: curl not available on device, cannot test HTTP endpoint"
    fi
}

***REMOVED*** Test LAN URL (from debug config)
if [ -n "$API_BASE_URL_DEBUG" ]; then
    test_url_from_device "$API_BASE_URL_DEBUG" "LAN (debug)"
elif [ -n "$devDebug_URL" ]; then
    test_url_from_device "$devDebug_URL" "Debug build URL"
fi

***REMOVED*** Test remote URL (from release config)
if [ -n "$API_BASE_URL" ]; then
    test_url_from_device "$API_BASE_URL" "Remote (release)"
elif [ -n "$devRelease_URL" ]; then
    test_url_from_device "$devRelease_URL" "Release build URL"
fi

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** 5. Check Installed App (if any)
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

print_header "5. Installed Scanium Apps"

***REMOVED*** Check for installed Scanium packages
INSTALLED_PACKAGES=$(adb shell pm list packages | grep "scanium" || echo "")

if [ -n "$INSTALLED_PACKAGES" ]; then
    print_pass "Found installed Scanium app(s):"
    while IFS= read -r package; do
        pkg_name=$(echo "$package" | sed 's/package://')
        print_info "  $pkg_name"

        ***REMOVED*** Try to get version info
        version=$(adb shell dumpsys package "$pkg_name" | grep versionName | head -1 | sed 's/.*versionName=//' || echo "unknown")
        print_info "    Version: $version"
    done <<< "$INSTALLED_PACKAGES"
else
    print_warn "No Scanium app installed on device"
    print_info "Install with: ./gradlew :androidApp:installDevDebug"
fi

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** 6. Check Network Security Config
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

print_header "6. Network Security Configuration"

***REMOVED*** Check debug network security config
if [ -f "androidApp/src/debug/res/xml/network_security_config_debug.xml" ]; then
    if grep -q 'cleartextTrafficPermitted="true"' "androidApp/src/debug/res/xml/network_security_config_debug.xml"; then
        print_pass "Debug builds allow cleartext HTTP (for LAN)"
    else
        print_fail "Debug network config does not allow cleartext traffic"
    fi
else
    print_warn "Debug network security config not found"
fi

***REMOVED*** Check release network security config
if [ -f "androidApp/src/main/res/xml/network_security_config.xml" ]; then
    if grep -q 'cleartextTrafficPermitted="false"' "androidApp/src/main/res/xml/network_security_config.xml"; then
        print_pass "Release builds enforce HTTPS (security)"
    else
        print_warn "Release network config may allow cleartext traffic (security risk)"
    fi
else
    print_warn "Release network security config not found"
fi

***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***
***REMOVED*** 7. Summary
***REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED******REMOVED***

print_header "Summary"

TOTAL=$((PASS + FAIL + WARN))

echo -e "${GREEN}✓ Passed: $PASS${NC}"
echo -e "${RED}✗ Failed: $FAIL${NC}"
echo -e "${YELLOW}⚠ Warnings: $WARN${NC}"
echo -e "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}✓ All critical checks passed!${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Build and install: ./gradlew :androidApp:installDevDebug"
    echo "  2. Open app and test backend features (classification, feature flags)"
    echo "  3. Check app logs: adb logcat | grep -i scanium"
    exit 0
else
    echo -e "${RED}✗ Some checks failed. Review errors above.${NC}"
    echo ""
    echo "Common fixes:"
    echo "  - Add missing values to local.properties"
    echo "  - Rebuild the app: ./gradlew :androidApp:assembleDevDebug"
    echo "  - Check network connectivity from device"
    echo "  - See docs/BACKEND_CONNECTIVITY.md for detailed troubleshooting"
    exit 1
fi
