# Model Selection UX Proposal for Scanium

**Status:** Design Proposal
**Author:** UX Design
**Date:** 2026-01-31
**Target:** Edit Item Screen - Model Field

---

## Overview

**Recommended Approach: Smart Autocomplete with Progressive Disclosure**

The model field uses an **autocomplete text input** that filters on-demand as users type, avoiding overwhelming dropdowns while staying fast and keyboard-efficient. This approach leverages Scanium's camera-scan context—users have typically *seen* the model name on the device—and handles 1000+ models gracefully through search-based filtering rather than browsing.

**Key Principles:**
- **Lazy & fast**: No modal to open, just start typing
- **Scales naturally**: Autocomplete filters large lists down to ~10 suggestions
- **Optional-first**: Empty state is clear and acceptable
- **Offline-resilient**: Cached results + free-text fallback

---

## Interaction Flow

### 1. Initial State (Before Brand Selection)

**Visual:**
```
┌─────────────────────────────┐
│ Brand            [Samsung ▼]│ ← User selects brand first
├─────────────────────────────┤
│ Model                       │
│ [Select a brand first     ] │ ← Disabled, grayed out
│   ↑ Placeholder text        │
└─────────────────────────────┘
```

**Behavior:**
- Model field is **disabled** (grayed, not tappable)
- Placeholder: `"Select a brand first"`
- Clear dependency hierarchy prevents confusion

---

### 2. After Brand Selected

**Visual:**
```
┌─────────────────────────────┐
│ Brand            [Samsung ▼]│
├─────────────────────────────┤
│ Model (optional)            │
│ [Start typing...          ] │ ← Now enabled
│  e.g., Galaxy S24, Note 20  │ ← Helper examples
│  842 models available       │ ← Subtle count hint
└─────────────────────────────┘
```

**Behavior:**
- Field becomes **enabled** with smooth 150ms fade-in
- Placeholder: `"Start typing..."`
- Helper text shows examples: `"e.g., Galaxy S24, Note 20"`
- Small count badge: `"842 models available"` (grayed, non-interactive)
- **Background fetch** begins silently (cached if offline)

---

### 3. User Starts Typing

**Visual (after typing "gal"):**
```
┌─────────────────────────────┐
│ Model (optional)            │
│ [gal                    ⌛] │ ← Typing + spinner
├─────────────────────────────┤
│ ✦ Galaxy S24                │ ← Dropdown suggestions
│   Galaxy S24+               │   (top 10 matches)
│   Galaxy S23                │
│   Galaxy S23 Ultra          │
│   ...                       │
│                             │
│ Showing 10 of 67 matches    │ ← Result count
└─────────────────────────────┘
```

**Behavior:**
- **300ms debounce** before triggering search (cancels previous requests)
- **Minimum 2 characters** before showing suggestions
- Loading spinner appears in field (right side, small)
- Dropdown appears below field with:
  - **Top 10 matches** only (prevent overwhelm)
  - **Prefix matches first**, then contains, then fuzzy
  - Matching text **bolded** (e.g., "**Gal**axy S24")
  - Result count at bottom: `"Showing 10 of 67 matches"`
- Screen reader announces: `"10 suggestions available"`

**Search Algorithm Priority:**
1. **Exact prefix match** ("Galaxy" for "gal") — highest
2. **Word prefix** ("Note 20" for "not") — medium
3. **Contains** ("S24 Ultra" for "ultra") — lower
4. **Popular models** boost (if usage data available)

---

### 4. Selecting a Model

**Visual:**
```
┌─────────────────────────────┐
│ Model (optional)         ✓  │ ← Checkmark indicates selection
│ [Galaxy S24               ] │
└─────────────────────────────┘
```

**Behavior:**
- Tap suggestion **or** press Enter/Down+Enter on keyboard
- Field populates with selected model name
- Dropdown closes with subtle fade
- **Checkmark icon** appears (right side, green)
- Small "from catalog" badge (subtle, optional)
- Screen reader announces: `"Galaxy S24 selected"`

---

## Edge Cases

