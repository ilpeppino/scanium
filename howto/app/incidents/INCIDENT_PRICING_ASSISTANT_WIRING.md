# Incident Resolution Plan: AI Pricing Assistant End-to-End Wiring

**Status**: Plan only — no code changes
**Created**: 2026-01-28
**Scope**: Android + Backend

---

## 1 Incident Description

### Current broken experience

When a user taps **Price my item** in the AI Assistant chooser sheet, one of two things happens:

1. **"Pricing not available"** — The `PricingUnavailableSheet` is shown because `showPricingAssistant` is false or `pricingAssistantViewModel` is null in `EditItemScreenV3.kt:554-578`. This means either the feature flag is off, the DI factory was not injected, or the API configuration (`scanium.api.base.url` / `scanium.api.key` in `local.properties`) is missing.

2. **The pricing wizard opens but produces no useful result** — The `PricingAssistantSheet` opens, walks the user through INTRO → optional VARIANTS/COMPLETENESS/IDENTIFIER → CONFIRM, then calls `submitPricingRequest()` in `PricingAssistantViewModel.kt:187`. However:
   - **No mandatory field validation** is enforced before reaching the wizard. The user can arrive with empty brand and product type, hit `InsufficientData` state, and see a disabled button with no guidance on what to fill.
   - **No one-time guidance popup** tells the user that filling in brand, product type, and condition improves pricing accuracy.
   - **No pricing results are visually presented** back in the edit screen after the wizard completes — the `PriceEstimateCard` exists but may not be wired into the visible layout.
   - **No "apply price" flow** persists the suggested price to `userPriceCents` on the item.
   - **No in-app browser** opens when tapping a source — the current `openUrl()` in `PriceEstimateCard.kt:473` uses a raw `ACTION_VIEW` intent that leaves the app.

### Why it blocks product validation

The **Sell faster** value proposition depends on users trusting the AI to suggest a competitive price. If pricing returns nothing or shows "not available", users cannot validate whether the AI pricing is useful.

### Why it breaks trust in the AI Assistant

The AI Assistant chooser sheet prominently highlights the pricing card (`highlightPrice = true`). Showing a broken or empty experience after the user explicitly requests pricing erodes trust in the entire assistant.

---

## 2 Expected User Flow

This section is the authoritative contract.

### Step-by-step

1. **User taps the AI button** on the Edit Item screen (`EditItemScreenV3.kt:348`).
2. **AI Assistant Chooser Sheet** opens (`AiAssistantChooserSheet`).
3. **User taps "Price my item"** → `onChoosePrice` fires.
4. **Mandatory field validation** runs immediately:
   - Required fields: **brand** and **product type** (from `item.attributes["brand"]` and `item.attributes["itemType"]`).
   - If any required field is empty, show an inline error in the chooser sheet (e.g., "Please fill in brand and product type first") and do **not** open the pricing wizard. Return the user to the edit form with the missing fields highlighted.
5. **One-time guidance popup** (if the user has never dismissed it):
   - A dialog or bottom sheet explains: "For best results, fill in brand, product type, condition, and model before pricing."
   - A **"Don't show again"** checkbox. When checked and dismissed, persist `pricing_guidance_dismissed = true` via `SettingsDataStore`.
   - The popup is shown **once** per installation. If already dismissed, skip directly to step 6.
   - Check the preference via `SettingsRepository.pricingGuidanceDismissedFlow`.
6. **Pricing wizard opens** (`PricingAssistantSheet`):
   - INTRO step shows item summary and explains what happens next.
   - VARIANTS step (if schema available) lets user pick size/color/storage.
   - COMPLETENESS step (if options available) lets user pick condition details.
   - IDENTIFIER step (if barcode present) shows barcode value.
   - CONFIRM step shows summary and a "Get Estimate" button.
7. **User taps "Get Estimate"**:
   - `submitPricingRequest()` fires in `PricingAssistantViewModel.kt:187`.
   - Loading shimmer shown via `PricingUiState.Loading`.
   - Backend `POST /v1/pricing/v4` called with brand, productType, model, condition, countryCode, variants, completeness, identifier.
8. **Pricing results presented**:
   - On success: `PriceEstimateCard` renders with price range (e.g., "€15 – €25"), confidence bar, marketplace sources, sample listings.
   - On error: error message with retry button.
   - On no results: "No results found" with retry.
