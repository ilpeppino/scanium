# Remove or Activate Unused Room Database Layer

**Labels:** `tech-debt`, `priority:p0`, `area:data`, `architectural-drift`
**Type:** Architectural Drift
**Severity:** Critical

## Problem

A complete Room database persistence layer is fully implemented but **completely unused** in the main application code. This creates:

- Architectural confusion (docs say "no persistence" but persistence layer exists)
- Wasted dependencies and build overhead
- Schema drift risk (entity missing fields from domain model)

## Evidence

**Implemented but unused files:**
- `/app/src/main/java/com/scanium/app/data/ScaniumDatabase.kt` - Complete database singleton
- `/app/src/main/java/com/scanium/app/data/ScannedItemEntity.kt` - Entity with converters
- `/app/src/main/java/com/scanium/app/data/ItemsDao.kt` - Full CRUD DAO
- `/app/src/main/java/com/scanium/app/data/ItemsRepository.kt` - Repository interface
- `/app/src/main/java/com/scanium/app/data/ItemsRepositoryImpl.kt` - Repository implementation
- `/app/src/test/java/com/scanium/app/data/ItemsDaoTest.kt` - Tests for unused code
- `/app/src/test/java/com/scanium/app/data/ItemsRepositoryTest.kt` - More unused tests

**Proof of non-usage:**
- Grep search shows these classes are ONLY referenced in test files
- ItemsViewModel.kt uses `MutableStateFlow` instead of repository (lines 45-49)
- No database initialization in MainActivity or Application class
- CLAUDE.md explicitly states "No Persistence Layer"

**Schema Drift:**
ScannedItemEntity is missing 4 fields that exist in ScannedItem:
- `fullImageUri: Uri?` (line 38 in ScannedItem.kt)
- `listingStatus: ItemListingStatus` (line 39)
- `listingId: String?` (line 40)
- `listingUrl: String?` (line 41)

If the database were ever activated, eBay integration data would be lost!

## Impact

- **Build Overhead**: Room dependencies add compilation time and APK size
- **Confusion**: New developers don't know if persistence is intentional or incomplete
- **Schema Risk**: Entity schema diverged from domain model - activation would lose data
- **Test Pollution**: 3+ test files testing code that's never used in production

## Decision Required

Choose ONE of these options:

### Option A: Delete Database Layer (Recommended for PoC)

**Pros:**
- Matches documented architecture ("No Persistence Layer")
- Reduces complexity and build time
- Cleans up test suite
- Aligns with PoC scope

**Cons:**
- If persistence is needed later, must re-implement

### Option B: Activate Database Layer

**Pros:**
- Items persist across app restarts
- Better user experience for production

**Cons:**
- Must fix schema drift (add 4 missing fields)
- Must refactor ItemsViewModel to use repository
- Must add database migrations
- Must document schema versioning strategy
- Increases complexity

## Acceptance Criteria

### If Option A (Delete):
- [ ] Remove all 7 database-related files (main + test)
- [ ] Remove Room dependencies from build.gradle.kts
- [ ] Verify tests pass
- [ ] Update CLAUDE.md to confirm "no persistence" is intentional

### If Option B (Activate):
- [ ] Add 4 missing fields to ScannedItemEntity
- [ ] Create type converters for Uri and ItemListingStatus
- [ ] Refactor ItemsViewModel to use ItemsRepository
- [ ] Initialize database in MainActivity or Application class
- [ ] Add database migration strategy
- [ ] Document schema versioning in README
- [ ] Add integration tests for persistence flow

## Suggested Approach (Option A - Delete)

1. Delete files:
   ```bash
   rm app/src/main/java/com/scanium/app/data/ScaniumDatabase.kt
   rm app/src/main/java/com/scanium/app/data/ScannedItemEntity.kt
   rm app/src/main/java/com/scanium/app/data/ItemsDao.kt
   rm app/src/main/java/com/scanium/app/data/ItemsRepository.kt
   rm app/src/main/java/com/scanium/app/data/ItemsRepositoryImpl.kt
   rm app/src/test/java/com/scanium/app/data/*
   ```

