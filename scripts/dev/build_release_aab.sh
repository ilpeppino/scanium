***REMOVED***!/usr/bin/env bash

***REMOVED*** Build Android App Bundle (AAB) with automatic version incrementing
***REMOVED*** Creates release AABs for specified flavor(s) with incremented versionCode
***REMOVED***
***REMOVED*** Usage:
***REMOVED***   ./scripts/dev/build_release_aab.sh [options] [flavor]
***REMOVED***
***REMOVED*** Arguments:
***REMOVED***   flavor    Build flavor: prod, beta, dev, or 'all' (default: prod)
***REMOVED***
***REMOVED*** Options:
***REMOVED***   --version-name VERSION    Set new version name (e.g., 1.2.0)
***REMOVED***                            If not specified, keeps current version name
***REMOVED***   --release-notes "NOTES"   Release notes for Google Play Console
***REMOVED***                            If not specified, will prompt for input
***REMOVED***   --skip-increment         Don't increment version code (use current)
***REMOVED***   --dry-run               Show what would be built without building
***REMOVED***
***REMOVED*** Examples:
***REMOVED***   ./scripts/dev/build_release_aab.sh                          ***REMOVED*** prod only
***REMOVED***   ./scripts/dev/build_release_aab.sh prod --version-name 1.2.0
***REMOVED***   ./scripts/dev/build_release_aab.sh all                      ***REMOVED*** all flavors
***REMOVED***   ./scripts/dev/build_release_aab.sh beta --skip-increment
***REMOVED***   ./scripts/dev/build_release_aab.sh --release-notes "Bug fixes and improvements"

set -euo pipefail

***REMOVED*** Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' ***REMOVED*** No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

VERSION_FILE="$PROJECT_ROOT/version.properties"
APP_MODULE="androidApp"

***REMOVED*** Parse arguments
FLAVOR="prod"
NEW_VERSION_NAME=""
RELEASE_NOTES=""
SKIP_INCREMENT=false
DRY_RUN=false

while [[ $***REMOVED*** -gt 0 ]]; do
    case $1 in
        --version-name)
            NEW_VERSION_NAME="$2"
            shift 2
            ;;
        --release-notes)
            RELEASE_NOTES="$2"
            shift 2
            ;;
        --skip-increment)
            SKIP_INCREMENT=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        prod|beta|dev|all)
            FLAVOR="$1"
            shift
            ;;
        *)
            echo -e "${RED}ERROR: Unknown argument: $1${NC}"
            echo "Usage: $0 [options] [flavor]"
            exit 1
            ;;
    esac
done

***REMOVED*** Initialize version file if it doesn't exist
if [[ ! -f "$VERSION_FILE" ]]; then
    echo -e "${YELLOW}Version file not found. Creating $VERSION_FILE with initial version...${NC}"
    cat > "$VERSION_FILE" << EOF
***REMOVED*** Scanium Version Configuration
***REMOVED*** This file tracks versionCode and versionName for release builds
***REMOVED*** DO NOT manually edit versionCode - it is auto-incremented by build_release_aab.sh

versionCode=1
versionName=1.0.0
EOF
    echo -e "${GREEN}Created version file with versionCode=1, versionName=1.0.0${NC}"
fi

***REMOVED*** Read current version
CURRENT_VERSION_CODE=$(grep "^versionCode=" "$VERSION_FILE" | cut -d'=' -f2)
CURRENT_VERSION_NAME=$(grep "^versionName=" "$VERSION_FILE" | cut -d'=' -f2)

if [[ -z "$CURRENT_VERSION_CODE" || -z "$CURRENT_VERSION_NAME" ]]; then
    echo -e "${RED}ERROR: Failed to read version from $VERSION_FILE${NC}"
    exit 1
fi

***REMOVED*** Determine new version
if [[ "$SKIP_INCREMENT" == "true" ]]; then
    NEW_VERSION_CODE=$CURRENT_VERSION_CODE
else
    NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))
fi

if [[ -z "$NEW_VERSION_NAME" ]]; then
    NEW_VERSION_NAME=$CURRENT_VERSION_NAME
fi

***REMOVED*** Validate version name format (semantic versioning)
if ! [[ "$NEW_VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+)?$ ]]; then
    echo -e "${RED}ERROR: Invalid version name format: $NEW_VERSION_NAME${NC}"
    echo -e "${YELLOW}Expected format: X.Y.Z or X.Y.Z-suffix (e.g., 1.2.0 or 1.2.0-beta)${NC}"
    exit 1
fi

***REMOVED*** Get git information
GIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
GIT_BRANCH=$(git branch --show-current 2>/dev/null || echo "unknown")
GIT_STATUS=$(git status --porcelain 2>/dev/null || echo "")

***REMOVED*** Check for uncommitted changes
if [[ -n "$GIT_STATUS" ]]; then
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}⚠️  WARNING: Uncommitted changes detected${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}Building from a dirty working tree. Consider committing changes first.${NC}"
    echo ""

    ***REMOVED*** Give user a chance to abort
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}Build cancelled${NC}"
        exit 1
    fi
fi

***REMOVED*** Determine which flavors to build
if [[ "$FLAVOR" == "all" ]]; then
    FLAVORS=("prod" "beta" "dev")
else
    FLAVORS=("$FLAVOR")
fi

