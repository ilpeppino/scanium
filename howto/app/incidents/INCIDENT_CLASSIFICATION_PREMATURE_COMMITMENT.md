# Incident: Premature Classification Commitment & Silent Overrides

**Severity**: Critical (UX trust + data integrity)
**Date identified**: 2026-01-27
**Status**: Plan complete, awaiting implementation

---

## 1. Incident Description

### User-Visible Problem

After capturing a photo in Scanium, three distinct failures occur in sequence:

1. **No processing feedback** — the user sees nothing after shutter tap until the hypothesis sheet appears. There is no spinner, progress bar, or "Classifying..." message during the `classifyMultiHypothesis()` network call.

2. **Hypothesis sheet shows with "Unknown" classification** — the sheet renders before classification completes or when the backend returns empty/low-confidence results. The user can confirm an item with category `Unknown` or an empty label. Nothing in the current code gates the sheet on valid classification data.

3. **Silent post-confirmation overwrite** — after the user confirms a hypothesis and the item is created, the async enrichment pipeline (Layers A/B/C in `VisionInsightsPrefiller`) continues running. When Layer B (cloud vision) or Layer C (full enrichment) completes seconds later, `applyVisionInsights()` overwrites the item's `category`, `labelText`, and `attributes` — **without user awareness or consent**.

### Why This Is Critical

- **Trust violation**: the core product promise is "no silent AI decisions." A category that changes after user confirmation directly contradicts this.
- **Data integrity**: the user believes they confirmed "T-Shirt / Fashion" but the item silently becomes something else when enrichment returns.
- **Export corruption**: items exported to CSV/marketplace may carry categories the user never saw or approved.

---

## 2. Expected UX Contract (Authoritative)

The following rules are **non-negotiable** for any classification flow:

### 2.1 Immediate Feedback

| Rule | Detail |
|------|--------|
| F-1 | Within 200ms of shutter tap, a visible processing indicator MUST appear (spinner or skeleton card). |
| F-2 | The indicator MUST persist until classification resolves OR times out. |
| F-3 | If classification takes >5s, show "Still processing..." secondary text. |

### 2.2 Hypothesis Sheet Gating

| Rule | Detail |
|------|--------|
| G-1 | The hypothesis sheet MUST NOT appear unless at least one hypothesis has a non-empty `categoryName` AND `confidence > 0`. |
| G-2 | "Unknown", empty string, or null category MUST NOT be presented as a confirmable hypothesis. |
| G-3 | If all hypotheses are invalid, show a "Could not identify — enter manually" fallback instead of the hypothesis sheet. |
| G-4 | The hypothesis sheet MUST only appear once per detection. Dismissal = manual entry path. |

### 2.3 Post-Confirmation Immutability

| Rule | Detail |
|------|--------|
| I-1 | Once the user confirms a hypothesis (taps a card), the item's `category` and `labelText` are **locked against async overwrites**. |
| I-2 | Enrichment layers (A/B/C) MAY update `attributes`, `visionAttributes`, and `enrichmentStatus` — but MUST NOT change `category` or `labelText`. |
| I-3 | If enrichment produces a significantly different classification, it MUST be surfaced as a **refinement suggestion** (non-blocking chip/banner), never applied silently. |
| I-4 | Only explicit user action (tap on refinement suggestion, or manual edit) may change `category` or `labelText` after confirmation. |

### 2.4 Low-Confidence / Uncertain Path

| Rule | Detail |
|------|--------|
| U-1 | If `globalConfidence < 0.3` or `needsRefinement == true`, the sheet MUST show a "Low confidence — consider adding another photo" notice. |
| U-2 | If the backend returns 0 valid hypotheses, skip the sheet entirely and open the manual edit screen with category picker pre-focused. |

---

## 3. Current Flow Analysis (Audit Plan)

### 3.1 Android — Capture to UI Trace

Follow this exact chain to identify each failure point:

#### Step 1: Capture

- **File**: `CameraScreen.kt:915-965`
- `onShutterTap` → `cameraManager.captureSingleFrame()` → ML Kit detection → `itemsViewModel.onDetectionReady(rawDetection)` at line 933.
- **Problem**: No UI feedback is emitted between shutter tap and hypothesis sheet. The `_pendingDetections` StateFlow is updated (line 917) but nothing in `CameraScreen` observes it to show a spinner.

#### Step 2: Classification Request

