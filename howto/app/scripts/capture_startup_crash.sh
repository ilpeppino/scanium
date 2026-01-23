#!/bin/bash
##############################################################################
# Scanium Startup Crash Capture Script
#
# Purpose: Capture diagnostic information when the app crash-loops on startup.
#          Helps identify root causes by collecting logs, package info, and
#          process state before/after launch attempts.
#
# Usage:
#   ./scripts/dev/capture_startup_crash.sh [OPTIONS]
#
# Options:
#   --flavor FLAVOR   Build flavor: dev, beta, or prod (default: dev)
#   --clear           Clear app data before launch
#   --install APK     Install APK before launch
#   --loop N          Run N iterations to catch intermittent crashes (default: 1)
#   --stop-on-crash   Stop loop on first crash detected
#   --output DIR      Output directory (default: tmp/startup_crash)
#   --help            Show this help message
#
# Examples:
#   ./scripts/dev/capture_startup_crash.sh
#   ./scripts/dev/capture_startup_crash.sh --clear --loop 5 --stop-on-crash
#   ./scripts/dev/capture_startup_crash.sh --flavor beta --install app.apk
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
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Package name mapping by flavor
declare -A PACKAGE_NAMES=(
    ["dev"]="com.scanium.app.dev"
    ["beta"]="com.scanium.app.beta"
    ["prod"]="com.scanium.app"
)

# Default options
FLAVOR="dev"
CLEAR_DATA=false
INSTALL_APK=""
LOOP_COUNT=1
STOP_ON_CRASH=false
OUTPUT_DIR="$REPO_ROOT/tmp/startup_crash"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --flavor)
            FLAVOR="$2"
            shift 2
            ;;
        --clear)
            CLEAR_DATA=true
            shift
            ;;
        --install)
            INSTALL_APK="$2"
            shift 2
            ;;
        --loop)
            LOOP_COUNT="$2"
            shift 2
            ;;
        --stop-on-crash)
            STOP_ON_CRASH=true
            shift
            ;;
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --help|-h)
            cat << 'EOF'
Scanium Startup Crash Capture Script

Usage: ./scripts/dev/capture_startup_crash.sh [OPTIONS]

Options:
  --flavor FLAVOR   Build flavor: dev, beta, or prod (default: dev)
  --clear           Clear app data before launch
  --install APK     Install APK before launch
  --loop N          Run N iterations to catch intermittent crashes (default: 1)
  --stop-on-crash   Stop loop on first crash detected
  --output DIR      Output directory (default: tmp/startup_crash)
  --help            Show this help message

Examples:
  # Single capture for dev flavor
  ./scripts/dev/capture_startup_crash.sh

  # Loop mode to catch intermittent crashes
  ./scripts/dev/capture_startup_crash.sh --clear --loop 10 --stop-on-crash

  # Install APK and test beta flavor
  ./scripts/dev/capture_startup_crash.sh --flavor beta --install app-beta-release.apk

Output:
  Artifacts are saved to tmp/startup_crash/YYYYMMDD_HHMMSS/
  Each run creates:
    - logcat.txt           Full logcat filtered to app + system crash logs
    - fatal_exception.txt  Extracted FATAL EXCEPTION stacktrace (if any)
    - package_info.txt     Package install info and version
    - process_info.txt     Process state after launch
    - files_list.txt       App files directory listing (if accessible)
    - summary.txt          Quick summary of the run
EOF
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Validate flavor
if [[ ! -v "PACKAGE_NAMES[$FLAVOR]" ]]; then
    echo -e "${RED}Invalid flavor: $FLAVOR${NC}"
    echo "Valid flavors: dev, beta, prod"
    exit 1
fi

PACKAGE="${PACKAGE_NAMES[$FLAVOR]}"
MAIN_ACTIVITY="$PACKAGE/.MainActivity"

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Scanium Startup Crash Capture${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  Flavor:     $FLAVOR"
echo "  Package:    $PACKAGE"
echo "  Iterations: $LOOP_COUNT"
echo "  Clear data: $CLEAR_DATA"
echo "  Stop on crash: $STOP_ON_CRASH"
echo ""

# Check adb connectivity
echo -e "${YELLOW}Checking ADB connection...${NC}"
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}No Android device connected.${NC}"
    echo "Connect a device via USB and enable USB debugging."
    exit 1
