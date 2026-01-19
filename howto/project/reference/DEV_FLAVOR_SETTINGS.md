***REMOVED*** DEV Flavor Settings

This document describes DEV-flavor-specific settings and UI changes.

***REMOVED******REMOVED*** Developer Mode (Forced ON)

In DEV builds, Developer Mode is **always enabled** and cannot be disabled by the user.

***REMOVED******REMOVED******REMOVED*** Behavior

| Flavor | Developer Mode | Toggle in UI | `developerModeFlow` | `setDeveloperMode()` |
|--------|----------------|--------------|---------------------|----------------------|
| DEV    | Always ON      | Not shown    | Always `true`       | No-op (ignored)      |
| BETA   | Always OFF     | Not shown    | Always `false`      | No-op (ignored)      |
| PROD   | Always OFF     | Not shown    | Always `false`      | No-op (ignored)      |

***REMOVED******REMOVED******REMOVED*** Why?

1. **Consistency**: Developers always have access to debug features without having to remember to
   enable them
2. **Determinism**: DEV builds behave identically for all developers
3. **Simplicity**: One less toggle to manage during development

***REMOVED******REMOVED******REMOVED*** UI Changes

In the Developer Options screen:

- **Before**: A toggle switch for "Developer Mode" with subtitle "Unlock all features for testing"
- **After**: A static row showing "Developer Mode" with subtitle "Always enabled in DEV builds" (
  highlighted in primary color)

***REMOVED******REMOVED******REMOVED*** Implementation

See `SettingsRepository.kt`:

```kotlin
val developerModeFlow: Flow<Boolean> =
    context.settingsDataStore.data.map { preferences ->
        when {
            // DEV flavor: Developer mode is always ON
            FeatureFlags.isDevBuild -> true
            // BETA/PROD: Developer mode is completely disabled
            !FeatureFlags.allowDeveloperMode -> false
            // Fallback: use stored preference
            else -> preferences[DEVELOPER_MODE_KEY] ?: false
        }
    }

suspend fun setDeveloperMode(enabled: Boolean) {
    // DEV flavor: Developer mode is forced ON, ignore setter
    if (FeatureFlags.isDevBuild) return
    // BETA/PROD: Developer mode is not allowed, ignore setter
    if (!FeatureFlags.allowDeveloperMode) return
    // Fallback: persist the value
    context.settingsDataStore.edit { preferences ->
        preferences[DEVELOPER_MODE_KEY] = enabled
    }
}
```

***REMOVED******REMOVED*** Diagnostics & Checks Description

In DEV builds, an informational card is shown at the top of the Developer Options screen.

***REMOVED******REMOVED******REMOVED*** Purpose

Helps developers understand what the diagnostics sections (System Health, Assistant Diagnostics,
Preflight Health Check, Background Health Monitor) are for.

***REMOVED******REMOVED******REMOVED*** Text

> **Diagnostics & Checks**
>
> Diagnostics & checks help verify connectivity to your backend services (health, config, preflight,
> assistant) and alert you when something breaks. Use them while testing to quickly spot disruptions.

***REMOVED******REMOVED******REMOVED*** UI

- Shows as a rounded card with primary container background
- Info icon on the left
- Title in bold, description in secondary text
- Only visible in DEV builds (`FeatureFlags.isDevBuild == true`)

***REMOVED******REMOVED******REMOVED*** Implementation

See `DeveloperOptionsScreen.kt`:

```kotlin
@Composable
private fun DiagnosticsDescriptionCard() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        ...
    ) {
        // Info icon + Title + Description
    }
}
```

***REMOVED******REMOVED*** Testing

***REMOVED******REMOVED******REMOVED*** Unit Tests

See `DeveloperModeSettingsTest.kt` for comprehensive tests covering:

- DEV flavor always returns `true` for developer mode
- DEV flavor setter is a no-op
- BETA/PROD always returns `false`
- Truth table for all combinations

***REMOVED******REMOVED******REMOVED*** Manual Verification

**DEV build:**

1. Open Settings → Developer Options
2. Verify "Developer Mode" shows "Always enabled in DEV builds" (no toggle)
3. Verify "Diagnostics & Checks" description card is visible at the top

**BETA/PROD build:**

1. Verify Developer Options is not accessible (no entry in Settings)

***REMOVED******REMOVED*** Related Documentation

- [FLAVOR_GATING.md](../FLAVOR_GATING.md) - Overall flavor feature gating
- [DEV_HEALTH_MONITOR.md](DEV_HEALTH_MONITOR.md) - Background health monitoring