9. **User applies a price**:
   - Taps "Use price €20" button in `PriceEstimateCard`.
   - `onUsePrice(median)` fires → updates `userPriceCents` on the item via `ItemsViewModel.updateItemsFields()`.
   - The edit screen price field immediately reflects the applied price.
   - The pricing wizard/card closes or collapses.
10. **User opens a source**:
    - Taps "View listings" on a marketplace source in `VerifiableSourcesSection`.
    - Opens `searchUrl` in a **Chrome Custom Tab** (in-app browser) so the user stays in the app.
    - Fallback to `ACTION_VIEW` if Custom Tabs is unavailable.

---

## 3 Android Plan

### 3.1 Entry Point

**Files**:
- `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt` — lines 554-578 (chooser sheet wiring), lines 619-634 (pricing sheet display)
- `androidApp/src/main/java/com/scanium/app/items/edit/AiAssistantChooserSheet.kt` — `onChoosePrice` callback
- `androidApp/src/main/java/com/scanium/app/items/edit/ItemEditState.kt` — `showPricingAssistantSheet`, `pricingAssistantViewModel`

**What to change**:
- In `EditItemScreenV3.kt`, the `onChoosePrice` lambda currently checks `showPricingAssistant` (a feature flag Boolean) and falls back to `PricingUnavailableSheet`. Ensure the feature flag is enabled by default in dev builds. Verify the `PricingAssistantViewModel.Factory` is provided via Hilt in `PricingModule.kt`.
- After the wizard completes and results are shown, wire the `onUsePrice` callback to call `itemsViewModel.updateItemsFields(mapOf(itemId to ItemFieldUpdate(userPriceCents = (price * 100).toLong())))`.

### 3.2 Mandatory Field Validation

**Required fields**: `brand` (from `attributes["brand"]`) and `productType` (from `attributes["itemType"]`).

**Where validation lives**: `PricingAssistantViewModel.refreshFromItem()` at line 81 already reads these fields. The method `PricingInputs.missingFields()` (referenced in `PriceEstimateCard`) already computes what's missing.

**What to change**:
- Move the missing-field check **before** opening the pricing wizard — into the `onChoosePrice` handler in `EditItemScreenV3.kt`.
- If `brand` or `productType` is blank, do **not** open the wizard. Instead, show a snackbar or inline message in the chooser sheet: "Please fill in Brand and Product Type before pricing."
- Optionally highlight the missing fields in the edit form by scrolling to them.

### 3.3 One-Time Guidance Popup

**Where popup logic lives**:
- New composable: `PricingGuidanceDialog` (to be added alongside `PricingAssistantSheet.kt`).
- Preference key: add `PRICING_GUIDANCE_DISMISSED` to `SettingsKeys.kt` (pattern: `booleanPreferencesKey("pricing_guidance_dismissed")`).
- Flow: add `pricingGuidanceDismissedFlow: Flow<Boolean>` to `AssistantSettings` in `SettingsRepository.kt`.

**"Don't show again" persistence**:
- When the user checks the box and dismisses, call `settingsRepository.setPricingGuidanceDismissed(true)`.
- On next entry, check the flow. If `true`, skip the dialog.

**What to change**:
- Add the preference key and flow to `AssistantSettings`.
- Add a `PricingGuidanceDialog` composable.
- In `EditItemScreenV3.kt`, between validation passing and opening the pricing wizard, check the preference. If not dismissed, show the dialog first with a "Continue" button that then opens the wizard.

### 3.4 Pricing Results Sheet

**Files**:
- `androidApp/src/main/java/com/scanium/app/items/edit/PriceEstimateCard.kt` — full results UI
- `androidApp/src/main/java/com/scanium/app/items/edit/PricingAssistantSheet.kt` — CONFIRM step
- `androidApp/src/main/java/com/scanium/app/items/edit/PricingAssistantViewModel.kt` — state management

**States** (already defined in `PricingUiState`):
- `Idle` / `InsufficientData` — before pricing
- `Ready` — all fields present, can submit
- `Loading` — shimmer animation
- `Success(insights, isStale)` — price range, confidence, sources, sample listings
- `Error(message, retryable, retryAfterSeconds)` — error with optional retry

