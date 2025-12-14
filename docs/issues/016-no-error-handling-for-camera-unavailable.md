# Missing Error Handling for Camera Unavailable Scenarios

**Labels:** `bug`, `priority:p2`, `area:camera`, `error-handling`
**Type:** Error Handling Gap
**Severity:** Medium

## Problem

The app doesn't gracefully handle scenarios where the camera is unavailable:
- Camera in use by another app
- Camera permission revoked at runtime
- Device has no camera (tablets, emulators)
- CameraX binding fails

Users see blank screen or app crashes without explanation.

## Scenarios Not Handled

### 1. Camera Binding Failure

**CameraXManager.kt** line ~152:

```kotlin
} catch (e: Exception) {
    e.printStackTrace()  // ❌ Only prints stack trace
    // No user feedback!
}
```

User sees blank screen, no explanation.

### 2. Permission Denied

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

### 3. Camera In Use

No check for camera availability before binding.

### 4. No Camera Hardware

Manifest says `android:required="false"` but no runtime check.

## Impact

**User Experience**:
- Confusion when camera doesn't appear
- No actionable error message
- Can't recover without force-closing app

**Crash Risk**: Unhandled exceptions could crash app

## Expected Behavior

### Permission Denied:

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

### Camera Unavailable:

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

### No Camera Hardware:

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

## Acceptance Criteria

- [x] Handle permission denied state with UI prompt
- [x] Handle camera binding failures with user-friendly message
- [x] Detect if device has camera hardware
- [x] Provide "Retry" and "View Items" actions on errors
- [x] Add error state to CameraState enum
- [x] Show toast or dialog for transient errors
- [x] Log errors for debugging but don't expose technical details to users

## Suggested Implementation

### 1. Enhanced CameraState

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

### 2. Permission Denied UI

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

### 3. Camera Binding Error Handling

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

### 4. Error UI Components

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

### 5. Runtime Camera Check

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

## Testing

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

## Related Issues

None

## Resolution

- Added a `CameraState.ERROR` branch with a dedicated `CameraErrorState` UI so users see actionable messaging instead of a blank preview when binding fails or the device has no camera hardware. The UI now offers retry and navigation to the items list.
- `CameraXManager` now checks for camera hardware and empty providers, and returns descriptive failures when the camera is unavailable.
- `CameraScreen` stops scanning when permission is lost, surfaces binding failures as retryable errors, and allows rebind attempts without restarting the app. Toasts remain minimal while logs keep stack traces for debugging.

### Verification

- Launch without a camera (emulator): verify "Camera unavailable" card with Retry/View Items.
- Deny permission: permission-required UI displayed; granting permission re-attempts binding.
- Start with another app using the camera: expect binding failure, then tap Retry after freeing the camera.
