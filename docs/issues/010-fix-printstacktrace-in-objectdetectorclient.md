# Replace printStackTrace() with Proper Logging

**Labels:** `bug`, `priority:p2`, `area:ml`, `logging`
**Type:** Code Quality Bug
**Severity:** Medium

## Problem

`ObjectDetectorClient.kt` line 373 uses `printStackTrace()` which is an anti-pattern in Android development. Stack traces should go through Android's logging system for proper filtering and log level control.

## Location

File: `/app/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`
Line: 373

## Current Code

```kotlin
} catch (e: Exception) {
    Log.e(TAG, ">>> ERROR in detectObjectsWithTracking", e)
    Log.e(TAG, ">>> Exception stack trace:")
    e.printStackTrace()  // ❌ ANTI-PATTERN
    emptyList()
}
```

## Why This Is Bad

- **No log filtering**: printStackTrace() bypasses Android log levels
- **Production leaks**: Stack traces appear in production even if logs disabled
- **Log loss**: Output might not be captured by logging systems
- **Inconsistent**: Rest of codebase uses `Log.e(TAG, message, exception)`

## Expected Behavior

Exception should be logged using Android's Log.e() with the exception parameter, which automatically includes the stack trace.

## Acceptance Criteria

- [x] Remove `e.printStackTrace()` call
- [x] Verify `Log.e(TAG, message, e)` already includes stack trace
- [x] Ensure no other printStackTrace() calls exist in codebase

## Suggested Fix

```kotlin
} catch (e: Exception) {
    Log.e(TAG, ">>> ERROR in detectObjectsWithTracking", e)
    // Stack trace is automatically included by Log.e() when exception is passed
    emptyList()
}
```

Or keep the detailed message:

```kotlin
} catch (e: Exception) {
    Log.e(TAG, """
        >>> ERROR in detectObjectsWithTracking
        >>> Exception type: ${e.javaClass.simpleName}
        >>> Exception message: ${e.message}
    """.trimIndent(), e)  // Exception includes full stack trace
    emptyList()
}
```

## Verification

After fix, test that stack traces still appear in logcat:

```bash
adb logcat | grep -A 20 "ERROR in detectObjectsWithTracking"
# Should show full stack trace from Log.e(TAG, msg, exception)
```

## Related Issues

None

---

## Resolution

**Status:** ✅ RESOLVED

**Changes Made:**

Fixed **TWO** `printStackTrace()` anti-patterns in the codebase (issue mentioned only one):

**1. ObjectDetectorClient.kt (lines 370-374):**

**Before:**
```kotlin
} catch (e: Exception) {
    Log.e(TAG, ">>> ERROR in detectObjectsWithTracking", e)
    Log.e(TAG, ">>> Exception stack trace:")
    e.printStackTrace()  // ❌ ANTI-PATTERN
    emptyList()
}
```

**After:**
```kotlin
} catch (e: Exception) {
    // Log.e() with exception parameter automatically includes full stack trace
    Log.e(TAG, ">>> ERROR in detectObjectsWithTracking", e)
    emptyList()
}
```

**2. CameraXManager.kt (line 152):**

**Before:**
```kotlin
} catch (e: Exception) {
    // Handle camera binding failure
    e.printStackTrace()  // ❌ ANTI-PATTERN (also missing TAG and message)
}
```

**After:**
```kotlin
} catch (e: Exception) {
    // Handle camera binding failure (Log.e includes full stack trace)
    Log.e(TAG, "Failed to bind camera use cases", e)
}
```

### Why This Fix Is Correct

**Android Log.e() Behavior:**
- When `Log.e(TAG, message, exception)` is called, Android automatically includes:
  - Exception type (e.g., `java.lang.RuntimeException`)
  - Exception message
  - Full stack trace with file:line references
  - Caused by chain (if exception has nested causes)

**Benefits:**
- ✅ Stack traces go through Android's log filtering system
- ✅ Respects log levels (can be filtered in production)
- ✅ Proper TAG for filtering by component
- ✅ Captured by logging systems (Logcat, Crashlytics, etc.)
- ✅ Consistent with rest of codebase

**No Behavior Change:**
- Stack traces still appear in logcat
- Same debugging information available
- Just routed through proper logging system

### Verification

**Codebase scan confirmed no remaining printStackTrace() calls:**
```bash
grep -r "printStackTrace" app/src/main/java/
# No results (only documentation references)
```

**All exception handling now uses proper logging:**
```kotlin
// Pattern used throughout codebase
Log.e(TAG, "Error message describing context", exception)
```

### Impact

**Before:**
- ❌ 2 instances of printStackTrace() anti-pattern
- ❌ CameraXManager exception had no TAG or descriptive message
- ❌ Stack traces bypass Android log filtering

**After:**
- ✅ All exceptions logged via Log.e() with exception parameter
- ✅ Proper TAGs for filtering
- ✅ Descriptive error messages
- ✅ Stack traces routed through Android logging system
- ✅ Consistent code quality across codebase

**Related Best Practices:**
- Never use `printStackTrace()` in Android (bypasses log system)
- Always pass exception to `Log.e()` as third parameter
- Include descriptive message explaining context
- Use appropriate TAG for component filtering
