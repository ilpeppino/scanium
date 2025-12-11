***REMOVED***!/usr/bin/env bash
***REMOVED*** Portable build script for Scanium that works across all platforms
***REMOVED*** Automatically finds and uses Java 17 for building

set -e  ***REMOVED*** Exit on error

***REMOVED*** Function to find Java 17 on different platforms
find_java17() {
    ***REMOVED*** Try macOS java_home utility
    if command -v /usr/libexec/java_home &> /dev/null; then
        JAVA17=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")
        if [ -n "$JAVA17" ]; then
            echo "$JAVA17"
            return 0
        fi
    fi

    ***REMOVED*** Try common Linux paths
    for path in /usr/lib/jvm/java-17-* /usr/lib/jvm/jdk-17* /opt/java/jdk-17*; do
        if [ -d "$path" ]; then
            echo "$path"
            return 0
        fi
    done

    ***REMOVED*** Try SDKMAN
    if [ -n "$SDKMAN_DIR" ] && [ -d "$SDKMAN_DIR/candidates/java" ]; then
        for path in "$SDKMAN_DIR/candidates/java"/17*; do
            if [ -d "$path" ]; then
                echo "$path"
                return 0
            fi
        done
    fi

    ***REMOVED*** Try mise
    if command -v mise &> /dev/null; then
        MISE_JAVA=$(mise where java@17 2>/dev/null || echo "")
        if [ -n "$MISE_JAVA" ]; then
            echo "$MISE_JAVA"
            return 0
        fi
    fi

    ***REMOVED*** Not found
    return 1
}

echo "🔍 Looking for Java 17..."

***REMOVED*** Find Java 17
if JAVA17_HOME=$(find_java17); then
    echo "✅ Found Java 17 at: $JAVA17_HOME"
    export JAVA_HOME="$JAVA17_HOME"
else
    echo "❌ Java 17 not found!"
    echo ""
    echo "Please install Java 17:"
    echo "  macOS:   brew install openjdk@17"
    echo "  Linux:   sudo apt install openjdk-17-jdk"
    echo "  Windows: choco install temurin17"
    echo "  Any OS:  sdk install java 17.0.9-tem"
    echo ""
    echo "Or download from: https://adoptium.net/temurin/releases/?version=17"
    exit 1
fi

***REMOVED*** Verify Java version
echo "☕ Using Java: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"

***REMOVED*** Run Gradle command
echo "🔨 Building Scanium..."
./gradlew "$@"

echo "✨ Done!"
