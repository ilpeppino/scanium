# ItemsDao Uses Risky OnConflictStrategy.REPLACE

**Labels:** `code-quality`, `priority:p3`, `area:data`
**Type:** Code Quality Issue
**Severity:** Low (only if database activated)

## Problem

`ItemsDao.kt` uses `OnConflictStrategy.REPLACE` for inserts, which will **overwrite entire rows** on ID collision. This could cause data loss if the schema grows and old code tries to insert items.

## Location

File: `/app/src/main/java/com/scanium/app/data/ItemsDao.kt`
Lines: 60, 67

## Current Code

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insert(item: ScannedItemEntity)

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAll(items: List<ScannedItemEntity>)
```

## Scenario Where This Causes Problems

1. **Version 1**: Schema has fields A, B, C
2. **User adds item**: DB stores A, B, C with values
3. **Version 2**: Schema adds field D (migration adds column with default NULL)
4. **Old code path**: Tries to insert same ID with only A, B, C
5. **Result**: REPLACE overwrites row, field D becomes NULL (data loss!)

## Expected Behavior (Better Approaches)

### Option 1: IGNORE + Explicit Update

```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insert(item: ScannedItemEntity)

@Update
suspend fun update(item: ScannedItemEntity)

// Usage
fun upsert(item: ScannedItemEntity) {
    insert(item)  // Insert if new
    update(item)  // Update if exists
}
```

### Option 2: Custom Upsert

```kotlin
@Query("""
    INSERT OR REPLACE INTO scanned_items (id, ..., newField)
    VALUES (:id, ..., COALESCE(:newField, (SELECT newField FROM scanned_items WHERE id = :id)))
""")
suspend fun upsert(...)
```

Preserves existing fields if new data doesn't provide them.

### Option 3: Document Intent

If REPLACE is truly desired, document why:

```kotlin
/**
 * Inserts item, replacing any existing item with same ID.
 *
 * WARNING: OnConflictStrategy.REPLACE will overwrite ALL fields.
 * Ensure all fields are populated before calling this method.
 * If partial updates are needed, use update() instead.
 */
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insert(item: ScannedItemEntity)
```

## Current Impact

**Low** - Database layer is currently unused (see Issue #002).

**Future Risk** - If database is activated and schema evolves, this could cause subtle bugs.

## Acceptance Criteria (if database is activated)

- [ ] Choose strategy: IGNORE+UPDATE, custom upsert, or documented REPLACE
- [ ] Implement chosen approach
- [ ] Add tests for conflict scenarios
- [ ] Document behavior in code comments

## Suggested Fix

### Recommended: Transaction-based Upsert

```kotlin
@Dao
interface ItemsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ScannedItemEntity): Long

    @Update
    suspend fun update(item: ScannedItemEntity)

    @Transaction
    suspend fun upsert(item: ScannedItemEntity) {
        val insertResult = insert(item)
        if (insertResult == -1L) {
            // Insert failed (conflict) - do update
            update(item)
        }
    }
}
```

## Related Issues

- Issue #002 (Remove or activate Room database) - BLOCKING
- Issue #008 (Schema drift in ScannedItemEntity)