fi
DEVICE=$(adb devices | grep "device$" | head -1 | awk '{print $1}')
echo -e "${GREEN}Connected to device: $DEVICE${NC}"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Function to capture a single startup attempt
capture_startup() {
    local iteration=$1
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local run_dir="$OUTPUT_DIR/${timestamp}_iter${iteration}"
    mkdir -p "$run_dir"

    echo -e "${YELLOW}[$iteration/$LOOP_COUNT] Starting capture at $timestamp${NC}"

    # Clear logcat buffer
    adb logcat -c

    # Optionally clear app data
    if [ "$CLEAR_DATA" = true ]; then
        echo "  Clearing app data..."
        adb shell pm clear "$PACKAGE" 2>/dev/null || true
    fi

    # Optionally install APK
    if [ -n "$INSTALL_APK" ]; then
        echo "  Installing APK: $INSTALL_APK"
        adb install -r "$INSTALL_APK"
    fi

    # Capture pre-launch package info
    echo "  Capturing package info..."
    adb shell dumpsys package "$PACKAGE" > "$run_dir/package_info.txt" 2>&1 || true

    # Force stop any existing instance
    adb shell am force-stop "$PACKAGE" 2>/dev/null || true
    sleep 0.5

    # Launch main activity
    echo "  Launching app..."
    local launch_time=$(date +%s%3N)
    adb shell am start -n "$MAIN_ACTIVITY" -W 2>&1 | tee "$run_dir/launch_result.txt" || true

    # Wait for potential crash (startup should complete within 3 seconds)
    sleep 3

    # Capture logcat
    echo "  Capturing logcat..."
    adb logcat -d \
        -s ActivityManager:* AndroidRuntime:* System.err:* "$PACKAGE:*" \
        "ScaniumApplication:*" "MainActivity:*" "Hilt:*" "DataStore:*" \
        "DomainPackProvider:*" "Sentry:*" \
        > "$run_dir/logcat.txt" 2>&1 || true

    # Also capture full logcat for thorough analysis
    adb logcat -d > "$run_dir/logcat_full.txt" 2>&1 || true

    # Extract FATAL EXCEPTION if present
    if grep -q "FATAL EXCEPTION" "$run_dir/logcat.txt" 2>/dev/null; then
        echo -e "  ${RED}FATAL EXCEPTION detected!${NC}"
        # Extract the exception block (from FATAL EXCEPTION to next blank line or end)
        awk '/FATAL EXCEPTION/,/^$/{print}' "$run_dir/logcat.txt" > "$run_dir/fatal_exception.txt"
        CRASH_DETECTED=true
    else
        CRASH_DETECTED=false
    fi

    # Check if app is still running
    echo "  Capturing process info..."
    adb shell "ps -A 2>/dev/null || ps" | grep "$PACKAGE" > "$run_dir/process_info.txt" 2>&1 || true

    local app_running=false
    if [ -s "$run_dir/process_info.txt" ]; then
        app_running=true
    fi

    # Check activity processes
    adb shell dumpsys activity processes | grep -A5 "$PACKAGE" > "$run_dir/activity_processes.txt" 2>&1 || true

    # Try to list app files (best effort, may fail without root)
    echo "  Capturing app files list..."
    adb shell "run-as $PACKAGE ls -la files/ 2>/dev/null || echo 'Cannot access app files (not debuggable or no files)'" > "$run_dir/files_list.txt" 2>&1 || true

    # Check for ANR traces
    adb shell "cat /data/anr/traces.txt 2>/dev/null | head -500" > "$run_dir/anr_traces.txt" 2>&1 || true

    # Create summary
    local crash_status="No crash detected"
    if [ "$CRASH_DETECTED" = true ]; then
        crash_status="CRASH DETECTED"
    fi

    local running_status="App NOT running"
    if [ "$app_running" = true ]; then
        running_status="App is running"
    fi

    cat > "$run_dir/summary.txt" << EOF
Scanium Startup Capture Summary
================================
Timestamp:     $timestamp
Iteration:     $iteration of $LOOP_COUNT
Flavor:        $FLAVOR
Package:       $PACKAGE
Device:        $DEVICE

Status:
  Crash:       $crash_status
  Running:     $running_status
  Clear data:  $CLEAR_DATA

Files captured:
  - logcat.txt           Filtered logcat
  - logcat_full.txt      Complete logcat dump
  - fatal_exception.txt  Extracted crash (if any)
  - package_info.txt     Package details
  - process_info.txt     Process state
  - activity_processes.txt Activity manager processes
  - files_list.txt       App files directory
  - anr_traces.txt       ANR traces (if available)
  - launch_result.txt    Activity launch result

EOF

    echo "  Artifacts saved to: $run_dir"

    if [ "$CRASH_DETECTED" = true ]; then
        echo -e "  ${RED}>>> CRASH DETECTED - see fatal_exception.txt${NC}"
        echo ""
        echo -e "${YELLOW}Crash excerpt:${NC}"
        head -30 "$run_dir/fatal_exception.txt" 2>/dev/null || true
        echo ""

        if [ "$STOP_ON_CRASH" = true ]; then
            echo -e "${RED}Stopping due to crash (--stop-on-crash)${NC}"
            echo ""
            echo -e "${BLUE}Full crash log at: $run_dir/fatal_exception.txt${NC}"
            echo -e "${BLUE}Full logcat at: $run_dir/logcat_full.txt${NC}"
            return 1
        fi
    else
        echo -e "  ${GREEN}No crash detected${NC}"
    fi

    return 0
}

# Run capture loop
CRASHES_FOUND=0
for ((i=1; i<=LOOP_COUNT; i++)); do
    if ! capture_startup "$i"; then
        CRASHES_FOUND=$((CRASHES_FOUND + 1))
        if [ "$STOP_ON_CRASH" = true ]; then
            break
        fi
    fi

    # Short delay between iterations (if looping)
    if [ $i -lt $LOOP_COUNT ]; then
        echo ""
        sleep 1
    fi
done

# Final summary
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Capture Complete${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  Total iterations: $LOOP_COUNT"
echo "  Crashes detected: $CRASHES_FOUND"
echo "  Output directory: $OUTPUT_DIR"
echo ""

if [ $CRASHES_FOUND -gt 0 ]; then
    echo -e "${RED}Crashes were detected. Review the fatal_exception.txt files.${NC}"
    echo ""
    echo "To analyze:"
    echo "  1. Check fatal_exception.txt for the stacktrace"
    echo "  2. Look for the 'Caused by:' chain to find root cause"
    echo "  3. Check if crash is in Application.onCreate, DI init, or Activity startup"
    echo ""
    exit 1
else
    echo -e "${GREEN}No crashes detected in $LOOP_COUNT iteration(s).${NC}"
fi