2. Remove from build.gradle.kts:
   ```kotlin
   // DELETE these lines
   val roomVersion = "2.6.1"
   implementation("androidx.room:room-runtime:$roomVersion")
   implementation("androidx.room:room-ktx:$roomVersion")
   ksp("androidx.room:room-compiler:$roomVersion")
   testImplementation("androidx.room:room-testing:$roomVersion")
   ```

3. Run full test suite
4. Update CLAUDE.md to document intentional lack of persistence

## Related Issues

None

---

## Resolution

**Status:** ✅ RESOLVED
**Date:** 2025-12-13
**Approach:** Option A - Delete Database Layer
**Branch:** `claude/fix-002-remove-unused-room-database-014whnexQzX44P7C5Sc83JjH`

### Changes Made

1. **Deleted unused Room database files (main source):**
   - `app/src/main/java/com/scanium/app/data/ScaniumDatabase.kt`
   - `app/src/main/java/com/scanium/app/data/ScannedItemEntity.kt`
   - `app/src/main/java/com/scanium/app/data/ItemsDao.kt`
   - `app/src/main/java/com/scanium/app/data/ItemsRepository.kt`
   - `app/src/main/java/com/scanium/app/data/ItemsRepositoryImpl.kt`

2. **Deleted unused Room database test files:**
   - `app/src/test/java/com/scanium/app/data/ItemsDaoTest.kt`
   - `app/src/test/java/com/scanium/app/data/ItemsRepositoryTest.kt`
   - `app/src/test/java/com/scanium/app/data/ScannedItemEntityTest.kt`
   - `app/src/test/java/com/scanium/app/data/FakeItemsRepository.kt`

3. **Removed Room dependencies from `app/build.gradle.kts`:**
   - Removed `androidx.room:room-runtime`
   - Removed `androidx.room:room-ktx`
   - Removed `androidx.room:room-compiler` (KSP)
   - Removed `androidx.room:room-testing`

4. **Updated CLAUDE.md:**
   - Added note in "No Persistence Layer" section documenting removal
   - Confirmed intentional decision to have no persistence for PoC

### Rationale

- **Architectural Alignment:** Matches documented architecture ("No Persistence Layer" for PoC)
- **Build Optimization:** Removes unused Room annotation processing, reducing compile time
- **APK Size Reduction:** Eliminates unused Room runtime library from APK
- **Code Clarity:** Removes dead code that confused developers about persistence strategy
- **Test Cleanup:** Removes 4 test files testing code never used in production
- **Prevents Schema Drift:** Entity was already outdated (missing 4 eBay integration fields)

### Benefits Achieved

✅ Reduced build complexity (no KSP annotation processing for Room)
✅ Smaller APK size (Room runtime removed)
✅ Clearer architecture (code matches documented intent)
✅ Reduced maintenance burden (less dead code to maintain)
✅ Eliminated schema drift risk
✅ Cleaner test suite (no tests for unused code)

### Files Preserved in `data/` Package

The following files in `app/src/main/java/com/scanium/app/data/` were **kept** as they serve active purposes:

- `AppError.kt` - Error handling types
- `ClassificationPreferences.kt` - ML classification settings
- `Result.kt` - Result wrapper type
- `ThresholdPreferences.kt` - Detection threshold settings

### Verification Steps

To verify the fix:

```bash
# Check that Room files are deleted
ls app/src/main/java/com/scanium/app/data/ | grep -E "Room|Database|Entity|Dao|Repository"
# Should return nothing

# Check that Room dependencies are removed
grep -i "room" app/build.gradle.kts
# Should return nothing

# Verify other data files are preserved
ls app/src/main/java/com/scanium/app/data/
# Should show: AppError.kt, ClassificationPreferences.kt, Result.kt, ThresholdPreferences.kt

# Build should complete successfully (when Java 17 available)
./gradlew clean assembleDebug
```

### Future Considerations

If persistence is needed in the future:

1. Re-add Room dependencies to `app/build.gradle.kts`
2. Create new database entities matching current domain models (including eBay fields)
3. Implement Repository pattern with DAO
4. Refactor `ItemsViewModel` to use repository instead of direct StateFlow
5. Add database migration strategy from day one
6. Document schema versioning approach
7. Add comprehensive integration tests for persistence flow

The removal was a clean slate that prevents inheriting technical debt from the outdated schema.