**Expected backend data** (from `PricingInsights` model):
- `status`: "OK", "FALLBACK", "NO_RESULTS", "NOT_SUPPORTED"
- `range`: `{ low, high, median, currency }`
- `confidence`: HIGH / MED / LOW
- `marketplacesUsed`: list of `{ name, listingCount, searchUrl }`
- `sampleListings`: list of `{ title, price, url, marketplace }`
- `totalListingsAnalyzed`: Int
- `timeWindowDays`: Int

**Applying a price**:
- `onUsePrice` callback in `PriceEstimateCard` passes the median price (Double).
- Wire this to `itemsViewModel.updateItemsFields(mapOf(itemId to ItemFieldUpdate(userPriceCents = (median * 100).toLong())))`.
- After applying, close the pricing sheet and update the price field in the edit form.

**What to change**:
- Ensure `PriceEstimateCard` is rendered inside the CONFIRM step of `PricingAssistantSheet` when `pricingUiState` is `Success`.
- Wire `onUsePrice` through the sheet to the ViewModel, which calls `itemsViewModel.updateItemsFields()`.
- After applying, set `showPricingAssistantSheet = false`.

### 3.5 In-App Browser

**Current state**: `openUrl()` in `PriceEstimateCard.kt:473` uses `ACTION_VIEW`, which opens the system browser and leaves the app.

**What to change**:
- Replace with Chrome Custom Tabs (`androidx.browser:browser` dependency — check if already in `build.gradle`).
- Create a utility function `openInAppBrowser(context, url)` that uses `CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))` with a fallback to `ACTION_VIEW`.
- Call this from `VerifiableSourcesSection` and `SampleListingsSection` when tapping a source or listing URL.

---

## 4 Backend Plan

### 4.1 Pricing Endpoint

**File**: `backend/src/modules/pricing/routes-v4.ts`

**Endpoint**: `POST /v1/pricing/v4`

**Purpose**: Accept item attributes, query marketplace adapters (eBay, Marktplaats, etc.), optionally normalize with AI, return a price range with verifiable sources.

**Required inputs** (from `PricingV4Request`):
- `itemId` (String) — for caching/dedup
- `brand` (String, required, non-empty)
- `productType` (String, required, non-empty)
- `model` (String, optional)
- `condition` (String: NEW, LIKE_NEW, GOOD, FAIR, POOR)
- `countryCode` (String, 2-letter ISO, required)
- `preferredMarketplaces` (String[], optional)
- `variantAttributes` (Record<string, string>, optional)
- `completeness` (String[], optional)
- `identifier` (String, optional — barcode/EAN)

**Response**: See section 5.

### 4.2 Marketplace Selection

**File**: `backend/config/marketplaces/marketplaces.eu.json`
**Service**: `backend/src/modules/marketplaces/service.ts`

**How it works**:
- On startup, `MarketplaceService` loads the JSON catalog.
- When a pricing request arrives with `countryCode`, the service calls `getMarketplaces(countryCode)` to get the relevant marketplaces (e.g., NL → Bol.com, Marktplaats, Amazon).
- Each marketplace has an `id`, `name`, `domains[]`, and `type` (global/marketplace/classifieds).
- The V4 pricing service iterates adapters (eBay, Marktplaats) for each marketplace and aggregates results.

### 4.3 Verifiable Sources

A **verifiable source** is a marketplace result that includes:
- `name`: Marketplace display name (e.g., "eBay", "Marktplaats")
- `searchUrl`: A URL the user can open to verify the pricing data. This links to the marketplace search page with the item query pre-filled.
- `listingCount`: Number of listings found on that marketplace.

**Fallback**: If scraping/API is unavailable for a marketplace, construct a search URL from the marketplace domain + URL-encoded query string (brand + productType + model). This gives the user a "View listings" link even without price data.

### 4.4 Cost and Safety

**Caching** (via `UnifiedCache`):
- Cache key: hash of `brand + productType + model + condition + countryCode + variants`.
- TTL: 6 hours (21600s).
- Max entries: 500.
- Deduplication enabled — concurrent identical requests coalesce.

**Rate limiting** (via `SlidingWindowRateLimiter`):
- IP: 60 req/min.
- API key: 60 req/min.
- Device: 30 req/min.
- Daily quota per device: configurable (default 100).

**AI usage**:
- AI (OpenAI) is used **only** for query normalization — clustering similar listings, normalizing titles to extract price signals.
- AI is **not** used for price generation itself; prices come from real marketplace data.

---

## 5 Android-Backend Contract

### Request

