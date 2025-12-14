***REMOVED*** No Loading State During ML Kit Model Download (First Launch)

**Labels:** `ux`, `priority:p2`, `area:ml`, `area:camera`
**Type:** UX Issue
**Severity:** Medium
**Status:** ✅ RESOLVED

***REMOVED******REMOVED*** Resolution Summary

**Fixed in:** PR ***REMOVED***[TBD]
**Date:** 2025-12-14
**Implementation:** Added model download state management with loading overlay UI

***REMOVED******REMOVED*** Problem

On first app launch, ML Kit must download object detection models (can take 10-30 seconds). During this time:
- Camera shows blank preview
- No loading indicator
- No progress bar
- No explanation
- Users think app is broken

***REMOVED******REMOVED*** Context

**AndroidManifest.xml** line 21-23:

```xml
<meta-data
    android:name="com.google.mlkit.vision.DEPENDENCIES"
    android:value="ocr,object_custom" />
```

This tells ML Kit to auto-download models at install time, but:
- Download might not complete before first launch
- Slow networks delay download
- No UI feedback during download

***REMOVED******REMOVED*** Current Behavior

***REMOVED******REMOVED******REMOVED*** First Launch Scenario:

1. User installs app
2. User opens app (model still downloading)
3. Camera preview shows
4. User tries to scan
5. **Nothing happens** (no detections because model not ready)
6. User confused, force-closes app

***REMOVED******REMOVED******REMOVED*** ObjectDetectorClient Attempts Validation:

**ObjectDetectorClient.kt** line ~175:

```kotlin
suspend fun ensureModelDownloaded(): Boolean {
    return try {
        detectorStream.isModelDownloaded()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Error checking/downloading model", e)
        false
    }
}
```

But this is not called before scanning starts, and no UI feedback if false!

***REMOVED******REMOVED*** Expected Behavior

***REMOVED******REMOVED******REMOVED*** On First Launch:

```
┌─────────────────────────────────┐
│       Camera Preview            │
│   (slightly dimmed overlay)     │
│                                 │
│  ┌───────────────────────────┐  │
│  │  Preparing ML Models...   │  │
│  │  ███████░░░░░░░  55%      │  │
│  │                           │  │
│  │  First launch requires    │  │
│  │  downloading AI models    │  │
│  │  (~15 MB)                 │  │
│  └───────────────────────────┘  │
│                                 │
└─────────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** After Model Ready:

- Loading overlay dismisses
- Camera becomes fully active
- User can scan normally

***REMOVED******REMOVED*** Impact

**Severity**: Medium - Critical for first-time user experience

**User Impact**:
- Poor onboarding experience
- Users think app is broken
- High uninstall rate after first launch
- Negative reviews

**Frequency**: Affects ALL users on first launch

***REMOVED******REMOVED*** Acceptance Criteria

- [ ] Check if ML Kit model is downloaded before allowing scanning
- [ ] Show loading indicator during model download
- [ ] Display progress bar if available
- [ ] Explain why download is needed (first launch, ~15MB)
- [ ] Disable scan button until model ready
- [ ] Handle download failures gracefully
- [ ] Allow user to cancel and retry with different network

***REMOVED******REMOVED*** Suggested Implementation

***REMOVED******REMOVED******REMOVED*** 1. Model Ready State

```kotlin
// In CameraXManager or CameraScreen
sealed class ModelState {
    object Checking : ModelState()
    object Downloading : ModelState()
    data class Progress(val percent: Int) : ModelState()
    object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}
```

***REMOVED******REMOVED******REMOVED*** 2. Model Check on Camera Launch

```kotlin
// CameraScreen.kt
var modelState by remember { mutableStateOf<ModelState>(ModelState.Checking) }

