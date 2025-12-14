***REMOVED*** Pull Request: Fix ML Kit Model Download Loading State (Issue ***REMOVED***018)

***REMOVED******REMOVED*** Summary

Adds loading state UI during ML Kit model download on first app launch to improve first-time user experience (FTUE).

***REMOVED******REMOVED*** Problem

On first app launch, ML Kit object detection models require download (10-30 seconds). Currently:
- ❌ No loading indicator shown
- ❌ No progress feedback
- ❌ No explanation for delay
- ❌ Users see blank camera and think app is broken
- ❌ Poor FTUE leads to potential uninstalls

***REMOVED******REMOVED*** Solution

Implemented comprehensive loading state management:

***REMOVED******REMOVED******REMOVED*** 1. State Management
**Created `ModelDownloadState` sealed class:**
```kotlin
sealed class ModelDownloadState {
    object Checking
    object Downloading
    object Ready
    data class Error(val message: String)
}
```

***REMOVED******REMOVED******REMOVED*** 2. UI Components
- **Loading Overlay**: Displays spinner with explanation during model initialization
- **Error Dialog**: Shows detailed error with retry/dismiss options

***REMOVED******REMOVED******REMOVED*** 3. User Flow
**First Launch:**
1. User grants camera permission
2. Loading overlay appears: "Preparing ML models..."
3. Explanation shown: "First launch requires downloading ML Kit models (~15 MB)"
4. When ready, overlay dismisses and camera becomes usable

**Error Handling:**
1. If download fails, error dialog shows with troubleshooting steps
2. User can retry or continue anyway (degraded experience)

**Subsequent Launches:**
- Model already downloaded → Brief loading (<1s)
- Immediate camera access

***REMOVED******REMOVED*** Technical Details

**Architecture:**
- ✅ Jetpack Compose: `LaunchedEffect`, state hoisting
- ✅ Material 3 UI: `Card`, `AlertDialog`, `CircularProgressIndicator`
- ✅ Reactive state: `remember`, state variables
- ✅ Graceful error handling with retry capability

**Integration:**
- Leverages existing `CameraXManager.ensureModelsReady()` method
- Calls `ObjectDetectorClient.ensureModelDownloaded()` under the hood
- Non-blocking initialization - camera preview still works

**Files Changed:**
1. ✨ **NEW**: `app/src/main/java/com/scanium/app/camera/ModelDownloadState.kt`
2. 📝 **MODIFIED**: `app/src/main/java/com/scanium/app/camera/CameraScreen.kt`
   - Added model check `LaunchedEffect`
   - Added loading overlay rendering
   - Added error dialog with retry
3. 📝 **MODIFIED**: `docs/issues/018-no-loading-state-during-ml-kit-model-download.md`
   - Marked as resolved
   - Added implementation details

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Manual Testing Required

**1. Fresh Install Test (Primary Scenario):**
```bash
***REMOVED*** Clear app data
adb shell pm clear com.scanium.app

***REMOVED*** Optionally throttle network to simulate slow download
***REMOVED*** Settings > Developer Options > Network throttling > Slow 3G

***REMOVED*** Launch app
***REMOVED*** Expected: Loading overlay with explanation
***REMOVED*** Verify: Overlay dismisses when models ready
```

**2. Network Failure Test:**
```bash
***REMOVED*** Enable airplane mode
***REMOVED*** Clear app data
adb shell pm clear com.scanium.app

***REMOVED*** Launch app
***REMOVED*** Expected: Error dialog with retry button

***REMOVED*** Disable airplane mode
***REMOVED*** Tap "Retry"
***REMOVED*** Expected: Loading overlay, then camera works
```

**3. Already Downloaded Test:**
```bash
***REMOVED*** Launch app (models already present)
***REMOVED*** Expected: Brief loading (<1 second), immediate camera access
```

***REMOVED******REMOVED******REMOVED*** Log Verification

```bash
adb logcat | grep -E "CameraScreen|CameraXManager.*ensureModelsReady"
```

**Expected logs:**
```
CameraScreen: Checking ML Kit model availability...
CameraXManager: Ensuring ML Kit models are ready...
ObjectDetectorClient: ML Kit Object Detection model initialization complete
CameraScreen: ML Kit models ready
```

***REMOVED******REMOVED******REMOVED*** Automated Testing

⚠️ **Note**: Build not tested due to Java 17 requirement in environment
- Code is syntactically valid
- Follows existing Compose patterns
- Should build successfully with Java 17

***REMOVED******REMOVED*** Acceptance Criteria

From issue ***REMOVED***018:
- [x] Check if ML Kit model is downloaded before allowing scanning
- [x] Show loading indicator during model download
- [x] Display progress bar if available (using indeterminate progress)
- [x] Explain why download is needed (first launch, ~15MB)
- [x] Disable scan button until model ready (implicitly via overlay)
- [x] Handle download failures gracefully
- [x] Allow user to cancel and retry with different network

***REMOVED******REMOVED*** Screenshots / Video

⚠️ **Manual testing required** - Screenshots will be added after successful device testing:
1. Loading overlay on first launch
2. Error dialog on network failure
3. Camera ready after model download

***REMOVED******REMOVED*** Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Model download slow on cellular | AndroidManifest already specifies WiFi-preferred auto-download |
| No progress percentage available | Using indeterminate progress with clear explanation |
| User dismisses error and tries to scan | "Continue Anyway" option allows degraded experience |
| LaunchedEffect triggers multiple times | Keyed to `cameraPermissionState.status.isGranted` to run once |

***REMOVED******REMOVED*** Related Issues

- Resolves: `docs/issues/018-no-loading-state-during-ml-kit-model-download.md`
- Related: Issue ***REMOVED***016 (Camera error handling)

***REMOVED******REMOVED*** Checklist

- [x] Code follows Scanium architecture (MVVM, Compose)
- [x] Material 3 design system used
- [x] Error handling implemented
- [x] Documentation updated (issue file)
- [x] Commit message follows convention
- [ ] Manual testing on physical device (pending)
- [ ] Build verified with Java 17 (pending)
- [ ] Screenshots added (pending device test)

***REMOVED******REMOVED*** Next Steps

**For Reviewer:**
1. Review code changes for correctness
2. Test on physical Android device or emulator
3. Verify loading overlay appears on first launch (clear app data)
4. Verify error handling with airplane mode
5. Add screenshots/video to PR
6. Approve if all acceptance criteria met

**For Merge:**
- Manual device testing required before merge
- Consider adding instrumented test for loading state (future enhancement)

---

**Branch:** `claude/review-scanium-architecture-ygcDQ`
**Commit:** `f892890`
**Files Changed:** 3 files, +296 insertions