```
POST /v1/pricing/v4
Content-Type: application/json
x-api-key: <api-key>
x-device-id: <hashed-device-id>
```

```json
{
  "itemId": "abc-123",
  "brand": "Mattel",
  "productType": "Card Game",
  "model": "UNO",
  "condition": "GOOD",
  "countryCode": "NL",
  "preferredMarketplaces": [],
  "variantAttributes": {},
  "completeness": ["box", "instructions"],
  "identifier": "0746775261023"
}
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "status": "OK",
    "countryCode": "NL",
    "range": {
      "low": 3.50,
      "high": 8.00,
      "median": 5.00,
      "currency": "EUR"
    },
    "confidence": "MED",
    "marketplacesUsed": [
      {
        "name": "Marktplaats",
        "listingCount": 12,
        "searchUrl": "https://www.marktplaats.nl/q/uno+kaartspel/"
      },
      {
        "name": "eBay",
        "listingCount": 8,
        "searchUrl": "https://www.ebay.nl/sch/i.html?_nkw=uno+card+game"
      }
    ],
    "sampleListings": [
      {
        "title": "UNO kaartspel compleet",
        "price": { "amount": 5.00, "currency": "EUR" },
        "url": "https://www.marktplaats.nl/v/...",
        "marketplace": "Marktplaats"
      }
    ],
    "totalListingsAnalyzed": 20,
    "timeWindowDays": 30,
    "warnings": []
  }
}
```

### Error cases

| Status | Code | Meaning |
|--------|------|---------|
| 400 | `VALIDATION_ERROR` | Missing required field (brand, productType, condition, countryCode) |
| 401 | `UNAUTHORIZED` | Invalid or missing API key |
| 404 | `NOT_SUPPORTED` | Country code not in marketplace catalog |
| 429 | `RATE_LIMITED` | Too many requests; includes `retryAfterSeconds` |
| 503 | `SERVICE_UNAVAILABLE` | All marketplace adapters failed |

### Confidence / warning fields

- `confidence`: `HIGH` (10+ listings, tight range), `MED` (5-9 listings or wide range), `LOW` (<5 listings or fallback data).
- `warnings`: Array of strings. Possible values:
  - `"fallback_pricing"` — marketplace adapters failed, used AI estimate.
  - `"low_sample_size"` — fewer than 5 listings found.
  - `"stale_data"` — cached result older than 24h.
  - `"wide_range"` — high/low spread > 2x median.

---

## 6 Acceptance Criteria

| # | Criterion | Pass condition |
|---|-----------|----------------|
| 1 | **Required field gate** | Tapping "Price my item" with empty brand or product type shows an error message and does **not** open the pricing wizard. |
| 2 | **Guidance popup (first time)** | On first use, a guidance dialog appears explaining how to improve results. |
| 3 | **"Don't show again"** | Checking the box and dismissing persists the preference. On next use, the popup does not appear. |
| 4 | **Pricing request fires** | With valid fields, tapping "Get Estimate" sends `POST /v1/pricing/v4` to the backend. Verify via Logcat or network inspector. |
| 5 | **Loading state** | Shimmer animation visible while waiting for the backend response. |
| 6 | **Price range displayed** | On success, a price range (e.g., "€3 – €8") is shown with a confidence bar. |
| 7 | **Sources displayed** | At least one marketplace source is shown with name and listing count. |
| 8 | **"View listings" opens in-app browser** | Tapping "View listings" opens the search URL in a Chrome Custom Tab, not the system browser. |
| 9 | **"Use price" applies price** | Tapping "Use price €5" sets `userPriceCents = 500` on the item. The edit screen price field updates immediately. |
| 10 | **Error handling** | Backend returning 429 or 503 shows an error with a retry button. |
| 11 | **Works in dev build** | All of the above works in a `devDebug` build against a local or NAS backend. |

---

## 7 Audit and Verification Plan

### Verify correct wiring

1. **Build and install**: `./gradlew :androidApp:assembleDevDebug` → install on device/emulator.
2. **Confirm build variant**: Open Developer Options in the app (dev flavor only) and verify the build info.
3. **Confirm API config**: Check `local.properties` has `scanium.api.base.url` and `scanium.api.key` set. For NAS backend: use the tunnel URL.

### Confirm pricing endpoint is called

