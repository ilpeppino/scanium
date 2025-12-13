# Remove SessionDeduplicator (Dead Code)

**Labels:** `tech-debt`, `priority:p1`, `area:items`
**Type:** Tech Debt
**Severity:** High

## Problem

`SessionDeduplicator.kt` is a 300-line session-level deduplication component that is fully implemented but **never called** in the main codebase. It has been replaced by `ItemAggregator`.

## Evidence

**Dead Code:**
- `/app/src/main/java/com/scanium/app/items/SessionDeduplicator.kt` ❌ NOT USED
- `/app/src/test/java/com/scanium/app/items/SessionDeduplicatorTest.kt` ❌ TESTS DEAD CODE

**Replacement:**
- `/app/src/main/java/com/scanium/app/aggregation/ItemAggregator.kt` ✅ ACTIVE
- ItemsViewModel.kt has comment: "Real-time item aggregator (replaces SessionDeduplicator)"

**Proof:**
- SessionDeduplicator is never instantiated or imported in any main code
- Only reference is the comment in ItemsViewModel acknowledging the replacement
- Tests exist but test unused code

## Impact

- **Code Bloat**: 300 lines of unused code in codebase
- **Maintenance Burden**: Developers might think it's still relevant
- **Test Pollution**: Tests for dead code waste CI time
- **Confusion**: Two deduplication systems exist but only one is used

## Acceptance Criteria

- [ ] Delete `/app/src/main/java/com/scanium/app/items/SessionDeduplicator.kt`
- [ ] Delete `/app/src/test/java/com/scanium/app/items/SessionDeduplicatorTest.kt`
- [ ] Update comment in ItemsViewModel.kt (remove reference to SessionDeduplicator)
- [ ] Verify all tests still pass

## Suggested Approach

1. Confirm ItemAggregator provides all functionality
2. Delete both SessionDeduplicator files (main + test)
3. Update ItemsViewModel comment to:
   ```kotlin
   // Real-time item aggregator for similarity-based deduplication
   ```
4. Commit with message: "Remove SessionDeduplicator (replaced by ItemAggregator)"

## Related Issues

- Issue #007 (ItemAggregator not documented in CLAUDE.md)
