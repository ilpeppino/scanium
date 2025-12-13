***REMOVED*** Fix Incorrect Tracker Reference in CameraXManager Comment

**Labels:** `documentation`, `priority:p2`, `area:camera`
**Type:** Documentation Bug
**Severity:** Low

***REMOVED******REMOVED*** Problem

CameraXManager.kt line 192 has a KDoc comment that says "uses CandidateTracker" but the actual code uses **ObjectTracker**.

***REMOVED******REMOVED*** Location

File: `/app/src/main/java/com/scanium/app/camera/CameraXManager.kt`
Line: 192

***REMOVED******REMOVED*** Current Code

```kotlin
/**
 * Starts continuous scanning mode with multi-frame candidate tracking.
 * Captures frames periodically and uses CandidateTracker to promote only stable detections.
 */
private fun startScanning(scanMode: ScanMode, onResult: (List<ScannedItem>) -> Unit) {
    // ... actual code uses objectTracker (which is ObjectTracker, not CandidateTracker)
}
```

***REMOVED******REMOVED*** Expected Behavior

Comment should say "ObjectTracker" since that's what's actually used.

***REMOVED******REMOVED*** Acceptance Criteria

- [ ] Update comment to reference ObjectTracker instead of CandidateTracker
- [ ] Ensure comment accurately describes the tracking system

***REMOVED******REMOVED*** Suggested Fix

```kotlin
/**
 * Starts continuous scanning mode with multi-frame object tracking.
 * Captures frames periodically and uses ObjectTracker to promote only stable detections
 * that meet confirmation criteria (frame count, confidence, box area).
 */
```

***REMOVED******REMOVED*** Related Issues

- Issue ***REMOVED***001 (Remove duplicate CandidateTracker dead code)
