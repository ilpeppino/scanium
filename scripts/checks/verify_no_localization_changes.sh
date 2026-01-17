***REMOVED***!/bin/bash

***REMOVED*** Guardrail: Verify no localization files were modified
***REMOVED***
***REMOVED*** This script checks that:
***REMOVED*** 1. No localized strings JSON files under core-domainpack/at_home_inventory/ were touched
***REMOVED*** 2. No androidApp values-*/strings.xml files were touched
***REMOVED***
***REMOVED*** Exits with 0 if all checks pass, 1 if any localization files were modified.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"

***REMOVED*** Array of forbidden paths (relative to repo root)
FORBIDDEN_PATHS=(
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.de.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.es.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.fr.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.it.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.nl.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.pt-BR.json"
)

***REMOVED*** Check for glob patterns
FORBIDDEN_GLOBS=(
    "androidApp/src/main/res/values-*/strings.xml"
)

VIOLATIONS=()

***REMOVED*** Check explicit forbidden paths
for path in "${FORBIDDEN_PATHS[@]}"; do
    full_path="$REPO_ROOT/$path"
    if git diff --quiet --name-only HEAD -- "$full_path" 2>/dev/null | grep -q .; then
        VIOLATIONS+=("$path")
    fi
done

***REMOVED*** Check glob patterns
for glob in "${FORBIDDEN_GLOBS[@]}"; do
    ***REMOVED*** Use git ls-files to check if any files matching the pattern were modified
    matching_files=$(git diff --name-only HEAD -- "$glob" 2>/dev/null || true)
    if [ -n "$matching_files" ]; then
        while IFS= read -r file; do
            if [ -n "$file" ]; then
                VIOLATIONS+=("$file")
            fi
        done <<< "$matching_files"
    fi
done

if [ ${***REMOVED***VIOLATIONS[@]} -gt 0 ]; then
    echo "❌ ERROR: Localization files were modified (not allowed):" >&2
    printf '  - %s\n' "${VIOLATIONS[@]}" >&2
    echo "" >&2
    echo "Only the following files are allowed to be modified:" >&2
    echo "  - core-domainpack/src/main/res/raw/brands_catalog_bundle_v1.json" >&2
    echo "  - scripts/domainpack/**" >&2
    echo "  - scripts/ebay/**" >&2
    echo "  - scripts/checks/**" >&2
    echo "  - scripts/output/ebay/**" >&2
    exit 1
fi

echo "✅ Localization check passed: no forbidden files modified"
exit 0