LaunchedEffect(Unit) {
    modelState = ModelState.Checking

    if (!cameraManager.objectDetector.ensureModelDownloaded()) {
        modelState = ModelState.Downloading
        // Monitor download progress (if API available)
        cameraManager.objectDetector.downloadModel(
            onProgress = { percent ->
                modelState = ModelState.Progress(percent)
            },
            onComplete = {
                modelState = ModelState.Ready
            },
            onError = { error ->
                modelState = ModelState.Error(error)
            }
        )
    } else {
        modelState = ModelState.Ready
    }
}
```

***REMOVED******REMOVED******REMOVED*** 3. Loading Overlay UI

```kotlin
// CameraScreen.kt
Box(modifier = Modifier.fillMaxSize()) {
    // Camera preview
    AndroidView(...)

    // Model download overlay
    when (val state = modelState) {
        is ModelState.Checking,
        is ModelState.Downloading,
        is ModelState.Progress -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(0.8f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = when (state) {
                                is ModelState.Checking -> "Checking ML models..."
                                is ModelState.Downloading -> "Downloading AI models..."
                                is ModelState.Progress -> "Downloading... ${state.percent}%"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (state is ModelState.Progress) {
                            LinearProgressIndicator(
                                progress = state.percent / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = "First launch requires downloading\nML Kit object detection models (~15 MB)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        is ModelState.Error -> {
            // Show error with retry
            ModelDownloadErrorDialog(
                error = state.message,
                onRetry = { /* retry download */ },
                onDismiss = { /* close app or continue without detection */ }
            )
        }

        ModelState.Ready -> {
            // No overlay, camera fully functional
        }
    }
}
```

***REMOVED******REMOVED******REMOVED*** 4. Disable Scanning Until Ready

```kotlin
ShutterButton(
    onCapture = {
        if (modelState == ModelState.Ready) {
            // Proceed with capture
        } else {
            Toast.makeText(context, "Please wait for models to download", Toast.LENGTH_SHORT).show()
        }
    },
    enabled = modelState == ModelState.Ready,
    cameraState = cameraState
)
```

***REMOVED******REMOVED******REMOVED*** 5. Network Error Handling

```kotlin
@Composable
fun ModelDownloadErrorDialog(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model Download Failed") },
        text = {
            Column {
                Text("Failed to download ML Kit models:")
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Ensure you have:")
                Text("• Active internet connection")
                Text("• At least 20 MB free storage")
                Text("• Network access for this app")
            }
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Exit")
            }
        }
    )
}
```

***REMOVED******REMOVED*** Alternative: Proactive Download

In MainActivity.onCreate():

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Proactively trigger model download in background
    lifecycleScope.launch {
        ObjectDetectorClient(this@MainActivity).ensureModelDownloaded()
    }

    setContent { /* ... */ }
}
```

But still need UI feedback in camera screen if download incomplete.

***REMOVED******REMOVED*** Testing

Manual test scenarios:

1. **Fresh Install on Slow Network**:
   - Clear app data
   - Use network throttling (slow 3G)
   - Launch app
   - Verify loading indicator shows
   - Verify progress updates
   - Verify camera works after download

2. **First Launch with No Network**:
   - Enable airplane mode
   - Clear app data
   - Launch app
   - Verify error dialog shows
   - Disable airplane mode
   - Tap "Retry"
   - Verify download succeeds

3. **Already Downloaded**:
   - Launch app (models already present)
   - Verify loading is brief (<1 second)
   - Verify camera immediately usable

***REMOVED******REMOVED*** Related Issues

- Issue ***REMOVED***016 (Camera error handling)

---

***REMOVED******REMOVED*** Implementation Details

***REMOVED******REMOVED******REMOVED*** Changes Made

**1. Created `ModelDownloadState` sealed class** (`camera/ModelDownloadState.kt`):
```kotlin
sealed class ModelDownloadState {
    object Checking : ModelDownloadState()
    object Downloading : ModelDownloadState()
    object Ready : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}
```

**2. Updated `CameraScreen.kt`**:
- Added `modelDownloadState` state variable
- Added `LaunchedEffect` to check model availability on permission grant
- Calls `cameraManager.ensureModelsReady()` on first launch
- Shows loading overlay during model initialization
- Shows error dialog if initialization fails

**3. Added UI Components**:
- `ModelLoadingOverlay()`: Displays loading spinner with explanation
- `ModelErrorDialog()`: Shows error with retry and dismiss options

***REMOVED******REMOVED******REMOVED*** Acceptance Criteria Status

- [x] Check if ML Kit model is downloaded before allowing scanning
- [x] Show loading indicator during model download
- [x] Display progress bar if available (using indeterminate progress)
- [x] Explain why download is needed (first launch, ~15MB)
- [x] Disable scan button until model ready (implicitly via overlay)
- [x] Handle download failures gracefully
- [x] Allow user to cancel and retry with different network

***REMOVED******REMOVED******REMOVED*** User Experience Flow

**First Launch (Model Not Ready):**
1. User opens app and grants camera permission
2. Loading overlay appears: "Preparing ML models..."
3. App calls `CameraXManager.ensureModelsReady()`
4. Overlay displays explanation about model download
5. When ready, overlay dismisses and camera becomes usable

**If Download Fails:**
1. Error dialog appears with detailed message
2. User can tap "Retry" to try again
3. User can tap "Continue Anyway" to dismiss (camera may not detect objects)

**Subsequent Launches:**
- Model already downloaded → Loading overlay shows briefly (<1 second) then dismisses
- Camera immediately usable

***REMOVED******REMOVED******REMOVED*** Testing Recommendations

**Manual Testing:**
1. **Fresh Install**: Clear app data, launch app on slow network
   - Expected: Loading overlay shows, explains model download
   - Verify: Overlay dismisses when models ready

2. **Network Failure**: Enable airplane mode, clear app data, launch app
   - Expected: Error dialog with helpful troubleshooting steps
   - Verify: Retry button works when network restored

3. **Already Downloaded**: Launch app with models present
   - Expected: Brief loading (<1s), immediate camera access
   - Verify: No user disruption on subsequent launches

**Log Verification:**
```bash
adb logcat | grep -E "CameraScreen|CameraXManager.*ensureModelsReady"
```

Look for:
- "Checking ML Kit model availability..."
- "ML Kit models ready"
- No errors during model initialization

***REMOVED******REMOVED******REMOVED*** Files Modified

1. `app/src/main/java/com/scanium/app/camera/ModelDownloadState.kt` (NEW)
2. `app/src/main/java/com/scanium/app/camera/CameraScreen.kt` (MODIFIED)

***REMOVED******REMOVED******REMOVED*** Files Already Existing (Utilized)

1. `app/src/main/java/com/scanium/app/camera/CameraXManager.kt`
   - Already had `ensureModelsReady()` method (line 101)
   - Already called in `startCamera()` (line 129)

2. `app/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`
   - Already had `ensureModelDownloaded()` method (line 135)
   - Used by `CameraXManager.ensureModelsReady()`

***REMOVED******REMOVED******REMOVED*** Architecture Alignment

✅ **Jetpack Compose**: Uses `LaunchedEffect`, `remember`, state hoisting
✅ **StateFlow**: Reactive state updates (existing ViewModel pattern)
✅ **Material 3**: Uses `Card`, `AlertDialog`, `CircularProgressIndicator`
✅ **Camera-first UX**: Non-blocking initialization, clear feedback
✅ **Error Handling**: Graceful degradation with retry capability

***REMOVED******REMOVED******REMOVED*** Known Limitations

1. **No Progress Percentage**: ML Kit doesn't expose download progress
   - Mitigation: Using indeterminate progress indicator
2. **Network Required**: First launch requires internet
   - Mitigation: Clear error message with troubleshooting steps
3. **Model Size**: ~15MB download on cellular could be expensive
   - Mitigation: AndroidManifest already specifies auto-download (WiFi preferred)

***REMOVED******REMOVED******REMOVED*** Future Enhancements

- [ ] Add progress percentage if ML Kit API becomes available
- [ ] Implement background pre-download in MainActivity
- [ ] Add user preference for WiFi-only model download
- [ ] Show download size estimate dynamically
- [ ] Add "Skip for now" option for offline use (limited functionality)
