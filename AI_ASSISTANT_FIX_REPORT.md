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

The `collectAsState()` uses `initial = false`, meaning on first composition (before the Flow emits), `aiAssistantEnabled` is `false`. When user clicks the AI button:

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
**Same as ISSUE 1.** The first click appeared to do nothing because `aiAssistantEnabled` was `false` initially. However, the generation still started in the background. When user clicked again:

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
**Files:**
- `androidApp/src/main/java/com/scanium/app/items/edit/ExportAssistantViewModel.kt:266-267`
- `androidApp/src/main/java/com/scanium/app/selling/assistant/AssistantViewModel.kt:603`

The ViewModels were using the old assistant-specific language setting:

```kotlin
val assistantPrefs = settingsRepository.assistantPrefsFlow.first()
val languageTag = assistantPrefs.language ?: "en"
```

But users set language in General settings, which updates `effectiveAiOutputLanguageFlow` (unified settings). The assistant was reading from a different, unset setting.

### Fix
Changed to use unified settings:

```kotlin
// ISSUE-3 FIX: Use unified settings for language and country
val languageTag = settingsRepository.effectiveAiOutputLanguageFlow.first()
val pricingCountryCode = settingsRepository.effectiveMarketplaceCountryFlow.first()

// Get other assistant preferences (tone, verbosity, units) and override language
val basePrefs = settingsRepository.assistantPrefsFlow.first()
val assistantPrefs = basePrefs.copy(language = languageTag)
```

### Manual Verification
1. Go to Settings > General
2. Set Language to Italian, Country to Italy
3. Return to Items list
4. Select an item, navigate to Edit
5. Click AI button
6. **Expected:** Generated listing is in Italian

---

## Files Changed

| File | Changes |
|------|---------|
| `EditItemScreenV3.kt:87-90` | Changed `collectAsState(initial = false)` to `initial = true` |
| `ExportAssistantViewModel.kt:265-272` | Use `effectiveAiOutputLanguageFlow` and `effectiveMarketplaceCountryFlow` |
| `AssistantViewModel.kt:602-605` | Use `effectiveAiOutputLanguageFlow` |

---

## Commits

1. **fix(ai): trigger assistant on first click (#ISSUE-1 #ISSUE-2)**
   - Changes initial value of `allowAssistantFlow.collectAsState()` from `false` to `true`
   - Fixes both double-click and navigation issues (shared root cause)

2. **fix(ai): propagate unified language setting to assistant (#ISSUE-3)**
   - Uses `effectiveAiOutputLanguageFlow` for AI output language
   - Uses `effectiveMarketplaceCountryFlow` for pricing country
   - Overrides language in `assistantPrefs` before sending requests

---

## Test Results

- `./gradlew test` - **PASSED**
- `./gradlew :androidApp:assembleDevDebug` - **PASSED**

---

## Risk Assessment

| Fix | Risk Level | Notes |
|-----|------------|-------|
| ISSUE 1/2 | Low | Simple initial value change; defense-in-depth in ViewModel handles edge cases |
| ISSUE 3 | Low | Uses existing unified settings infrastructure; no new code paths |

---

## Git Log

```
7ad61a0 fix(ai): propagate unified language setting to assistant (#ISSUE-3)
7aaeda0 fix(ai): trigger assistant on first click (#ISSUE-1 #ISSUE-2)
```
