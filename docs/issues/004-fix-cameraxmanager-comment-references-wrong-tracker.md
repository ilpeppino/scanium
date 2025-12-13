# Fix Incorrect Tracker Reference in CameraXManager Comment

**Labels:** `documentation`, `priority:p2`, `area:camera`
**Type:** Documentation Bug
**Severity:** Low

## Problem

CameraXManager.kt line 192 has a KDoc comment that says "uses CandidateTracker" but the actual code uses **ObjectTracker**.

## Location

File: `/app/src/main/java/com/scanium/app/camera/CameraXManager.kt`
Line: 192

## Current Code

```kotlin
/**
 * Starts continuous scanning mode with multi-frame candidate tracking.
 * Captures frames periodically and uses CandidateTracker to promote only stable detections.
 */
private fun startScanning(scanMode: ScanMode, onResult: (List<ScannedItem>) -> Unit) {
    // ... actual code uses objectTracker (which is ObjectTracker, not CandidateTracker)
}
```

## Expected Behavior

Comment should say "ObjectTracker" since that's what's actually used.

## Acceptance Criteria

- [ ] Update comment to reference ObjectTracker instead of CandidateTracker
- [ ] Ensure comment accurately describes the tracking system

## Suggested Fix

```kotlin
/**
 * Starts continuous scanning mode with multi-frame object tracking.
 * Captures frames periodically and uses ObjectTracker to promote only stable detections
 * that meet confirmation criteria (frame count, confidence, box area).
 */
```

## Related Issues

- Issue #001 (Remove duplicate CandidateTracker dead code)
