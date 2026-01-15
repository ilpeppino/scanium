***REMOVED***!/bin/bash
***REMOVED*** update-submodule.sh
***REMOVED*** Updates the dataset submodule working tree to a requested ref.

set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
SUBMODULE_DIR="$ROOT_DIR/external/golden-tests/scanium-golden-tests"
REF="${1:-}"

echo "Initializing submodule..."
git -C "$ROOT_DIR" submodule update --init --recursive "$SUBMODULE_DIR"

CURRENT_COMMIT="$(git -C "$SUBMODULE_DIR" rev-parse HEAD)"
echo "Current submodule commit: $CURRENT_COMMIT"

DEFAULT_BRANCH="$(git -C "$SUBMODULE_DIR" remote show origin | awk '/HEAD branch/ {print $NF}')"
if [[ -z "$DEFAULT_BRANCH" ]]; then
    DEFAULT_BRANCH="main"
fi

echo "Fetching latest from origin/$DEFAULT_BRANCH..."
git -C "$SUBMODULE_DIR" fetch origin "$DEFAULT_BRANCH" --tags

if [[ -n "$REF" ]]; then
    echo "Checking out ref: $REF"
    git -C "$SUBMODULE_DIR" checkout "$REF"
else
    echo "No ref provided; leaving submodule at current commit."
fi

NEW_COMMIT="$(git -C "$SUBMODULE_DIR" rev-parse HEAD)"
echo "New submodule commit: $NEW_COMMIT"

echo "Next steps:"
echo "  1) Validate: ./scripts/golden-tests/validate-dataset.sh"
echo "  2) Stage pointer: git add external/golden-tests/scanium-golden-tests"
echo "  3) Commit: git commit -m \"chore: bump golden dataset to <tag-or-commit>\""