- **File**: `ItemsViewModel.kt:954-998`
- `triggerMultiHypothesisClassification()` launches a coroutine on `workerDispatcher`.
- Calls `CloudClassifier.classifyMultiHypothesis()` (`CloudClassifier.kt:243-301`) which hits `POST /v1/classify?mode=multi-hypothesis`.
- **Problem**: If the result has `hypotheses.isNotEmpty()` it immediately calls `showHypothesisSelectionFromResult()`. There is **no validation** that hypotheses contain valid category names or non-zero confidence.

#### Step 3: Hypothesis Sheet Display

- **File**: `ItemsViewModel.kt:1074-1107` — sets `_hypothesisSelectionState` to `Showing`.
- **File**: `CameraScreen.kt:1127-1148` — observes state and renders `HypothesisSelectionSheet`.
- **File**: `HypothesisSelectionSheet.kt:50-171` — renders hypothesis cards.
- **Problem**: The sheet renders whatever hypotheses are provided, including those with `categoryName == "Unknown"` or empty strings. No gating logic exists.

#### Step 4: User Confirmation

- **File**: `ItemsViewModel.kt:1006` — `confirmPendingDetection(detectionId, hypothesis)`.
- **File**: `ItemsViewModel.kt:1118-1167` — `createItemFromDetection()` creates `ScannedItem` with resolved category and label.
- `classificationStatus` is set to `"CONFIRMED"` (line 1154).
- **File**: `ItemsUiFacade.kt:232-245` — `addItemWithThumbnailEnrichment()` adds item to state AND immediately launches async enrichment.
- **Problem**: The `"CONFIRMED"` status is a plain string on the item. Nothing in the enrichment pipeline checks it.

#### Step 5: Async Overwrite (ROOT CAUSE of silent override)

- **File**: `VisionInsightsPrefiller.kt:103-229` — `extractAndApply()` launches `scope.launch(Dispatchers.IO)` with three sequential layers:
  - **Layer A** (local, ~100-200ms): `stateManager.applyVisionInsights(itemId, ..., suggestedLabel, null)` — can overwrite label.
  - **Layer B** (cloud, ~1-3s): `stateManager.applyVisionInsights(itemId, ..., suggestedLabel, categoryHint)` — can overwrite label AND category.
  - **Layer C** (full enrichment, ~5-15s): `applyEnrichmentResults()` at line 366 — calls `applyVisionInsights()` and `applyEnhancedClassification()` — can overwrite label, category, and attributes.
- **File**: `ItemsStateManager.kt:299-388` — `applyVisionInsights()` and `applyEnhancedClassification()` both call `itemAggregator.applyEnhancedClassification()` with `category` and `label` parameters. There is **no guard** checking `classificationStatus == "CONFIRMED"`.

#### Key StateFlow Variables

| Variable | Location | Purpose |
|----------|----------|---------|
| `_hypothesisSelectionState` | `ItemsViewModel.kt:203` | Controls hypothesis sheet visibility |
| `_pendingDetections` | `ItemsViewModel.kt:217` | Queue of captures awaiting classification |
| `items` | `ItemsStateManager.kt:73` | Authoritative item list |

#### Coroutine Launch Sites (Race Condition Sources)

| Site | File:Line | Dispatcher |
|------|-----------|-----------|
| Classification request | `ItemsViewModel.kt:915` | `workerDispatcher` |
| Enrichment pipeline | `VisionInsightsPrefiller.kt:127` | `Dispatchers.IO` |
| No synchronization between user confirmation and enrichment completion. |

### 3.2 Backend

#### Classify endpoint

- `POST /v1/classify?mode=multi-hypothesis`
- **Can return**: empty `hypotheses` array, hypotheses with `categoryName: "Unknown"`, hypotheses with `confidence: 0`.
- The API contract does not guarantee non-empty valid results. The client MUST validate.

#### Vision insights endpoint

- `POST /v1/vision/insights`
- Returns `suggestedLabel` and `categoryHint` as optional strings.
- `categoryHint` can be any string including values that don't map to valid `ItemCategory` entries.

#### Enrichment endpoint

- `POST /v1/items/enrich` → `GET /v1/items/enrich/status/:requestId`
- Returns `draft.title` which is used as `suggestedLabel`. This is the primary source of late overwrites.

---

## 4. Proposed Solution Architecture

### 4.1 Classification State Machine

Replace the current string-based `classificationStatus` with an enum-driven state machine.

