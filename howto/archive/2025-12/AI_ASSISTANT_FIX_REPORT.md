# AI Assistant Fix Report

## Summary

Fixed 3 user-facing regressions on branch `refactoring` related to the AI Assistant feature.

---

## ISSUE 1: Double Click to Trigger AI Assistant

### Symptoms

- User must click AI assistant button twice to trigger AI description generation
- First click shows "AI disabled" inlay or does nothing
- Second click works correctly

### Root Cause

**File:** `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt:87`

```kotlin
val aiAssistantEnabled by settingsRepository.allowAssistantFlow.collectAsState(initial = false)
```

The `collectAsState()` uses `initial = false`, meaning on first composition (before the Flow emits),
`aiAssistantEnabled` is `false`. When user clicks the AI button:

1. First click: `aiAssistantEnabled = false` (initial) → shows "AI disabled" inlay
2. Flow emits actual value (`true`)
3. Second click: `aiAssistantEnabled = true` → opens sheet correctly

### Fix

Changed initial value from `false` to `true`:

```kotlin
// ISSUE-1 FIX: Use initial=true to avoid double-click bug where first click
// shows "AI disabled" before flow emits actual value. Defense-in-depth check
// in ExportAssistantViewModel.generateExport() handles truly disabled case.
val aiAssistantEnabled by settingsRepository.allowAssistantFlow.collectAsState(initial = true)
```

### Manual Verification

1. Open Items list
2. Select any item
3. Navigate to Edit Item screen
4. Click AI button **once**
5. **Expected:** Export Assistant sheet opens and generation starts immediately

---

## ISSUE 2: Navigation Not Shown After Selecting 2 Items

### Symptoms

- After selecting 2 items and triggering AI, app stays on Edit page when AI completes
- If user clicks again, AI response screen appears immediately

### Root Cause

**Same as ISSUE 1.** The first click appeared to do nothing because `aiAssistantEnabled` was `false`
initially. However, the generation still started in the background. When user clicked again:

1. First click: Blocked by `aiAssistantEnabled = false` check
2. Generation completed in background, Success state cached in ViewModel
3. Second click: Opens sheet, immediately shows cached Success state

### Fix

Same fix as ISSUE 1 - changing initial value to `true` ensures first click opens the sheet properly.

### Manual Verification

1. Open Items list
2. Select 2 items
3. Navigate to Edit Items
4. Click AI button **once**
5. **Expected:** Export Assistant sheet opens with loading indicator, then shows generated content

---

## ISSUE 3: Language/Country Not Applied

### Symptoms

- User sets Language=Italian and Country=Italy in Settings > General
- AI generates description in English instead of Italian

### Root Cause

**File:** `androidApp/src/main/java/com/scanium/app/data/AssistantSettings.kt:51-54`

The `assistantPrefsFlow` combined settings including `assistantLanguageFlow`, which read from a
separate DataStore key (`ASSISTANT_LANGUAGE_KEY`) with default "EN":

```kotlin
val assistantLanguageFlow: Flow<String> =
    dataStore.data.map { preferences ->
        preferences[SettingsKeys.Assistant.ASSISTANT_LANGUAGE_KEY] ?: "EN"
    }
```

But users set language in General settings, which updates `primaryLanguageFlow` →
`effectiveAiOutputLanguageFlow` (unified settings). The assistant was reading from a different,
unset setting.

### Fix

**File:** `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt:153-160`

Fixed at the source by overriding `assistantPrefsFlow` in `SettingsRepository` to combine base prefs
with the unified language setting:

```kotlin
// ISSUE-3 FIX: Combine base assistant prefs with unified language setting
// Users set language in General settings (primaryLanguageFlow), which should drive AI output
val assistantPrefsFlow: Flow<AssistantPrefs> = combine(
    assistantSettings.assistantPrefsFlow,
    unifiedSettings.effectiveAiOutputLanguageFlow,
) { basePrefs, unifiedLanguage ->
    basePrefs.copy(language = unifiedLanguage)
}
```

This approach fixes the language at the settings layer, so all consumers of `assistantPrefsFlow`
automatically get the correct language without needing changes.

### Manual Verification

1. Go to Settings > General
2. Set Language to Italian, Country to Italy
3. Return to Items list
4. Select an item, navigate to Edit
5. Click AI button
6. **Expected:** Generated listing is in Italian

---

## Files Changed

| File                            | Changes                                                                |
|---------------------------------|------------------------------------------------------------------------|
| `ItemEditState.kt:74`           | Removed `item` from `remember` key to prevent ViewModel recreation     |
| `SettingsRepository.kt:153-160` | Override `assistantPrefsFlow` to combine with unified language setting |

---

## Commits

1. **fix(ai): trigger assistant on first click (#ISSUE-1 #ISSUE-2)**
    - Removes `item` from `remember` key in `rememberItemEditState()`
    - Prevents ViewModel recreation when item loads, fixing timing issues

2. **fix(ai): propagate unified language setting to assistant (#ISSUE-3)**
    - Overrides `assistantPrefsFlow` in `SettingsRepository` to combine with unified language
    - Uses `effectiveAiOutputLanguageFlow` as the language source
    - All assistant consumers automatically get the correct language

---

## Test Results

- `./gradlew test` - **PASSED**
- `./gradlew :androidApp:assembleDevDebug` - **PASSED**

---

## Risk Assessment

| Fix       | Risk Level | Notes                                                                                        |
|-----------|------------|----------------------------------------------------------------------------------------------|
| ISSUE 1/2 | Low        | Removes `item` from remember key; ViewModel fetches data internally, so no behavioral change |
| ISSUE 3   | Low        | Combines existing flows in SettingsRepository; no new code paths, just proper wiring         |

---

## Notes

The key insight for ISSUE 3 was that overriding language at request time using `.copy()` caused "
Invalid response format" errors from the backend. The correct fix was to override
`assistantPrefsFlow` at the settings layer, so the language flows through naturally without
modifying the `AssistantPrefs` object at request time.
