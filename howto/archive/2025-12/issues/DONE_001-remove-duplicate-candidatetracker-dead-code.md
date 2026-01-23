# Remove Duplicate CandidateTracker (Dead Code)

**Status:** Resolved

**Labels:** `tech-debt`, `priority:p0`, `area:ml`, `area:tracking`
**Type:** Tech Debt
**Severity:** Critical

## Problem

The codebase contains TWO completely separate object tracking implementations:

1. **CandidateTracker** (ml/ package) - Uses `DetectionCandidate`, frame-count based
2. **ObjectTracker** (tracking/ package) - Uses `ObjectCandidate`, frame-based (ACTIVE)

Only **ObjectTracker** is actually used in CameraXManager. CandidateTracker is legacy dead code that
should be removed.

## Evidence

**Dead Code Files:**

- `/app/src/main/java/com/scanium/app/ml/CandidateTracker.kt` ❌ NOT USED
- `/app/src/main/java/com/scanium/app/ml/DetectionCandidate.kt` ❌ NOT USED
- `/app/src/test/java/com/scanium/app/ml/CandidateTrackerTest.kt` ❌ TESTS DEAD CODE

**Active Code Files:**

- `/app/src/main/java/com/scanium/app/tracking/ObjectTracker.kt` ✅ USED
- `/app/src/main/java/com/scanium/app/tracking/ObjectCandidate.kt` ✅ USED
- `/app/src/test/java/com/scanium/app/tracking/ObjectTrackerTest.kt` ✅ VALID TESTS

**Proof:**

- CameraXManager.kt line 20 imports only `ObjectTracker`
- CameraXManager.kt line 55 instantiates only `objectTracker`
- Grep search shows `CandidateTracker` has ZERO references in main code

## Impact

- **Code Confusion**: Developers don't know which tracker is "official"
- **Maintenance Burden**: Two implementations to maintain when only one is used
- **Test Pollution**: Tests exist for dead code (CandidateTrackerTest.kt)
- **Documentation Mismatch**: Some comments still reference old tracker

## Steps to Reproduce

```bash
# Search for usage of CandidateTracker
grep -r "CandidateTracker" app/src/main/java

# Result: Only found in:
# - The dead CandidateTracker.kt file itself
# - Nowhere else in main code
```

## Acceptance Criteria

- [x] Delete `/app/src/main/java/com/scanium/app/ml/CandidateTracker.kt`
- [x] Delete `/app/src/main/java/com/scanium/app/ml/DetectionCandidate.kt`
- [x] Delete `/app/src/test/java/com/scanium/app/ml/CandidateTrackerTest.kt`
- [x] Delete `/app/src/test/java/com/scanium/app/ml/DetectionCandidateTest.kt`
- [ ] Verify all tests still pass after deletion *(blocked: local environment missing Android
  SDK/JDK 17)*
- [x] Update any comments/docs that reference CandidateTracker to use ObjectTracker

## Suggested Approach

1. Run full test suite to confirm baseline
2. Delete the four files listed above
3. Run tests again - should pass (no code depends on these)
4. Search codebase for "CandidateTracker" string and update comments
5. Commit with message: "Remove legacy CandidateTracker (replaced by ObjectTracker)"

## Resolution Notes

- Removed the legacy CandidateTracker/DetectionCandidate implementations and their unit tests so
  ObjectTracker is the single tracking pipeline.
- Updated CameraXManager KDoc plus architecture/testing/improvements docs to reference ObjectTracker
  and current TrackerConfig values.
- Marked related documentation and review summaries as completed for this cleanup.

## Verification

- Commands attempted (failures due to missing SDK/Java 17 toolchain in container):
    - `./gradlew test`
    - `./gradlew assembleDebug`
- Manual check: `rg "CandidateTracker"` now only references historical issue records.

## Related Issues

- Issue #002 (Documentation mismatch in CameraXManager comment)