```
                ┌─────────────┐
                │   IDLE      │  (item not yet captured)
                └──────┬──────┘
                       │ capture
                ┌──────▼──────┐
                │ PROCESSING  │  (classification in flight)
                └──────┬──────┘
                       │
              ┌────────┴────────┐
              │                 │
       valid hypotheses    no valid hypotheses
              │                 │
       ┌──────▼──────┐  ┌──────▼──────┐
       │READY_FOR_USER│  │MANUAL_ENTRY │
       └──────┬──────┘  └──────┬──────┘
              │ user taps       │ user fills form
              │ hypothesis      │
       ┌──────▼──────┐  ┌──────▼──────┐
       │  CONFIRMED  │  │  CONFIRMED  │
       └──────┬──────┘  └──────┬──────┘
              │                 │
              └────────┬────────┘
                       │ enrichment suggests different category
                ┌──────▼──────┐
                │REFINEMENT_  │  (optional, non-blocking)
                │AVAILABLE    │
                └─────────────┘
```

**State definitions**:

| State | UI Allowed | Item Mutation Allowed | Background Update Allowed |
|-------|-----------|----------------------|--------------------------|
| IDLE | — | — | — |
| PROCESSING | Show spinner only | No | No |
| READY_FOR_USER | Show hypothesis sheet | No | No |
| MANUAL_ENTRY | Show edit form | No | No |
| CONFIRMED | Show item in list | Attributes only (not category/label) | Attributes + enrichment metadata only |
| REFINEMENT_AVAILABLE | Show refinement chip on item | Only via explicit user tap | Same as CONFIRMED |

**Transition rules**:

- `PROCESSING → READY_FOR_USER`: only if >= 1 hypothesis with non-empty `categoryName` AND `confidence > 0`.
- `PROCESSING → MANUAL_ENTRY`: if 0 valid hypotheses OR timeout (10s).
- `CONFIRMED → REFINEMENT_AVAILABLE`: if enrichment returns a `categoryHint` that differs from confirmed category.
- `REFINEMENT_AVAILABLE → CONFIRMED`: user taps "Accept suggestion" or "Dismiss".

### 4.2 Gating Rules

#### Processing UI gate

- **Condition**: state == `PROCESSING`
- **Show**: spinner overlay on camera preview or pending item card with pulsing skeleton.
- **File to modify**: `CameraScreen.kt` — observe `_pendingDetections` and render spinner when any item is in `AwaitingClassification` state.

#### Hypothesis sheet gate

- **Condition**: state == `READY_FOR_USER` AND all of:
  - `result.hypotheses.size >= 1`
  - `result.hypotheses[0].categoryName` is not blank, not "Unknown"
  - `result.hypotheses[0].confidence > 0`
- **File to modify**: `ItemsViewModel.kt:1074` — add validation before calling `showHypothesisSelection()`.

#### Rejection rules

- Filter out any hypothesis where `categoryName.isNullOrBlank()` or `categoryName.equals("Unknown", ignoreCase = true)` or `confidence <= 0`.
- If after filtering, 0 hypotheses remain → transition to `MANUAL_ENTRY`.

### 4.3 Concurrency & Race-Condition Control

#### 4.3.1 Confirmation lock flag

Add a `userConfirmedFields: Boolean` flag (or `confirmedAt: Long?` timestamp) to `ScannedItem`. When set:

- `applyVisionInsights()` in `ItemsStateManager.kt:337` MUST skip `category` and `labelText` updates.
- `applyEnhancedClassification()` in `ItemsStateManager.kt:299` MUST skip `category` and `label` parameters.
- Both methods MUST still apply `attributes`, `visionAttributes`, and `enrichmentStatus`.

**Implementation location**: `ItemsStateManager.kt:299-388` — add guard:

```
if (existingItem.classificationStatus == "CONFIRMED") {
    // only apply attributes, skip category + label
}
```

#### 4.3.2 Refinement suggestion path

When enrichment would change category/label on a CONFIRMED item:

- Store the suggestion in a new field: `pendingRefinement: RefinementSuggestion?` on `ScannedItem`.
- `RefinementSuggestion` = `{ suggestedCategory: ItemCategory, suggestedLabel: String, source: String }`.
- UI renders this as a dismissible chip/banner on the item card or edit screen.
- User tap applies the suggestion and clears `pendingRefinement`.

#### 4.3.3 Session-scoped cancellation

- Each detection should carry a `detectionSessionId` (already exists as `detectionId` in `PendingDetectionState`).
- If the user dismisses the hypothesis sheet (calls `dismissPendingDetection()`), any in-flight enrichment coroutine for that `detectionId` should be cancelled.
- **Implementation**: store `Job` references in a `Map<String, Job>` in `ItemsViewModel` or `VisionInsightsPrefiller`. Cancel on dismiss.

---

