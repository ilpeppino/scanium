# Flavor Gating

This document describes the product flavor behavior for Scanium's `dev`, `beta`, and `prod` builds.

## Overview

Scanium uses Android product flavors to control feature availability and UI visibility across different build variants. The centralized feature flag system ensures consistent behavior without scattered BuildConfig checks.

## Flavor Summary

| Feature | dev | beta | prod |
|---------|-----|------|------|
| Developer Mode | Enabled (toggleable) | Disabled | Disabled |
| Screenshots | Enabled (toggleable) | Disabled (FLAG_SECURE) | Disabled (FLAG_SECURE) |
| AI Assistant | Enabled | Hidden | Hidden |
| Image Resolution | Low/Normal/High | Low/Normal | Low/Normal |
| Item Diagnostics | Shown | Hidden | Hidden |

## Feature Details

### Developer Mode
- **dev**: Users can enable Developer Options in Settings. When enabled, shows Developer Options menu with debug toggles.
- **beta/prod**: Developer Options completely hidden. No entry point in Settings. Deep links to developer screen navigate back immediately.

### Screenshots
- **dev**: Screenshot toggle available in Developer Options. FLAG_SECURE applied when disabled.
- **beta/prod**: FLAG_SECURE always applied. No toggle exposed. User preference is ignored and clamped to false.

### AI Assistant
- **dev**: Full access to AI Assistant. Navigation to assistant screen works normally.
- **beta/prod**: Assistant is completely hidden:
  - No icons or menu entries visible
  - No navigation routes accessible
  - Deep links or stale state navigates back immediately

### Image Resolution
- **dev**: All resolution options available (Low/Normal/High).
- **beta/prod**: HIGH option hidden from UI. Persisted HIGH value from dev build is clamped to NORMAL at runtime.

### Item Diagnostics
- **dev**: Item list shows diagnostic labels:
  - Aggregation accuracy badge (Low/Medium/High confidence)
  - Cloud/on-device classification indicator
  - Confidence percentage in timestamp row
- **beta/prod**: Diagnostic labels hidden. Item list shows only:
  - Item image
  - Title/category
  - Price and condition
  - Attributes

## Architecture

### Central Feature Flags

All flavor-specific behavior is driven by `FeatureFlags.kt`:

```kotlin
object FeatureFlags {
    val allowDeveloperMode: Boolean      // FEATURE_DEV_MODE
    val allowScreenshots: Boolean        // FEATURE_SCREENSHOTS
    val allowAiAssistant: Boolean        // FEATURE_AI_ASSISTANT
    val maxImageResolution: String       // MAX_IMAGE_RESOLUTION
    val showItemDiagnostics: Boolean     // FEATURE_ITEM_DIAGNOSTICS
}
```

### BuildConfig Fields

Gradle flavors define these BuildConfig fields:

```kotlin
// DEV flavor
buildConfigField("boolean", "FEATURE_DEV_MODE", "true")
buildConfigField("boolean", "FEATURE_SCREENSHOTS", "true")
buildConfigField("boolean", "FEATURE_AI_ASSISTANT", "true")
buildConfigField("String", "MAX_IMAGE_RESOLUTION", "\"HIGH\"")
buildConfigField("boolean", "FEATURE_ITEM_DIAGNOSTICS", "true")

// BETA and PROD flavors
buildConfigField("boolean", "FEATURE_DEV_MODE", "false")
buildConfigField("boolean", "FEATURE_SCREENSHOTS", "false")
buildConfigField("boolean", "FEATURE_AI_ASSISTANT", "false")
buildConfigField("String", "MAX_IMAGE_RESOLUTION", "\"NORMAL\"")
buildConfigField("boolean", "FEATURE_ITEM_DIAGNOSTICS", "false")
```

### Runtime Enforcement

1. **UI Gating**: Composables check `FeatureFlags` before rendering gated elements
2. **Navigation Guards**: Routes check flags and navigate back if unauthorized
3. **Settings Clamping**: SettingsRepository clamps values at read time
4. **Resolution Clamping**: CameraViewModel clamps HIGH to NORMAL

### Migration Safety

When a dev build is replaced by beta/prod, settings that would enable restricted features are clamped at runtime:
- `developerModeFlow` returns false regardless of stored value
- `devAllowScreenshotsFlow` returns false regardless of stored value
- Capture resolution HIGH is clamped to NORMAL

No crashes or stale UI will occur.

## Files Modified

- `androidApp/build.gradle.kts` - Flavor BuildConfig fields
- `androidApp/src/main/java/com/scanium/app/config/FeatureFlags.kt` - Central flags
- `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt` - Item diagnostics gating
- `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt` - Route guards
- `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsHomeScreen.kt` - Developer menu visibility
- `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsCameraScreen.kt` - Resolution options
- `androidApp/src/main/java/com/scanium/app/camera/CameraViewModel.kt` - Resolution clamping
- `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` - Settings clamping

## Testing

### Manual Verification

**DEV build:**
1. Item list shows accuracy badges (Low/Medium/High)
2. Item list shows Cloud/Classifying.../Failed labels
3. Resolution selector shows High option
4. Developer menu visible in Settings
5. Screenshots can be toggled

**BETA/PROD build:**
1. Item list has NO accuracy badges
2. Item list has NO classification indicators
3. Resolution selector shows only Low/Normal
4. No developer menu in Settings
5. Screenshots always blocked

### Unit Tests

See test files:
- `FeatureFlagsTest.kt` - Flag value verification
- `ItemsListScreenTest.kt` - Diagnostic visibility tests
