***REMOVED*** Flavor Gating

This document describes the product flavor behavior for Scanium's `dev`, `beta`, and `prod` builds.

***REMOVED******REMOVED*** Overview

Scanium uses Android product flavors to control feature availability and UI visibility across
different build variants. The centralized feature flag system ensures consistent behavior without
scattered BuildConfig checks.

***REMOVED******REMOVED*** Flavor Summary

| Feature                 | dev                  | beta                   | prod                   |
|-------------------------|----------------------|------------------------|------------------------|
| Developer Mode          | Always ON (forced)   | Disabled               | Disabled               |
| Screenshots             | Enabled (toggleable) | Disabled (FLAG_SECURE) | Disabled (FLAG_SECURE) |
| AI Assistant            | Enabled              | Hidden                 | Hidden                 |
| Image Resolution        | Low/Normal/High      | Low/Normal             | Low/Normal             |
| Item Diagnostics        | Shown                | Hidden                 | Hidden                 |
| Diagnostics Description | Shown                | Hidden                 | Hidden                 |

***REMOVED******REMOVED*** Feature Details

***REMOVED******REMOVED******REMOVED*** Developer Mode

- **dev**: Developer Mode is **always ON** and cannot be disabled. The toggle is removed from the
  Developer Options screen - replaced with a static indicator showing "Always enabled in DEV
  builds". This ensures developers always have access to debug features without having to remember
  to enable them.
- **beta/prod**: Developer Options completely hidden. No entry point in Settings. Deep links to
  developer screen navigate back immediately.

***REMOVED******REMOVED******REMOVED*** Screenshots

- **dev**: Screenshot toggle available in Developer Options. FLAG_SECURE applied when disabled.
- **beta/prod**: FLAG_SECURE always applied. No toggle exposed. User preference is ignored and
  clamped to false.

***REMOVED******REMOVED******REMOVED*** AI Assistant

- **dev**: Full access to AI Assistant. Navigation to assistant screen works normally.
- **beta/prod**: Assistant is completely hidden:
    - No icons or menu entries visible
    - No navigation routes accessible
    - Deep links or stale state navigates back immediately

***REMOVED******REMOVED******REMOVED*** Image Resolution

- **dev**: All resolution options available (Low/Normal/High).
- **beta/prod**: HIGH option hidden from UI. Persisted HIGH value from dev build is clamped to
  NORMAL at runtime.

***REMOVED******REMOVED******REMOVED*** Item Diagnostics

- **dev**: Item list shows diagnostic labels:
    - Aggregation accuracy badge (Low/Medium/High confidence)
    - Cloud/on-device classification indicator
    - Confidence percentage in timestamp row
- **beta/prod**: Diagnostic labels hidden. Item list shows only:
    - Item image
    - Title/category
    - Price and condition
    - Attributes

***REMOVED******REMOVED******REMOVED*** Diagnostics & Checks Description

- **dev**: Shows an informational card at the top of the Developer Options screen explaining the
  purpose of diagnostics sections: "Diagnostics & checks help verify connectivity to your backend
  services (health, config, preflight, assistant) and alert you when something breaks. Use them
  while testing to quickly spot disruptions."
- **beta/prod**: Not shown (Developer Options screen is not accessible).

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** Central Feature Flags

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

***REMOVED******REMOVED******REMOVED*** BuildConfig Fields

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

***REMOVED******REMOVED******REMOVED*** Runtime Enforcement

1. **UI Gating**: Composables check `FeatureFlags` before rendering gated elements
2. **Navigation Guards**: Routes check flags and navigate back if unauthorized
3. **Settings Clamping**: SettingsRepository clamps values at read time
4. **Resolution Clamping**: CameraViewModel clamps HIGH to NORMAL
5. **Developer Mode Forcing**: DEV builds always return `true` for `developerModeFlow`, BETA/PROD
   always return `false`

***REMOVED******REMOVED******REMOVED*** Migration Safety

When a dev build is replaced by beta/prod, settings that would enable restricted features are
clamped at runtime:

- `developerModeFlow` returns false regardless of stored value
- `devAllowScreenshotsFlow` returns false regardless of stored value
- Capture resolution HIGH is clamped to NORMAL

When a beta/prod build is replaced by dev:

- `developerModeFlow` returns true regardless of stored value (forced ON)
- `setDeveloperMode()` is a no-op (cannot be disabled by user)

No crashes or stale UI will occur.

***REMOVED******REMOVED*** Files Modified

- `androidApp/build.gradle.kts` - Flavor BuildConfig fields
- `androidApp/src/main/java/com/scanium/app/config/FeatureFlags.kt` - Central flags
- `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt` - Item diagnostics gating
- `androidApp/src/main/java/com/scanium/app/navigation/NavGraph.kt` - Route guards
- `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsHomeScreen.kt` - Developer menu
  visibility
- `androidApp/src/main/java/com/scanium/app/ui/settings/SettingsCameraScreen.kt` - Resolution
  options
- `androidApp/src/main/java/com/scanium/app/camera/CameraViewModel.kt` - Resolution clamping
- `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` - Settings clamping

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Manual Verification

**DEV build:**

1. Item list shows accuracy badges (Low/Medium/High)
2. Item list shows Cloud/Classifying.../Failed labels
3. Resolution selector shows High option
4. Developer menu visible in Settings
5. Screenshots can be toggled
6. Developer Mode shows "Always enabled in DEV builds" (no toggle)
7. Diagnostics & Checks description card visible at top of Developer Options

**BETA/PROD build:**

1. Item list has NO accuracy badges
2. Item list has NO classification indicators
3. Resolution selector shows only Low/Normal
4. No developer menu in Settings
5. Screenshots always blocked

***REMOVED******REMOVED******REMOVED*** Unit Tests

See test files:

- `FeatureFlagsTest.kt` - Flag value verification
- `DeveloperModeSettingsTest.kt` - Developer mode behavior per flavor
- `ItemsListScreenTest.kt` - Diagnostic visibility tests
