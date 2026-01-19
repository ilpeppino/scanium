***REMOVED*** Remove Permanently Disabled Code Block (if false &&)

**Labels:** `tech-debt`, `priority:p2`, `area:ml`, `code-quality`
**Type:** Code Quality
**Severity:** Medium

***REMOVED******REMOVED*** Problem

ObjectDetectorClient.kt has a permanently disabled code block using `if (false && ...)` which should
either be removed entirely or properly handled with configuration.

***REMOVED******REMOVED*** Location

File: `/app/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`
Line: 310

***REMOVED******REMOVED*** Current Code

```kotlin
if (false && detectedObjects.isEmpty() && useStreamMode) {
    // DISABLED: Don't mix STREAM and SINGLE_IMAGE modes - causes ML Kit crashes
    Log.w(TAG, ">>> Zero objects in STREAM mode, trying SINGLE_IMAGE_MODE...")
    // ... disabled fallback code
}
```

***REMOVED******REMOVED*** Impact

- **Code Clutter**: Dead code that will never execute
- **Confusion**: Developers don't know if this is temporary or permanent
- **Maintenance**: Code must be maintained even though it's disabled
- **Missed Cleanup**: Comment says "DISABLED" but doesn't explain if/when to re-enable

***REMOVED******REMOVED*** Decision Required

Choose ONE:

***REMOVED******REMOVED******REMOVED*** Option A: Delete Entirely (Recommended)

If the ML Kit crash issue is resolved and this fallback is no longer needed.

***REMOVED******REMOVED******REMOVED*** Option B: Move to Feature Flag

If this might be useful for debugging specific devices:

```kotlin
if (BuildConfig.ENABLE_MODE_FALLBACK && detectedObjects.isEmpty() && useStreamMode) {
    // Fallback to SINGLE_IMAGE_MODE for debugging
}
```

***REMOVED******REMOVED******REMOVED*** Option C: Document Permanently

If this must stay disabled, add a comprehensive comment:

```kotlin
// PERMANENT FIX: Do not attempt SINGLE_IMAGE_MODE fallback.
// Mixing STREAM and SINGLE_IMAGE modes causes ML Kit native crashes on Android <8.
// See: crash.log (line XYZ) and md/fixes/ML_KIT_NATIVE_CRASH_FIX.md
// Decision: Keep STREAM mode only, accept zero-detection cases.
```

***REMOVED******REMOVED*** Acceptance Criteria

- [ ] Choose option A, B, or C based on project needs
- [ ] Implement chosen option
- [ ] Update ML Kit documentation if permanently removing fallback
- [ ] Verify tests pass

***REMOVED******REMOVED*** Suggested Approach (Option A)

1. Delete lines 310-323 in ObjectDetectorClient.kt
2. Keep the detailed diagnostic logging above it
3. Add comment explaining zero-detection acceptance:
   ```kotlin
   // If zero objects detected after all attempts, return empty list.
   // Do NOT attempt SINGLE_IMAGE_MODE fallback (causes ML Kit crashes).
   ```
4. Commit with message: "Remove disabled ML Kit mode fallback"

***REMOVED******REMOVED*** Related Issues

None