## 5. Required UI Changes (Design Level)

### 5.1 Processing Feedback

- **When**: immediately after shutter tap, before classification returns.
- **What**: a small card slides up from the bottom of the camera preview showing:
  - Thumbnail of the captured frame (already available in `rawDetection.thumbnailRef`).
  - "Identifying..." text with an indeterminate progress indicator.
  - After 5s: secondary text "Still working...".
- **Where**: `CameraScreen.kt` — new composable observing `_pendingDetections` for items in `AwaitingClassification` state.

### 5.2 Hypothesis Sheet Behavior

- **Unchanged**: shows ranked hypothesis cards with confidence band and explanation.
- **New gate**: only appears when valid hypotheses exist (per Section 4.2).
- **New badge**: if `needsRefinement == true` or `globalConfidence < 0.3`, show amber "Low confidence" badge at top of sheet.
- **"None of these"** → navigates to manual edit screen with category picker pre-focused (no item created yet).

### 5.3 Refinement Path

- **When**: enrichment returns a different classification for an already-confirmed item.
- **What**: a subtle chip appears on the item card in the list: "AI suggests: [New Category] — Tap to review".
- **Tap**: opens a bottom sheet with old vs. new classification, "Accept" and "Dismiss" buttons.
- **Dismiss**: clears `pendingRefinement`, no further prompts for this item.

### 5.4 Item List After Confirmation

- Item appears immediately after confirmation with user-chosen category and label.
- Enrichment status indicators (Layer A/B/C dots) may update in the background.
- Category and label text NEVER change without explicit user action.

---

## 6. Acceptance Criteria & Tests

### 6.1 Processing Feedback Timing

| Test | Expected | Pass/Fail Criteria |
|------|----------|-------------------|
| AC-1: Tap shutter, observe UI | Processing indicator visible within 200ms | PASS: indicator appears before classification returns. FAIL: no indicator until hypothesis sheet. |
| AC-2: Slow network (>5s) | "Still working..." text appears | PASS: secondary text after 5s. FAIL: only initial indicator. |
| AC-3: Classification timeout (>10s) | Fallback to manual entry | PASS: manual edit screen opens. FAIL: infinite spinner. |

### 6.2 Hypothesis Sheet Gating

| Test | Expected | Pass/Fail Criteria |
|------|----------|-------------------|
| AC-4: Backend returns 3 valid hypotheses | Sheet shows with 3 cards | PASS: all cards have non-empty category names. |
| AC-5: Backend returns hypotheses with `categoryName: "Unknown"` | Those hypotheses are filtered out | PASS: "Unknown" cards not shown. If 0 remain, manual entry opens. FAIL: "Unknown" is confirmable. |
| AC-6: Backend returns empty hypotheses array | Manual entry screen opens | PASS: no hypothesis sheet shown. FAIL: empty sheet or crash. |
| AC-7: Backend returns `confidence: 0` on all hypotheses | Manual entry screen opens | PASS: no confirmable hypothesis. |

### 6.3 No Silent Override After Confirmation

| Test | Expected | Pass/Fail Criteria |
|------|----------|-------------------|
| AC-8: Confirm hypothesis, wait 15s for enrichment | Item category and label unchanged | PASS: category/label identical to confirmed value. FAIL: any change without user action. **Blocking for release.** |
| AC-9: Confirm hypothesis, enrichment returns different category | Refinement chip appears (not auto-applied) | PASS: chip visible, item unchanged. FAIL: category silently changes. **Blocking for release.** |
| AC-10: Dismiss refinement chip | Chip disappears, item unchanged | PASS: no further prompts. |
| AC-11: Accept refinement suggestion | Item updates to suggested category | PASS: category matches suggestion. |

### 6.4 Edge Cases

| Test | Expected | Pass/Fail Criteria |
|------|----------|-------------------|
| AC-12: Airplane mode — capture photo | Processing indicator → timeout → manual entry | PASS: graceful fallback. FAIL: hang or crash. |
| AC-13: Rapid double-tap shutter | Each capture gets its own processing card; no cross-contamination | PASS: independent items. |
| AC-14: Dismiss hypothesis sheet | No item created; pending detection cleared; enrichment cancelled | PASS: clean state. |

### Release-Blocking Tests

AC-8, AC-9, AC-5, AC-6 are **release-blocking**. All others are important but not blocking.

---

## 7. Execution Checklist

### Android Changes