### Edge Case 1: Brand Changes After Model Selected

**Scenario:** User selected "Samsung Galaxy S24", then changes Brand to "Apple"

**Behavior:**
1. Model field **clears immediately**
2. Snackbar appears: `"Model cleared — brand changed"`
3. Field resets to enabled/empty state with new placeholder
4. No orphaned data confusion

**Why:** Prevent impossible states (iPhone can't be a Samsung model)

---

### Edge Case 2: Very Large Lists (1000+ models)

**Scenario:** User selected "Samsung" (842 models)

**Strategy:**
- **Never load all 842** — fetch **only top 100** initially for prefix matching
- As user types, **server-side filtering** narrows results (or client-side if cached)
- Show **top 10 suggestions** only
- Bottom hint: `"Showing 10 of 842 matches — keep typing to narrow"`
- **"Browse all" option** (see below) for exploratory users

**Performance:**
- Debounced search prevents request spam
- Cancel in-flight requests on new keystroke
- Cache recent searches (LRU, max 5 brands)

---

### Edge Case 3: Offline Mode

**Scenario:** User is offline, selects "Samsung"

**Behavior:**
1. Check **local cache** (last fetched models for Samsung)
2. If cached:
   - Show suggestions normally
   - Small badge: `"Cached (offline)"` (gray, subtle)
   - Full functionality with cached data
3. If **no cache**:
   - Field becomes **free-text**
   - Placeholder: `"No models available (offline) — enter manually"`
   - Helper: `"Will sync when online"`
   - Allow any text input

**Cache Strategy:**
```
- Cache last 10 brands (LRU eviction)
- Expire after 7 days
- ~50KB per brand (500 models × 100 bytes)
- Total: ~500KB max cache
```

---

### Edge Case 4: Unknown/Unlisted Model

**Scenario:** User has "Samsung Galaxy A03" but it's not in catalog

**Option A: Free-Text Entry**
- User types "Galaxy A03"
- No suggestions appear
- Message: `"No matches — press Enter to save custom model"`
- User presses Enter
- Field accepts text, shows info icon ⓘ
- Tooltip: `"Custom model (not in catalog)"`

**Option B: "Not Listed" Affordance**
- After typing with no results, show:
  - `"Can't find your model?"`
  - `[Save as 'Galaxy A03']` button
- Tap button → saves custom text
- Future: `"Suggest to catalog"` link (sends feedback)

**Why Allow Free-Text:**
- Catalog will never be 100% complete
- New models released constantly
- User productivity > data purity
- Can sync/validate later server-side

---

### Edge Case 5: No Models Available for Brand

**Scenario:** User selects "Huawei" but catalog has 0 models (data gap)

**Behavior:**
1. Model field enables but shows:
   - Placeholder: `"No models in catalog — enter manually"`
   - Helper: `"You can type any model name"`
2. Field accepts free-text normally
3. Small info banner (dismissible):
   - `"📝 This brand has no catalog yet — manual entry is fine"`

**Why:** Graceful degradation > blocking user

---

### Edge Case 6: Loading States

**States to Handle:**

**A. Initial Brand Selection (Fetching Models)**
```
┌─────────────────────────────┐
│ Model (optional)            │
│ [Loading models...      ⌛] │ ← Skeleton/shimmer
│   ↑ Disabled during fetch   │
└─────────────────────────────┘
```
- **150ms delay** before showing spinner (avoid flicker on cache hit)
- If fetch fails: Enable field with free-text fallback

**B. Typing Search (Debouncing)**
```
│ [gal                    ⌛] │ ← Small spinner, right side
```
- Mini spinner appears only **after 300ms** typing pause
- If results load <100ms, skip spinner (feels instant)

**C. Slow Network (>2s)**
```
│ [gal                    ⌛] │
│ Taking longer than usual... │ ← Status message
│ [Continue typing] [Cancel]  │
```
- Show status after 2s
- Allow user to keep typing (don't block)
- Cancel button clears field, hides dropdown

---

## Accessibility Considerations

### Keyboard Navigation
1. **Tab to field** → focuses input, announces state
2. **Type** → dropdown appears, announces `"10 suggestions"`
3. **Down arrow** → moves to first suggestion
4. **Up/Down** → navigates suggestions
5. **Enter** → selects highlighted suggestion
6. **Escape** → closes dropdown, returns to field

### Screen Reader Announcements
- **Disabled state:** `"Model, text field, disabled. Select a brand first."`
- **Enabled empty:** `"Model, optional text field. 842 models available. Start typing to search."`
- **Typing:** `"10 suggestions available. Use arrow keys to navigate."`
- **Selected:** `"Galaxy S24 selected from catalog."`
- **Custom:** `"Custom model entered. Will be saved as-is."`

### Focus Management
- Dropdown opens → **focus stays in text field** (not first suggestion)
  - Why: Users may keep typing to refine search
- Arrow down → focus moves to suggestions
- Selection → focus returns to field
- Clear field → announce `"Model cleared"`

### High Contrast / Large Text
- Disabled state: 0.38 alpha (Material guideline)
- Dropdown suggestions: Min 48dp touch targets
- Checkmark icon: 24dp, AA contrast ratio
- Helper text: Min 12sp, grayed but readable

---

## Optional Enhancement: "Browse All" Affordance

**When to Show:**
- After brand selected, small link below field: `"Browse all models"`
- For users who prefer scrolling vs. typing

**Interaction:**
1. Tap "Browse all" → **Bottom sheet** slides up
2. Sheet contains:
   - **Search bar** (sticky at top, same autocomplete)
   - **Alphabetical sections** (A-Z jump headers)
   - **Fast scroll** with letter preview
   - Optional: **"Popular"** section at top (if we have data)
3. Tap model → sheet dismisses, field populates

**Why Bottom Sheet for Browse:**
- More screen space for long lists
- Sectioned browsing (A-Z) feels organized
- Doesn't replace primary autocomplete flow (just supplements)

**Visual:**
```
╔═══════════════════════════════╗
║ Samsung Models           [✕]  ║ ← Header with close
╠═══════════════════════════════╣
║ [🔍 Search models...        ] ║ ← Search (same autocomplete)
╠═══════════════════════════════╣
║ ⭐ Popular                    ║ ← Optional section
║   Galaxy S24                  ║
║   Galaxy S23                  ║
╠═══════────────────────────────╣
║ A                             ║ ← Alpha sections
║   Galaxy A03                  ║
║   Galaxy A14                  ║
║ G                             ║
║   Galaxy S24                  ║
║   Galaxy S24+                 ║
║   ...                     [📜]║ ← Fast scroll handle
╚═══════════════════════════════╝
```

---

## Why This Works for Scanium

### 1. **Scales to Thousands Without Overwhelm**
- Autocomplete naturally filters 1000+ items → ~10 suggestions
- No scrolling through endless lists
- Search-driven discovery vs. browsing paralysis

### 2. **Leverages Camera-Scan Context**
- Users have **already seen** the model name on the device (via camera/OCR)
- Just type what they saw → instant match
- Typing is faster than scrolling for known targets

### 3. **Fast & Intentional**
- No modal/sheet to open (0 extra taps)
- Debounced fetch feels instant with cache
- Keyboard-first = power user friendly

### 4. **Respects Optional Nature**
- Empty state is clear: `"Optional — leave blank if unsure"`
- No pressure to fill if unknown
- Free-text fallback always available

### 5. **Offline Resilient**
- Cached models work fully offline
- Graceful degradation to free-text if no cache
- Sync verification later when online

### 6. **Fits Edit Item Workflow**
- Appears naturally in form flow (below Brand)
- Dependency is obvious (disabled until brand selected)
- Checkmark confirms selection (visual feedback)

### 7. **Future-Proof**
- Free-text captures unlisted models → improve catalog
- Telemetry on searches → prioritize popular models
- "Suggest to catalog" → crowdsourced improvements

---

## Alternative Approach (Not Recommended)

### **Bottom Sheet with Full List + Search**

**How it works:**
- Tap Model field → opens full-screen bottom sheet
- Sheet shows all models (paginated, 50 at a time)
- Search bar at top filters results
- Alphabetical sections with fast scroll

**Why worse:**
1. **Extra tap to open** → slower (modal interrupts flow)
2. **Initial render of 1000 items** → laggy, even paginated
3. **Context switch** → breaks form-filling momentum
4. **Browsing overwhelm** → users see hundreds of similar names
5. **Doesn't leverage OCR** → assumes discovery vs. confirmation
6. **Accessibility** → focus juggling between sheet/form

**When it might work:**
- If users genuinely don't know model names (unlikely for Scanium)
- If browsing is primary use case (not here—typing is faster)

---

## Copy Examples

### Field Labels
- Primary label: `"Model (optional)"`
- Helper text: `"Start typing to search 842 models"`

### Placeholders
- Before brand: `"Select a brand first"`
- After brand: `"e.g., Galaxy S24, iPhone 15 Pro"`
- Offline no cache: `"Enter model manually (offline)"`

### Dropdown States
- Searching: `"Searching..."`
- Results: `"Showing 10 of 67 matches"`
- No results: `"No matches for 'xyz' — try different keywords"`
- Empty query: (don't show dropdown until 2+ chars)

### Success States
- Selected: ✓ `"Galaxy S24"` (green check icon)
- Custom entry: ⓘ `"Custom model"` (info icon, gray)

### Error States
- Network error: `"Can't load models — try again or enter manually"`
- No catalog: `"No models available for this brand — manual entry OK"`

### Empty State
- `"Leave blank if unsure — you can add it later"`

---

## Technical Implementation Checklist

### Caching
- [ ] LRU cache, 10 brands max (~500KB total)
- [ ] Expire after 7 days
- [ ] Fetch on brand select (lazy), cache response
- [ ] Fallback to free-text if cache miss + offline

### Search
- [ ] Debounce 300ms (use `kotlin-flow` with `debounce` operator)
- [ ] Min 2 characters before triggering
- [ ] Cancel in-flight requests on new input (use `Flow.collectLatest`)
- [ ] Client-side filter if cached, server-side if live

### Filtering Algorithm
```kotlin
// Pseudo-code
fun filterModels(query: String, models: List<Model>): List<Model> {
  return models
    .filter { it.label.contains(query, ignoreCase = true) }
    .sortedBy {
      when {
        it.label.startsWith(query, ignoreCase = true) -> 0 // Prefix
        it.label.split(" ").any { word ->
          word.startsWith(query, ignoreCase = true)
        } -> 1 // Word prefix
        else -> 2 // Contains
      }
    }
    .take(10) // Limit to 10 suggestions
}
```

### State Management
- [ ] `modelFieldEnabled: Boolean` (derived from `brand != null`)
- [ ] `modelSuggestions: List<Model>` (empty, loading, data, error)
- [ ] `selectedModel: Model?` (null if custom text)
- [ ] `isCustomModel: Boolean` (true if user typed non-catalog value)

### Validation
- [ ] Accept any text (don't enforce catalog)
- [ ] Tag as `verified: true` if from catalog, `false` if custom
- [ ] Send custom models to backend for future catalog enrichment

---

## Summary

The **smart autocomplete** approach balances speed, scale, and simplicity:
- **No overwhelming lists** (filtered to 10 suggestions)
- **No extra taps** (inline, not modal)
- **Leverages context** (users know the model from camera scan)
- **Offline works** (cached + free-text fallback)
- **Optional-first** (empty is OK)

This design respects Scanium's camera-centric workflow where users are cataloging physical items they're holding—typing the model they just saw is faster and more natural than browsing 1000 options.

---

## Related Documentation

- Backend API: See `/Users/family/dev/scanium/backend/src/modules/catalog/` for implementation
- API endpoints:
  - `GET /v1/catalog/:subtype/brands` - List brands for a subtype
  - `GET /v1/catalog/:subtype/models?brand=X` - List models for subtype + brand
- See `catalog-api-summary.md` in scratchpad for full API documentation
