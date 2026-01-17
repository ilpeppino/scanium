***REMOVED***!/bin/bash

***REMOVED*** Generate placeholder locale resources for supported languages
***REMOVED*** This script creates Android resource directories for all supported locales
***REMOVED*** and populates them with the base English strings.xml as a fallback.

set -e

***REMOVED*** Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' ***REMOVED*** No Color

***REMOVED*** Configuration
BASE_RES_DIR="androidApp/src/main/res"
BASE_STRINGS_XML="$BASE_RES_DIR/values/strings.xml"

***REMOVED*** Discover supported locales from AppLanguage.kt
***REMOVED*** Extract locale codes from enum class entries (lines containing enum values)
SUPPORTED_LOCALES=$(
    sed -n '/enum class AppLanguage/,/^}/p' androidApp/src/main/java/com/scanium/app/model/AppLanguage.kt | \
    grep -oE '"[^"]*"' | \
    tr -d '"' | \
    grep -v '^$' | \
    sort -u
)

***REMOVED*** Create mapping function for BCP-47 to Android resource qualifiers
get_android_qualifier() {
    case "$1" in
        "en")      echo "values" ;;
        "es")      echo "values-es" ;;
        "it")      echo "values-it" ;;
        "fr")      echo "values-fr" ;;
        "nl")      echo "values-nl" ;;
        "de")      echo "values-de" ;;
        "pt-BR")   echo "values-pt-rBR" ;;
        *)         echo "" ;;
    esac
}

***REMOVED*** Validate base strings.xml exists
if [ ! -f "$BASE_STRINGS_XML" ]; then
    echo "Error: Base strings.xml not found at $BASE_STRINGS_XML"
    exit 1
fi

echo "Discovering supported locales..."
echo "Supported locales: $SUPPORTED_LOCALES"
echo

***REMOVED*** Initialize counters
CREATED=0
SKIPPED=0
ERRORS=0

***REMOVED*** Process each discovered locale
for locale in $SUPPORTED_LOCALES; do
    ***REMOVED*** Skip non-language codes
    if [ "$locale" = "system" ]; then
        continue
    fi

    qualifier=$(get_android_qualifier "$locale")

    if [ -z "$qualifier" ]; then
        echo "Skipping unknown locale: $locale"
        ((SKIPPED++))
        continue
    fi

    res_dir="$BASE_RES_DIR/$qualifier"
    strings_file="$res_dir/strings.xml"

    if [ "$qualifier" == "values" ]; then
        ***REMOVED*** English base locale already exists
        ((SKIPPED++))
        continue
    fi

    if [ -f "$strings_file" ]; then
        ***REMOVED*** Strings file already exists, skip
        ((SKIPPED++))
        continue
    fi

    ***REMOVED*** Create directory if it doesn't exist
    if [ ! -d "$res_dir" ]; then
        mkdir -p "$res_dir" 2>/dev/null || {
            printf "${RED}✗ Failed to create %s${NC}\n" "$res_dir"
            ((ERRORS++))
            continue
        }
    fi

    ***REMOVED*** Copy base strings.xml
    cp "$BASE_STRINGS_XML" "$strings_file" 2>/dev/null || {
        printf "${RED}✗ Failed to copy strings.xml to %s${NC}\n" "$res_dir"
        ((ERRORS++))
        continue
    }

    printf "${GREEN}✓ Created %s/strings.xml${NC}\n" "$qualifier"
    ((CREATED++))
done

echo
echo "════════════════════════════════════════"
locale_count=$(echo "$SUPPORTED_LOCALES" | wc -w)
echo "Discovered locales: $locale_count"
printf "${GREEN}Created: %d${NC}\n" "$CREATED"
printf "${YELLOW}Skipped existing: %d${NC}\n" "$SKIPPED"
if [ "$ERRORS" -gt 0 ]; then
    printf "${RED}Errors: %d${NC}\n" "$ERRORS"
else
    echo "Errors: 0"
fi
echo "════════════════════════════════════════"

if [ "$ERRORS" -gt 0 ]; then
    exit 1
fi

exit 0
