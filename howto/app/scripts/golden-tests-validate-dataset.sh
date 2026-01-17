***REMOVED***!/bin/bash
***REMOVED*** validate-dataset.sh
***REMOVED*** Validates the external golden dataset submodule for provenance and image constraints.

set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
SUBMODULE_DIR="$ROOT_DIR/external/golden-tests/scanium-golden-tests"

echo "Initializing submodule..."
git -C "$ROOT_DIR" submodule update --init --recursive "$SUBMODULE_DIR"

if [[ ! -d "$SUBMODULE_DIR/scripts/golden-images" ]]; then
    echo "Error: expected scripts/golden-images in submodule."
    exit 1
fi

if [[ -d "$SUBMODULE_DIR/tests/golden_images/by_subtype" ]]; then
    DATASET_DIR="tests/golden_images"
elif [[ -d "$SUBMODULE_DIR/golden_images/by_subtype" ]]; then
    DATASET_DIR="golden_images"
else
    echo "Error: could not find dataset root (tests/golden_images or golden_images)."
    exit 1
fi

FAILED=0

echo "Running provenance check..."
if ! (cd "$SUBMODULE_DIR" && ./scripts/golden-images/check-provenance.sh); then
    FAILED=1
fi

echo "Running image validation..."
VALIDATE_OUTPUT="$(cd "$SUBMODULE_DIR" && ./scripts/golden-images/validate-images.sh "$DATASET_DIR/by_subtype" 2>&1 || true)"
echo "$VALIDATE_OUTPUT"
if echo "$VALIDATE_OUTPUT" | grep -q "FAIL:"; then
    FAILED=1
fi

if [[ "$FAILED" -eq 0 ]]; then
    echo "OK: dataset validation passed."
    exit 0
else
    echo "FAIL: dataset validation failed."
    exit 1
fi