- [ ] **`ScannedItem` (shared/core-models)**: Add `userConfirmedFields: Boolean` field (or use existing `classificationStatus == "CONFIRMED"` consistently).
- [ ] **`ScannedItem` (shared/core-models)**: Add `pendingRefinement: RefinementSuggestion?` data class and field.
- [ ] **`ItemsViewModel.kt:1074`**: Add hypothesis validation gate before `showHypothesisSelection()`. Filter out invalid hypotheses. If 0 valid remain, route to manual entry.
- [ ] **`ItemsViewModel.kt:954`**: Add timeout (10s) for classification request with fallback to manual entry.
- [ ] **`ItemsStateManager.kt:299-388`**: Add guard in `applyEnhancedClassification()` and `applyVisionInsights()` — skip `category` and `label` if item `classificationStatus == "CONFIRMED"`. Instead, populate `pendingRefinement` if the suggested category differs.
- [ ] **`VisionInsightsPrefiller.kt`**: Store `Job` references per `itemId`. Expose `cancel(itemId)` method.
- [ ] **`ItemsViewModel.kt:dismissPendingDetection()`**: Call `visionInsightsPrefiller.cancel(detectionId)`.
- [ ] **`CameraScreen.kt`**: Add processing feedback composable observing `_pendingDetections` for `AwaitingClassification` state.
- [ ] **`HypothesisSelectionSheet.kt`**: Add low-confidence badge when `needsRefinement == true` or `globalConfidence < 0.3`.
- [ ] **Item list UI**: Add refinement chip composable that reads `pendingRefinement` from item state.

### Tests to Add

- [ ] **Unit test**: `ItemsStateManager` — verify `applyVisionInsights()` skips category/label when `classificationStatus == "CONFIRMED"`.
- [ ] **Unit test**: `ItemsViewModel` — verify hypothesis validation filters out "Unknown" and zero-confidence entries.
- [ ] **Unit test**: `ItemsViewModel` — verify timeout routes to manual entry.
- [ ] **Instrumented test**: Capture → verify processing indicator appears before hypothesis sheet.
- [ ] **Instrumented test**: Mock backend returning "Unknown" hypotheses → verify manual entry path.
- [ ] **Instrumented test**: Confirm hypothesis → mock delayed enrichment with different category → verify no silent overwrite and refinement chip appears.

### Manual Verification Steps

1. Build `devDebug` variant: `./gradlew :androidApp:assembleDevDebug`
2. Install on device: `adb install -r androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk`
3. Verify installed build: Developer Options → "Build info" shows current date/time and git SHA.
4. **Test AC-1**: Tap shutter, observe spinner appears immediately.
5. **Test AC-8**: Confirm a hypothesis, wait 15+ seconds, verify category does not change.
6. **Test AC-5**: Use Developer Options to force backend to return "Unknown" hypotheses (or use a difficult-to-classify object). Verify manual entry path.
7. **Test AC-12**: Enable airplane mode, capture photo, verify graceful timeout to manual entry.

---

## 8. Non-Goals

This plan explicitly does **NOT** address:

- **Pricing accuracy** — price estimates, marketplace values, or pricing model quality.
- **Marketplace posting flow** — export, listing creation, or platform integration.
- **ML model quality** — on-device ML Kit detection accuracy or cloud classifier model improvements.
- **Backend classification algorithm** — the clustering, prompt engineering, or AI model used by `/v1/classify`.
- **Barcode/OCR flows** — only the photo-capture → classification → item-creation path is in scope.
- **PricingAssistantSheet / VariantSchema** — the new pricing assistant feature is separate from this fix.
- **Performance optimization** — classification speed improvements are out of scope (timeout handling is in scope).

---

## Appendix: File Reference Index

| File | Relevance |
|------|-----------|
| `androidApp/.../camera/CameraScreen.kt` | Shutter tap handler, hypothesis sheet rendering, processing feedback (new) |
| `androidApp/.../items/ItemsViewModel.kt` | Detection queue, classification trigger, hypothesis validation (fix), confirmation flow |
| `androidApp/.../items/edit/HypothesisSelectionSheet.kt` | Hypothesis card UI, low-confidence badge (new) |
| `androidApp/.../items/ItemsStateManager.kt` | Item state mutations, enrichment application, confirmation guard (fix) |
| `androidApp/.../items/ItemsUiFacade.kt` | Item creation + enrichment trigger |
| `androidApp/.../ml/VisionInsightsPrefiller.kt` | Async enrichment pipeline, job cancellation (new) |
| `shared/core-models/.../ScannedItem.kt` | Item data model, new fields |
| `shared/core-models/.../ClassificationHypothesis.kt` | Hypothesis data model |
