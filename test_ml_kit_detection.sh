***REMOVED***!/bin/bash

***REMOVED*** Test script for verifying ML Kit detection fixes
***REMOVED*** This script helps diagnose ML Kit zero detection issues

echo "========================================="
echo "ML Kit Detection Test Script"
echo "========================================="
echo ""

***REMOVED*** Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No Android device connected"
    echo "Please connect a device or start an emulator"
    exit 1
fi

echo "Device connected: $(adb devices | grep 'device$' | awk '{print $1}')"
echo ""

***REMOVED*** Build and install app
echo "Building and installing app..."
./gradlew :app:installDebug --quiet

if [ $? -ne 0 ]; then
    echo "ERROR: Build or installation failed"
    exit 1
fi

echo "App installed successfully"
echo ""

***REMOVED*** Clear logcat
echo "Clearing logcat..."
adb logcat -c

echo "========================================="
echo "Starting logcat monitoring..."
echo "========================================="
echo ""
echo "Instructions:"
echo "1. Open the app on your device"
echo "2. Grant camera permission"
echo "3. Point camera at objects (bottles, books, boxes work well)"
echo "4. Long-press the screen to start scanning"
echo "5. Watch the logs below for detection results"
echo ""
echo "Look for these key log messages:"
echo "  - 'ML Kit Object Detection model initialization complete' (model ready)"
echo "  - 'Bitmap Analysis: ...' (image diagnostics)"
echo "  - 'ML Kit returned X objects' (detection results)"
echo "  - 'Alternative detection returned X objects' (fallback strategy)"
echo "  - 'CRITICAL: ZERO OBJECTS DETECTED' (if all strategies fail)"
echo ""
echo "Press Ctrl+C to stop monitoring"
echo ""
echo "========================================="
echo ""

***REMOVED*** Monitor logcat for relevant logs
adb logcat -s \
    ObjectDetectorClient:* \
    CameraXManager:* \
    ObjectTracker:* \
    | grep --line-buffered -E ">>|Model|Detection|Bitmap Analysis|CRITICAL|WARNING"
