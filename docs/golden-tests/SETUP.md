# Golden Tests Setup

## Submodule initialization
Initialize and update the dataset submodule:
```bash
git submodule update --init --recursive external/golden-tests/scanium-golden-tests
```

## Validate the dataset locally
Run the dataset compliance checks (provenance + image constraints):
```bash
./scripts/golden-tests/validate-dataset.sh
```

## Bump the dataset version
Update the submodule pointer to a new commit or tag:
```bash
./scripts/golden-tests/update-submodule.sh v0.1.0
```
Then commit the submodule pointer update in Scanium.

## Troubleshooting
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

## Make targets (doc-only)
This repo does not include a Makefile, but these are the intended shortcuts:
```bash
make golden-init      # -> git submodule update --init --recursive external/golden-tests/scanium-golden-tests
make golden-validate  # -> ./scripts/golden-tests/validate-dataset.sh
```