1. Open Logcat, filter on `PricingV4` or `OkHttp`.
2. Scan an item, fill in brand ("Mattel"), product type ("Card Game"), model ("UNO"), condition (Good).
3. Tap AI → Price my item → walk through wizard → tap "Get Estimate".
4. Verify Logcat shows `POST /v1/pricing/v4` request and response.
5. Alternatively, check backend logs: `docker compose -p scanium logs -f backend | grep pricing`.

### Confirm marketplaces are country-specific

1. Set device locale to NL.
2. Run pricing → verify response includes Dutch marketplaces (Marktplaats, Bol.com).
3. Change locale to DE → verify response includes German marketplaces (Kleinanzeigen, Amazon.de).

### Test with a real item (UNO game in NL)

1. Scan an UNO card game box.
2. Fill attributes: brand = "Mattel", productType = "Card Game", model = "UNO", condition = "Good".
3. Run pricing. Expect:
   - Status: OK or FALLBACK
   - Range: approximately €3–€10
   - Sources: Marktplaats and/or eBay
   - At least one sample listing
4. Tap "Use price" → verify the price field in the edit form updates.
5. Tap "View listings" → verify Chrome Custom Tab opens with marketplace search results.

---

## 8 Execution Checklist

### Android files to modify

| File | Change |
|------|--------|
| `androidApp/src/main/java/com/scanium/app/items/edit/EditItemScreenV3.kt` | Add mandatory field check in `onChoosePrice`, wire `onUsePrice` to `ItemsViewModel`, add guidance popup check |
| `androidApp/src/main/java/com/scanium/app/items/edit/PricingAssistantSheet.kt` | Ensure `PriceEstimateCard` is rendered in CONFIRM step with all callbacks wired |
| `androidApp/src/main/java/com/scanium/app/items/edit/PricingAssistantViewModel.kt` | Add `applyPrice(median: Double)` method that calls `itemsViewModel.updateItemsFields()` |
| `androidApp/src/main/java/com/scanium/app/items/edit/PriceEstimateCard.kt` | Replace `openUrl()` with Custom Tabs utility |
| `androidApp/src/main/java/com/scanium/app/data/SettingsRepository.kt` | Add `pricingGuidanceDismissedFlow` and setter |
| `androidApp/src/main/java/com/scanium/app/data/SettingsKeys.kt` | Add `PRICING_GUIDANCE_DISMISSED` key |
| `androidApp/build.gradle.kts` | Add `androidx.browser:browser` dependency if missing |

### New Android files

| File | Purpose |
|------|---------|
| `androidApp/src/main/java/com/scanium/app/items/edit/PricingGuidanceDialog.kt` | One-time guidance popup composable |
| `androidApp/src/main/java/com/scanium/app/util/InAppBrowser.kt` | Chrome Custom Tab utility function |

### Backend files to modify

| File | Change |
|------|--------|
| `backend/src/modules/pricing/routes-v4.ts` | Ensure validation rejects empty brand/productType with 400 |
| `backend/src/modules/pricing/service-v4.ts` | Ensure `searchUrl` is always populated (fallback to constructed URL) |

### Tests to add

| Test | Location |
|------|----------|
| Mandatory field validation blocks pricing | `androidApp/src/test/` — unit test for `PricingAssistantViewModel` |
| Guidance popup respects "don't show again" | `androidApp/src/test/` — unit test for `SettingsRepository` |
| `onUsePrice` updates `userPriceCents` | `androidApp/src/test/` — unit test for ViewModel wiring |
| Backend rejects empty brand/productType | `backend/src/modules/pricing/__tests__/` — Vitest |
| Backend returns `searchUrl` for all sources | `backend/src/modules/pricing/__tests__/` — Vitest |

### Docs to update

| Doc | Change |
|-----|--------|
| `howto/app/reference/` | Add pricing assistant user flow documentation |

### Deployment notes

- If backend validation changes are made, deploy backend before testing Android.
- No database migration required — no schema changes.
- No new environment variables required.

---

## 9 Non-Goals

This plan explicitly does **not** cover:

- **Pricing accuracy tuning** — adjusting AI models, marketplace adapter weights, or confidence thresholds.
- **Marketplace posting** — actually listing items for sale on marketplaces.
- **Subscription gating** — limiting pricing to paid users.
- **Model improvements** — upgrading the LLM or adding new marketplace adapters.
- **Multi-currency support** — the user's country determines the currency; no currency conversion is in scope.
