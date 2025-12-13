***REMOVED*** Remove or Activate Unused Room Database Layer

**Labels:** `tech-debt`, `priority:p0`, `area:data`, `architectural-drift`
**Type:** Architectural Drift
**Severity:** Critical

***REMOVED******REMOVED*** Problem

A complete Room database persistence layer is fully implemented but **completely unused** in the main application code. This creates:

- Architectural confusion (docs say "no persistence" but persistence layer exists)
- Wasted dependencies and build overhead
- Schema drift risk (entity missing fields from domain model)

***REMOVED******REMOVED*** Evidence

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

***REMOVED******REMOVED*** Impact

- **Build Overhead**: Room dependencies add compilation time and APK size
- **Confusion**: New developers don't know if persistence is intentional or incomplete
- **Schema Risk**: Entity schema diverged from domain model - activation would lose data
- **Test Pollution**: 3+ test files testing code that's never used in production

***REMOVED******REMOVED*** Decision Required

Choose ONE of these options:

***REMOVED******REMOVED******REMOVED*** Option A: Delete Database Layer (Recommended for PoC)

**Pros:**
- Matches documented architecture ("No Persistence Layer")
- Reduces complexity and build time
- Cleans up test suite
- Aligns with PoC scope

**Cons:**
- If persistence is needed later, must re-implement

***REMOVED******REMOVED******REMOVED*** Option B: Activate Database Layer

**Pros:**
- Items persist across app restarts
- Better user experience for production

**Cons:**
- Must fix schema drift (add 4 missing fields)
- Must refactor ItemsViewModel to use repository
- Must add database migrations
- Must document schema versioning strategy
- Increases complexity

***REMOVED******REMOVED*** Acceptance Criteria

***REMOVED******REMOVED******REMOVED*** If Option A (Delete):
- [ ] Remove all 7 database-related files (main + test)
- [ ] Remove Room dependencies from build.gradle.kts
- [ ] Verify tests pass
- [ ] Update CLAUDE.md to confirm "no persistence" is intentional

***REMOVED******REMOVED******REMOVED*** If Option B (Activate):
- [ ] Add 4 missing fields to ScannedItemEntity
- [ ] Create type converters for Uri and ItemListingStatus
- [ ] Refactor ItemsViewModel to use ItemsRepository
- [ ] Initialize database in MainActivity or Application class
- [ ] Add database migration strategy
- [ ] Document schema versioning in README
- [ ] Add integration tests for persistence flow

***REMOVED******REMOVED*** Suggested Approach (Option A - Delete)

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

***REMOVED******REMOVED*** Related Issues

None
