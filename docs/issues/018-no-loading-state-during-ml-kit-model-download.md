# No Loading State During ML Kit Model Download (First Launch)

**Labels:** `ux`, `priority:p2`, `area:ml`, `area:camera`
**Type:** UX Issue
**Severity:** Medium

## Problem

On first app launch, ML Kit must download object detection models (can take 10-30 seconds). During this time:
- Camera shows blank preview
- No loading indicator
- No progress bar
- No explanation
- Users think app is broken

## Context

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

## Current Behavior

### First Launch Scenario:

1. User installs app
2. User opens app (model still downloading)
3. Camera preview shows
4. User tries to scan
5. **Nothing happens** (no detections because model not ready)
6. User confused, force-closes app

### ObjectDetectorClient Attempts Validation:

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

## Expected Behavior

### On First Launch:

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

### After Model Ready:

- Loading overlay dismisses
- Camera becomes fully active
- User can scan normally

## Impact

**Severity**: Medium - Critical for first-time user experience

**User Impact**:
- Poor onboarding experience
- Users think app is broken
- High uninstall rate after first launch
- Negative reviews

**Frequency**: Affects ALL users on first launch

## Acceptance Criteria

- [ ] Check if ML Kit model is downloaded before allowing scanning
- [ ] Show loading indicator during model download
- [ ] Display progress bar if available
- [ ] Explain why download is needed (first launch, ~15MB)
- [ ] Disable scan button until model ready
- [ ] Handle download failures gracefully
- [ ] Allow user to cancel and retry with different network

## Suggested Implementation

### 1. Model Ready State

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

### 2. Model Check on Camera Launch

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

### 3. Loading Overlay UI

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

### 4. Disable Scanning Until Ready

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

### 5. Network Error Handling

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

## Alternative: Proactive Download

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

## Testing

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

## Related Issues

- Issue #016 (Camera error handling)
