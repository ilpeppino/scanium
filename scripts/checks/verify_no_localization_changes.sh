#!/bin/bash

# Guardrail: Verify no localization files were modified
#
# This script checks that:
# 1. No localized strings JSON files under core-domainpack/at_home_inventory/ were touched
# 2. No androidApp values-*/strings.xml files were touched
#
# Exits with 0 if all checks pass, 1 if any localization files were modified.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"

# Array of forbidden paths (relative to repo root)
FORBIDDEN_PATHS=(
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.de.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.es.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.fr.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.it.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.nl.json"
    "core-domainpack/at_home_inventory/strings_keys_catalog.at_home_inventory.pt-BR.json"
)

# Check for glob patterns
FORBIDDEN_GLOBS=(
    "androidApp/src/main/res/values-*/strings.xml"
)

VIOLATIONS=()

# Check explicit forbidden paths
for path in "${FORBIDDEN_PATHS[@]}"; do
    full_path="$REPO_ROOT/$path"
    if git diff --quiet --name-only HEAD -- "$full_path" 2>/dev/null | grep -q .; then
        VIOLATIONS+=("$path")
    fi
done

# Check glob patterns
for glob in "${FORBIDDEN_GLOBS[@]}"; do
    # Use git ls-files to check if any files matching the pattern were modified
    matching_files=$(git diff --name-only HEAD -- "$glob" 2>/dev/null || true)
    if [ -n "$matching_files" ]; then
        while IFS= read -r file; do
            if [ -n "$file" ]; then
                VIOLATIONS+=("$file")
            fi
        done <<< "$matching_files"
    fi
done

if [ ${#VIOLATIONS[@]} -gt 0 ]; then
    echo "❌ ERROR: Localization files were modified (not allowed):" >&2
    printf '  - %s\n' "${VIOLATIONS[@]}" >&2
    echo "" >&2
    echo "Only the following files are allowed to be modified for brand expansion:" >&2
    echo "  - core-domainpack/src/main/res/raw/brands_catalog_bundle_v1.json" >&2
    echo "  - scripts/domainpack/**" >&2
    echo "  - scripts/checks/**" >&2
    exit 1
fi

echo "✅ Localization check passed: no forbidden files modified"
exit 0
