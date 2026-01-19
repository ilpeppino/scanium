***REMOVED*** Dataset Version Pinning

***REMOVED******REMOVED*** Why Scanium pins a commit

- Reproducibility: CI and local runs use the exact same dataset content.
- Auditability: provenance changes are reviewed as explicit diffs.
- Safety: license changes or removals do not silently alter test inputs.

***REMOVED******REMOVED*** How to bump the dataset version

1. Update the submodule pointer:
   ```bash
   ./scripts/golden-tests/update-submodule.sh <tag-or-commit>
   ```
2. Validate:
   ```bash
   ./scripts/golden-tests/validate-dataset.sh
   ```
3. Commit the pointer update in Scanium:
   ```bash
   git add external/golden-tests/scanium-golden-tests
   git commit -m "chore: bump golden dataset to <tag-or-commit>"
   ```

***REMOVED******REMOVED*** How to roll back quickly

1. Find the previous submodule commit in git history.
2. Check out that commit in Scanium:
   ```bash
   git checkout <scanium_commit_with_old_pointer>
   git submodule update --init --recursive external/golden-tests/scanium-golden-tests
   ```
