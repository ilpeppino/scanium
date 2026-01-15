***REMOVED*** Golden Tests Setup

***REMOVED******REMOVED*** Submodule initialization
Initialize and update the dataset submodule:
```bash
git submodule update --init --recursive external/golden-tests/scanium-golden-tests
```

***REMOVED******REMOVED*** Validate the dataset locally
Run the dataset compliance checks (provenance + image constraints):
```bash
./scripts/golden-tests/validate-dataset.sh
```

***REMOVED******REMOVED*** Bump the dataset version
Update the submodule pointer to a new commit or tag:
```bash
./scripts/golden-tests/update-submodule.sh v0.1.0
```
Then commit the submodule pointer update in Scanium.

***REMOVED******REMOVED*** Troubleshooting
Detached HEAD in submodule:
```bash
cd external/golden-tests/scanium-golden-tests
git status
git checkout main
```

Dirty submodule (uncommitted changes inside):
```bash
cd external/golden-tests/scanium-golden-tests
git status
```
If you need to keep changes, commit them in the dataset repo or stash them before updating the pointer.

***REMOVED******REMOVED*** Make targets (doc-only)
This repo does not include a Makefile, but these are the intended shortcuts:
```bash
make golden-init      ***REMOVED*** -> git submodule update --init --recursive external/golden-tests/scanium-golden-tests
make golden-validate  ***REMOVED*** -> ./scripts/golden-tests/validate-dataset.sh
```

***REMOVED******REMOVED*** Run golden regression tests (classification + attributes)
These tests run against the cloud classifier and load images directly from the dataset repo.

Required environment variables:
```bash
export SCANIUM_BASE_URL=https://your-backend.com
export SCANIUM_API_KEY=your-api-key
```

Optional dataset path override (otherwise defaults to the submodule):
```bash
export SCANIUM_GOLDEN_TESTS_PATH=external/golden-tests/scanium-golden-tests/tests/golden_images/by_subtype
```

Run core mode (first 3 images per subtype):
```bash
./gradlew :androidApp:testDevDebugUnitTest --tests com.scanium.app.golden.GoldenDatasetRegressionTest
```

Run full sweep:
```bash
SCANIUM_GOLDEN_TESTS_FULL=1 ./gradlew :androidApp:testDevDebugUnitTest --tests com.scanium.app.golden.GoldenDatasetRegressionTest
```

Notes:
- If the dataset repo is not present, tests are skipped with a clear message.
- Failures print a per-image report: subtype, image, pass/fail, predicted subtype, score, and missing attributes.

***REMOVED******REMOVED*** CI (optional)
GitHub Actions workflow `golden-classification-tests` runs on:
- workflow_dispatch
- nightly schedule
- PRs labeled `run-golden-tests`

The workflow expects repository secrets:
- `SCANIUM_BASE_URL`
- `SCANIUM_API_KEY`
