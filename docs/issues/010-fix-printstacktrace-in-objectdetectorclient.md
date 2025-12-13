***REMOVED*** Replace printStackTrace() with Proper Logging

**Labels:** `bug`, `priority:p2`, `area:ml`, `logging`
**Type:** Code Quality Bug
**Severity:** Medium

***REMOVED******REMOVED*** Problem

`ObjectDetectorClient.kt` line 373 uses `printStackTrace()` which is an anti-pattern in Android development. Stack traces should go through Android's logging system for proper filtering and log level control.

***REMOVED******REMOVED*** Location

File: `/app/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`
Line: 373

***REMOVED******REMOVED*** Current Code

```kotlin
} catch (e: Exception) {
    Log.e(TAG, ">>> ERROR in detectObjectsWithTracking", e)
    Log.e(TAG, ">>> Exception stack trace:")
    e.printStackTrace()  // ❌ ANTI-PATTERN
    emptyList()
}
```

***REMOVED******REMOVED*** Why This Is Bad

- **No log filtering**: printStackTrace() bypasses Android log levels
- **Production leaks**: Stack traces appear in production even if logs disabled
- **Log loss**: Output might not be captured by logging systems
- **Inconsistent**: Rest of codebase uses `Log.e(TAG, message, exception)`

***REMOVED******REMOVED*** Expected Behavior

Exception should be logged using Android's Log.e() with the exception parameter, which automatically includes the stack trace.

***REMOVED******REMOVED*** Acceptance Criteria

- [ ] Remove `e.printStackTrace()` call
- [ ] Verify `Log.e(TAG, message, e)` already includes stack trace
- [ ] Ensure no other printStackTrace() calls exist in codebase

***REMOVED******REMOVED*** Suggested Fix

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

***REMOVED******REMOVED*** Verification

After fix, test that stack traces still appear in logcat:

```bash
adb logcat | grep -A 20 "ERROR in detectObjectsWithTracking"
***REMOVED*** Should show full stack trace from Log.e(TAG, msg, exception)
```

***REMOVED******REMOVED*** Related Issues

None
