***REMOVED***!/bin/bash

***REMOVED*** Main verification script for brands catalog updates.
***REMOVED***
***REMOVED*** This script:
***REMOVED*** 1. Runs localization guardrail checks
***REMOVED*** 2. Runs pytest for brands append script tests
***REMOVED*** 3. Validates that only catalog files were modified
***REMOVED***
***REMOVED*** Exits with 0 if all checks pass, 1 if any check fails.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel)"

echo "Running brands catalog verification checks..."
echo ""

***REMOVED*** Step 1: Localization guardrail
echo "1. Checking for localization file modifications..."
if bash "$SCRIPT_DIR/verify_no_localization_changes.sh"; then
    echo ""
else
    exit 1
fi

***REMOVED*** Step 2: Run pytest tests
echo "2. Running pytest for append_brands_bundle script..."
pytest_file="$SCRIPT_DIR/../domainpack/test_append_brands_bundle.py"
if [ -f "$pytest_file" ]; then
    if python -m pytest "$pytest_file" -v; then
        echo ""
    else
        exit 1
    fi
else
    echo "⚠️  Warning: pytest file not found at $pytest_file (skipping)"
    echo ""
fi

***REMOVED*** Step 3: Catalog-only check
echo "3. Verifying only catalog files were modified..."
allowed_patterns=(
    "core-domainpack/src/main/res/raw/brands_catalog_bundle_v1.json"
    "scripts/domainpack/"
    "scripts/checks/"
)

modified_files=$(git diff --name-only HEAD || true)
disallowed=()

while IFS= read -r file; do
    if [ -z "$file" ]; then
        continue
    fi

    matched=0
    for pattern in "${allowed_patterns[@]}"; do
        if [[ "$file" == "$pattern"* ]]; then
            matched=1
            break
        fi
    done

    if [ $matched -eq 0 ]; then
        disallowed+=("$file")
    fi
done <<< "$modified_files"

if [ ${***REMOVED***disallowed[@]} -gt 0 ]; then
    echo "❌ ERROR: Only the following files are allowed to be modified:" >&2
    printf '  - %s\n' "${allowed_patterns[@]}" >&2
    echo "" >&2
    echo "Disallowed files found:" >&2
    printf '  - %s\n' "${disallowed[@]}" >&2
    exit 1
fi

echo "✅ All modifications are within allowed paths"
echo ""
echo "✅ All verification checks passed!"
exit 0