***REMOVED*** Prompt for release notes if not provided
if [[ -z "$RELEASE_NOTES" ]]; then
    echo -e "\n${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}Release Notes${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}Enter release notes for Google Play Console (end with Ctrl+D):${NC}"
    echo -e "${YELLOW}Example:${NC}"
    echo -e "${YELLOW}  - Fixed camera crash on Android 14${NC}"
    echo -e "${YELLOW}  - Improved object detection accuracy${NC}"
    echo -e "${YELLOW}  - Performance improvements${NC}"
    echo ""

    ***REMOVED*** Read multi-line input
    RELEASE_NOTES=$(cat)

    if [[ -z "$RELEASE_NOTES" ]]; then
        echo -e "${YELLOW}Warning: No release notes provided${NC}"
        RELEASE_NOTES="Bug fixes and improvements"
    fi
fi

***REMOVED*** Display build plan
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Scanium Release AAB Build${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "Current version:  ${CYAN}$CURRENT_VERSION_NAME ($CURRENT_VERSION_CODE)${NC}"
echo -e "New version:      ${GREEN}$NEW_VERSION_NAME ($NEW_VERSION_CODE)${NC}"
echo -e "Git SHA:          ${CYAN}$GIT_SHA${NC}"
echo -e "Git branch:       ${CYAN}$GIT_BRANCH${NC}"
echo -e "Flavors:          ${CYAN}${FLAVORS[*]}${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [[ "$DRY_RUN" == "true" ]]; then
    echo -e "\n${YELLOW}DRY RUN: Would update $VERSION_FILE and build AABs${NC}"
    echo -e "${YELLOW}No actual build or version update performed${NC}"
    exit 0
fi

***REMOVED*** Update version file
echo -e "\n${BLUE}[1/3] Updating version file...${NC}"
cat > "$VERSION_FILE" << EOF
***REMOVED*** Scanium Version Configuration
***REMOVED*** This file tracks versionCode and versionName for release builds
***REMOVED*** DO NOT manually edit versionCode - it is auto-incremented by build_release_aab.sh

versionCode=$NEW_VERSION_CODE
versionName=$NEW_VERSION_NAME
EOF
echo -e "${GREEN}Updated $VERSION_FILE${NC}"

***REMOVED*** Build AABs for each flavor
echo -e "\n${BLUE}[2/3] Building release AAB(s)...${NC}"

BUILD_TASKS=()
for flavor in "${FLAVORS[@]}"; do
    ***REMOVED*** Capitalize first letter for Gradle task name (portable for macOS/Linux)
    first_char=$(echo "${flavor}" | cut -c1 | tr '[:lower:]' '[:upper:]')
    rest_chars=$(echo "${flavor}" | cut -c2-)
    VARIANT="${first_char}${rest_chars}Release"
    BUILD_TASKS+=(":$APP_MODULE:bundle$VARIANT")
done

***REMOVED*** Build all tasks
echo -e "${CYAN}Running: ./gradlew ${BUILD_TASKS[*]} -Pscanium.version.code=$NEW_VERSION_CODE -Pscanium.version.name=$NEW_VERSION_NAME${NC}"
./gradlew "${BUILD_TASKS[@]}" \
    -Pscanium.version.code="$NEW_VERSION_CODE" \
    -Pscanium.version.name="$NEW_VERSION_NAME" \
    --no-daemon \
    --console=plain

***REMOVED*** Display AAB locations and sizes
echo -e "\n${BLUE}[3/3] Build complete! AAB files:${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

AAB_DIR="$PROJECT_ROOT/$APP_MODULE/build/outputs/bundle"
for flavor in "${FLAVORS[@]}"; do
    AAB_FILE="$AAB_DIR/${flavor}Release/$APP_MODULE-$flavor-release.aab"
    first_char=$(echo "${flavor}" | cut -c1 | tr '[:lower:]' '[:upper:]')
    rest_chars=$(echo "${flavor}" | cut -c2-)
    FLAVOR_CAP="${first_char}${rest_chars}"

    if [[ -f "$AAB_FILE" ]]; then
        AAB_SIZE=$(du -h "$AAB_FILE" | awk '{print $1}')
        echo -e "${GREEN}✓${NC} ${FLAVOR_CAP}: $AAB_FILE (${AAB_SIZE})"
    else
        echo -e "${RED}✗${NC} ${FLAVOR_CAP}: AAB not found"
    fi
done

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "\n${GREEN}Build Summary:${NC}"
echo -e "  Version:     ${GREEN}$NEW_VERSION_NAME ($NEW_VERSION_CODE)${NC}"
echo -e "  Git SHA:     ${GREEN}$GIT_SHA${NC}"
echo -e "  Flavors:     ${GREEN}${FLAVORS[*]}${NC}"

***REMOVED*** Save release notes to file
RELEASE_NOTES_FILE="$PROJECT_ROOT/release-notes-$NEW_VERSION_NAME.txt"
echo "$RELEASE_NOTES" > "$RELEASE_NOTES_FILE"
echo -e "\n${BLUE}Saved release notes to: ${CYAN}$RELEASE_NOTES_FILE${NC}"

***REMOVED*** Commit and push version.properties
echo -e "\n${BLUE}Committing and pushing version.properties...${NC}"
git add version.properties
git commit -m "chore: bump version to $NEW_VERSION_NAME ($NEW_VERSION_CODE)"
git push

echo -e "\n${GREEN}✓ Version committed and pushed${NC}"

echo -e "\n${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${CYAN}Release Notes for Google Play Console:${NC}"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo "$RELEASE_NOTES"
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo -e "\n${CYAN}Next steps:${NC}"
echo -e "  1. Test the AAB(s) using bundletool or internal testing track"
echo -e "  2. Upload to Google Play Console for release"
echo -e "  3. Copy release notes from above or: ${CYAN}cat $RELEASE_NOTES_FILE${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
