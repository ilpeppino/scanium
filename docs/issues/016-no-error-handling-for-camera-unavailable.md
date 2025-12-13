***REMOVED*** Missing Error Handling for Camera Unavailable Scenarios

**Labels:** `bug`, `priority:p2`, `area:camera`, `error-handling`
**Type:** Error Handling Gap
**Severity:** Medium

***REMOVED******REMOVED*** Problem

The app doesn't gracefully handle scenarios where the camera is unavailable:
- Camera in use by another app
- Camera permission revoked at runtime
- Device has no camera (tablets, emulators)
- CameraX binding fails

Users see blank screen or app crashes without explanation.

***REMOVED******REMOVED*** Scenarios Not Handled

***REMOVED******REMOVED******REMOVED*** 1. Camera Binding Failure

**CameraXManager.kt** line ~152:

```kotlin
} catch (e: Exception) {
    e.printStackTrace()  // ❌ Only prints stack trace
    // No user feedback!
}
```

User sees blank screen, no explanation.

***REMOVED******REMOVED******REMOVED*** 2. Permission Denied

**CameraScreen.kt** line ~128:

```kotlin
when {
    cameraPermissionState.status.isGranted -> {
        // Show camera
    }
    else -> {
        // ❌ No UI shown when permission denied!
    }
}
```

After denying permission, user sees blank screen.

***REMOVED******REMOVED******REMOVED*** 3. Camera In Use

No check for camera availability before binding.

***REMOVED******REMOVED******REMOVED*** 4. No Camera Hardware

Manifest says `android:required="false"` but no runtime check.

***REMOVED******REMOVED*** Impact

**User Experience**:
- Confusion when camera doesn't appear
- No actionable error message
- Can't recover without force-closing app

**Crash Risk**: Unhandled exceptions could crash app

***REMOVED******REMOVED*** Expected Behavior

***REMOVED******REMOVED******REMOVED*** Permission Denied:

```
┌─────────────────────────────┐
│     Camera Permission       │
│         Required            │
│                             │
│  Scanium needs camera       │
│  access to detect objects   │
│                             │
│  [Grant Permission]         │
│  [Use Without Camera]       │
└─────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** Camera Unavailable:

```
┌─────────────────────────────┐
│   Camera Unavailable        │
│                             │
│  Camera is in use by        │
│  another app or cannot      │
│  be accessed.               │
│                             │
│  [Retry]  [Go to Items]     │
└─────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** No Camera Hardware:

```
┌─────────────────────────────┐
│   No Camera Detected        │
│                             │
│  This device doesn't have   │
│  a camera. You can still    │
│  view previously scanned    │
│  items.                     │
│                             │
│  [View Items]               │
└─────────────────────────────┘
```

***REMOVED******REMOVED*** Acceptance Criteria

- [ ] Handle permission denied state with UI prompt
- [ ] Handle camera binding failures with user-friendly message
- [ ] Detect if device has camera hardware
- [ ] Provide "Retry" and "View Items" actions on errors
- [ ] Add error state to CameraState enum
- [ ] Show toast or dialog for transient errors
- [ ] Log errors for debugging but don't expose technical details to users

***REMOVED******REMOVED*** Suggested Implementation

***REMOVED******REMOVED******REMOVED*** 1. Enhanced CameraState

```kotlin
enum class CameraState {
    IDLE,
    SCANNING,
    PROCESSING,
    ERROR_PERMISSION_DENIED,
    ERROR_CAMERA_UNAVAILABLE,
    ERROR_NO_CAMERA_HARDWARE,
    ERROR_BINDING_FAILED
}
```

***REMOVED******REMOVED******REMOVED*** 2. Permission Denied UI

```kotlin
// CameraScreen.kt
when {
    cameraPermissionState.status.isGranted -> {
        // Show camera
    }
    cameraPermissionState.status.shouldShowRationale -> {
        // User denied once, show rationale
        PermissionRationaleDialog(
            onGrantPermission = { cameraPermissionState.launchPermissionRequest() },
            onDismiss = { onNavigateToItems() }
        )
    }
    else -> {
        // Permission permanently denied
        PermissionDeniedScreen(
            onOpenSettings = { /* open app settings */ },
            onViewItems = { onNavigateToItems() }
        )
    }
}
```

***REMOVED******REMOVED******REMOVED*** 3. Camera Binding Error Handling

```kotlin
// CameraXManager.kt
fun bindCamera(previewView: PreviewView) {
    try {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()

        // Check if camera is available
        if (cameraProvider.availableCameraInfos.isEmpty()) {
            onError(CameraError.NO_CAMERA_HARDWARE)
            return
        }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
        onSuccess()

    } catch (e: CameraAccessException) {
        Log.e(TAG, "Camera access failed", e)
        onError(CameraError.CAMERA_IN_USE)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Invalid camera configuration", e)
        onError(CameraError.BINDING_FAILED)
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected camera error", e)
        onError(CameraError.UNKNOWN)
    }
}
```

***REMOVED******REMOVED******REMOVED*** 4. Error UI Components

```kotlin
@Composable
fun CameraErrorScreen(
    error: CameraState,
    onRetry: () -> Unit,
    onViewItems: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error.title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (error.canRetry) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(onClick = onViewItems) {
            Text("View Items")
        }
    }
}
```

***REMOVED******REMOVED******REMOVED*** 5. Runtime Camera Check

```kotlin
fun hasCamera(context: Context): Boolean {
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}

// Usage in CameraScreen
LaunchedEffect(Unit) {
    if (!hasCamera(context)) {
        cameraState = CameraState.ERROR_NO_CAMERA_HARDWARE
    }
}
```

***REMOVED******REMOVED*** Testing

Manual test scenarios:

1. **Permission Denied**:
   - Deny camera permission
   - Verify error screen shows with "Grant Permission" option
   - Grant permission from error screen
   - Verify camera starts working

2. **Camera In Use**:
   - Open another camera app
   - Launch Scanium
   - Verify error message
   - Close other app, tap Retry
   - Verify camera works

3. **No Camera** (emulator without camera):
   - Launch on emulator without camera
   - Verify appropriate error message
   - Tap "View Items"
   - Verify can still see empty items list

4. **Permission Revoked at Runtime**:
   - Grant permission, start scanning
   - Via Android Settings: revoke permission
   - Return to app
   - Verify error state shown

***REMOVED******REMOVED*** Related Issues

None
