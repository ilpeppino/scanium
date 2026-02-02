# Developer Options Integration Example

This document shows how to add a "Save Classifier Debug Images" toggle to Developer Options (optional enhancement).

## Quick Toggle Implementation

### Option 1: Direct Toggle (Simplest)

Add this to `SettingsRepository.kt`:

```kotlin
// In SettingsRepository.kt
val saveClassifierDebugImagesFlow: Flow<Boolean> =
    context.dataStore.data.map { preferences ->
        preferences[Keys.SAVE_CLASSIFIER_DEBUG_IMAGES] ?: false
    }

suspend fun setSaveClassifierDebugImages(enabled: Boolean) {
    context.dataStore.edit { preferences ->
        preferences[Keys.SAVE_CLASSIFIER_DEBUG_IMAGES] = enabled
    }
}

// Add to Keys object:
object Keys {
    // ... existing keys
    val SAVE_CLASSIFIER_DEBUG_IMAGES = booleanPreferencesKey("save_classifier_debug_images")
}
```

Add toggle to Developer Options screen:

```kotlin
// In DeveloperOptionsScreen.kt
val saveClassifierDebugImages by settingsRepository.saveClassifierDebugImagesFlow.collectAsState(initial = false)

// Add this switch:
SwitchPreference(
    title = "Save Classifier Debug Images",
    subtitle = "Save ML Kit, Cloud Vision, and Local Vision input images to filesDir/debug",
    checked = saveClassifierDebugImages,
    onCheckedChange = { enabled ->
        scope.launch {
            settingsRepository.setSaveClassifierDebugImages(enabled)
            ImageClassifierDebugger.SAVE_DEBUG_IMAGES = enabled
        }
    }
)
```

Initialize on app startup in `CameraScreen.kt` or Application:

```kotlin
// In CameraScreen.kt or ScaniumApplication.kt
LaunchedEffect(Unit) {
    settingsRepository.saveClassifierDebugImagesFlow.collect { enabled ->
        ImageClassifierDebugger.SAVE_DEBUG_IMAGES = enabled
    }
}
```

### Option 2: Runtime Toggle via ADB

Without any UI changes, you can toggle it via ADB:

```bash
# Check current directory
adb shell run-as com.scanium.app.dev ls /data/data/com.scanium.app.dev/files/debug/

# The feature is always logging to logcat (can't disable)
# Just enable/disable disk saving in code or debugger
```

## Current Status

**No Developer Options integration yet** - this is optional. The instrumentation is always active in dev builds and logs to logcat. Disk saving must be enabled programmatically:

```kotlin
ImageClassifierDebugger.SAVE_DEBUG_IMAGES = true
```

## Recommendation

For now, use **Option 2** (runtime toggle via code/debugger) since:
1. This is a temporary debugging feature
2. Only needed during development/investigation
3. No UI clutter in Developer Options
4. Easy to enable when needed:
   ```kotlin
   // In Android Studio debugger:
   expr ImageClassifierDebugger.SAVE_DEBUG_IMAGES = true
   ```

If this becomes a frequently-used feature, implement **Option 1** to add a persistent toggle.
