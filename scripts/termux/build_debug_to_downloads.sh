#!/data/data/com.termux/files/usr/bin/bash
# Build Scanium debug APK and copy to Android Downloads for easy installation.
# Designed for Termux on Android devices.

set -euo pipefail

# Navigate to repo root
ROOT="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
if [[ -z "$ROOT" ]]; then
    echo "ERROR: Not inside a git repository."
    exit 1
fi
cd "$ROOT"

DEST_DIR="$HOME/storage/downloads/scanium-apk"

# Check storage access
if [[ ! -d "$HOME/storage/downloads" ]]; then
    echo "ERROR: Termux storage not configured."
    echo "Run: ./scripts/termux/termux-storage-setup.sh"
    exit 1
fi

echo "=== Scanium Debug Build for Termux ==="
echo "Repo root: $ROOT"
echo ""

# Determine which Gradle task to run
GRADLE_TASK=":androidApp:assembleDebug"

# Check if androidApp module exists, otherwise use generic assembleDebug
if [[ ! -d "$ROOT/androidApp" ]]; then
    echo "Note: androidApp module not found, using generic assembleDebug"
    GRADLE_TASK="assembleDebug"
fi

echo "Running: ./gradlew $GRADLE_TASK"
echo ""

# Run the build
./gradlew $GRADLE_TASK

echo ""
echo "Build complete. Locating APK..."
echo ""

# Find the newest debug APK in common output locations
APK_PATH=""
SEARCH_DIRS=(
    "$ROOT/androidApp/build/outputs/apk"
    "$ROOT/app/build/outputs/apk"
)

for dir in "${SEARCH_DIRS[@]}"; do
    if [[ -d "$dir" ]]; then
        # Find newest *-debug.apk or *debug*.apk
        FOUND=$(find "$dir" -type f -name "*debug*.apk" -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -n1 | cut -d' ' -f2- || echo "")
        if [[ -n "$FOUND" && -f "$FOUND" ]]; then
            APK_PATH="$FOUND"
            break
        fi
    fi
done

if [[ -z "$APK_PATH" ]]; then
    echo "ERROR: No debug APK found."
    echo ""
    echo "Searched directories:"
    for dir in "${SEARCH_DIRS[@]}"; do
        if [[ -d "$dir" ]]; then
            echo "  $dir:"
            find "$dir" -name "*.apk" 2>/dev/null | head -5 | sed 's/^/    /' || echo "    (no APKs)"
        else
            echo "  $dir: (not found)"
        fi
    done
    exit 1
fi

# Create destination directory
mkdir -p "$DEST_DIR"

# Get APK filename
APK_FILENAME=$(basename "$APK_PATH")

# Copy to Downloads
cp -f "$APK_PATH" "$DEST_DIR/"

# Get file size
APK_SIZE=$(du -h "$DEST_DIR/$APK_FILENAME" | cut -f1)

echo "=== APK Ready ==="
echo ""
echo "Source:      $APK_PATH"
echo "Destination: $DEST_DIR/$APK_FILENAME"
echo "Size:        $APK_SIZE"
echo ""
echo "To install:"
echo "  1. Open Files app or file manager"
echo "  2. Navigate to Downloads/scanium-apk/"
echo "  3. Tap $APK_FILENAME"
echo "  4. Allow installation from unknown sources if prompted"
echo ""
