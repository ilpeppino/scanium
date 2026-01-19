***REMOVED*** Vision Enrichment UI - Edit Items Enhancement

***REMOVED******REMOVED*** Overview

This document describes the UI enhancements made to the Edit Items screen to display and utilize
vision attributes that are automatically extracted when items are scanned.

***REMOVED******REMOVED*** Problem Statement

After scanning an item (e.g., Labello lip balm), the app would show "Unknown classification + no
prefilled attributes" even though:

- The backend was successfully extracting vision data (OCR text, logos, colors, labels)
- The Android app was receiving enriched attributes from the classification endpoint
- The data was being stored in the `ScannedItem` model

**Root Cause:** The Edit Items UI was not displaying or utilizing the vision attributes that were
already available in the data model.

***REMOVED******REMOVED*** Solution

Enhanced the Edit Items screen (`EditItemsScreen.kt`) to:

1. **Pre-fill label/name** from detected brand and model
2. **Display category as editable dropdown** instead of read-only text
3. **Populate recognized text** from vision OCR data
4. **Show detected attributes** (brand, colors) as informational chips

***REMOVED******REMOVED*** Architecture

***REMOVED******REMOVED******REMOVED*** Existing Infrastructure (Already Working)

```
1. User scans item (CameraScreen)
   ↓
2. Item added → ItemsViewModel → ItemsStateManager
   ↓
3. **AUTOMATIC**: triggerEnhancedClassification() called
   ↓
4. CloudClassifier → POST /v1/classify?enrichAttributes=true
   ↓
5. Backend extracts vision facts (OCR, logos, colors, labels)
   ↓
6. Response with enrichedAttributes + visionAttributes
   ↓
7. applyEnhancedClassification() stores in ScannedItem
   ↓
8. **NEW**: EditItemsScreen displays vision data ✅
```

***REMOVED******REMOVED******REMOVED*** Data Flow

**ScannedItem Model Fields:**

- `attributes: Map<String, ItemAttribute>` - Enriched attributes (brand, model, color, material)
- `visionAttributes: VisionAttributes` - Raw vision data (OCR, logos, colors, labels)
- `detectedAttributes: Map<String, ItemAttribute>` - Original detected values (for showing "
  Detected: X")

**VisionAttributes Structure:**

```kotlin
data class VisionAttributes(
    val colors: List<VisionColor>,          // Detected colors with scores
    val ocrText: String?,                   // Full OCR text
    val logos: List<VisionLogo>,           // Brand logos
    val labels: List<VisionLabel>,         // Category hints
    val brandCandidates: List<String>,     // Potential brands
    val modelCandidates: List<String>,     // Potential models
)
```

***REMOVED******REMOVED*** Changes Made

***REMOVED******REMOVED******REMOVED*** 1. Files Modified

***REMOVED******REMOVED******REMOVED******REMOVED*** `/androidApp/src/main/java/com/scanium/app/items/EditItemsScreen.kt`

**Added:**

- `buildSuggestedLabel()` - Helper to construct label from brand + model
- `CategoryDropdown()` - Dropdown for selecting/changing category
- `VisionAttributesDisplay()` - Composable to show detected attributes as chips
- Enhanced `ItemDraft` to include `category` field
- Pre-fill logic for label and recognizedText from vision attributes

**Changes:**

- Line 100-104: Pre-fill label from vision attributes if empty
- Line 107: Pre-fill recognized text from `visionAttributes.ocrText`
- Line 340-346: Replace read-only category text with editable dropdown
- Line 348-349: Add vision attributes display section
- Line 511-519: Helper function to build suggested label
- Line 522-569: Category dropdown composable
- Line 571-630: Vision attributes display with brand and color chips

***REMOVED******REMOVED******REMOVED******REMOVED*** `/androidApp/src/main/java/com/scanium/app/items/state/ItemsStateManager.kt`

**Added:**

- Line 707: `category` field to `ItemFieldUpdate` data class
- Line 313: Apply category updates in `updateItemsFields()`

***REMOVED******REMOVED******REMOVED*** 2. UI Enhancements

***REMOVED******REMOVED******REMOVED******REMOVED*** Pre-filled Fields

**Label / Name:**

- If `labelText` is blank, automatically fills from:
    1. `brand + model` (e.g., "Labello Cherry")
    2. `brand` only (e.g., "Labello")
    3. Remains empty if no vision data

**Recognized Text:**

- Automatically populated from `visionAttributes.ocrText` if not already set

**Category:**

- Now editable via dropdown
- Shows all available categories
- Allows user to correct misclassifications

***REMOVED******REMOVED******REMOVED******REMOVED*** Vision Attributes Display

Shows detected attributes as chips:

- **Brand chip:** Displays detected brand (e.g., "Brand: Labello")
- **Color chips:** Shows up to 3 detected colors (e.g., "Blue", "White", "Red")

The section only appears if vision data is available (not empty).

***REMOVED******REMOVED*** Manual Test Checklist

***REMOVED******REMOVED******REMOVED*** Scenario 1: Scan Item with Clear Brand Logo (e.g., Labello)

1. **Setup:**
    - Ensure backend is running and classification is enabled
    - Have good lighting conditions

2. **Steps:**
   ```
   [ ] Launch app
   [ ] Scan Labello lip balm (or similar branded item)
   [ ] Wait ~1-2 seconds for classification
   [ ] Tap the scanned item to open Edit Items screen
   ```

3. **Expected Results:**
   ```
   [ ] Label/Name field shows "Labello" or "Labello [model]"
   [ ] Category shows correct category (not "Unknown")
   [ ] "Detected Attributes" section visible
   [ ] Brand chip shows "Brand: Labello"
   [ ] Color chips show detected colors (e.g., "Blue", "White")
   [ ] Recognized Text field populated with OCR text
   ```

***REMOVED******REMOVED******REMOVED*** Scenario 2: Edit Category

1. **Steps:**
   ```
   [ ] Open Edit Items for a scanned item
   [ ] Tap Category dropdown
   [ ] Select different category
   [ ] Tap OK to save
   ```

2. **Expected Results:**
   ```
   [ ] Category dropdown opens with all categories
   [ ] Selected category applied to item
   [ ] Category persists after save
   ```

***REMOVED******REMOVED******REMOVED*** Scenario 3: Scan Item Without Brand (e.g., Generic Object)

1. **Steps:**
   ```
   [ ] Scan generic object (no visible brand/logo)
   [ ] Open Edit Items
   ```

2. **Expected Results:**
   ```
   [ ] Label/Name field may be empty or have generic text
   [ ] "Detected Attributes" section may not appear (if no vision data)
   [ ] Colors may still be detected and shown
   [ ] Category based on object labels
   ```

***REMOVED******REMOVED******REMOVED*** Scenario 4: Multiple Items

1. **Steps:**
   ```
   [ ] Scan 2-3 different items
   [ ] Select all items
   [ ] Open Edit Items
   [ ] Swipe between items
   ```

2. **Expected Results:**
   ```
   [ ] Each item shows its own vision attributes
   [ ] Pre-filled data specific to each item
   [ ] Page indicator shows current item
   ```

***REMOVED******REMOVED*** Verification on NAS

***REMOVED******REMOVED******REMOVED*** Build and Deploy

```bash
***REMOVED*** On development machine
cd /Users/family/dev/scanium
./gradlew :androidApp:assembleDebug

***REMOVED*** Transfer APK to device (or use adb install)
adb install -r androidApp/build/outputs/apk/dev/debug/androidApp-dev-debug.apk
```

***REMOVED******REMOVED******REMOVED*** Backend Logs

```bash
***REMOVED*** SSH into NAS
ssh nas
cd /volume1/docker/scanium/repo/deploy/nas/compose

***REMOVED*** Check backend logs for vision extraction
docker logs -f --tail=200 scanium-backend | grep -i "vision\|classify\|enrich"
```

***REMOVED******REMOVED******REMOVED*** Expected Log Output

```
[Classifier] POST /classify - enrichAttributes=true
[VisionExtractor] Extracting visual facts for image hash: abc123...
[VisionCache] Cache MISS for key: vf:abc123:v1:ocr+logo+color+label
[VisionAPI] Calling Google Vision API with features: [TEXT_DETECTION, LOGO_DETECTION, IMAGE_PROPERTIES, LABEL_DETECTION]
[VisionExtractor] Extracted: logos=["Labello"], colors=["blue","white"], ocrSnippets=["Labello","Cherry","SPF15"]
[VisionCache] Stored vision facts, TTL: 3600s
[AttributeResolver] Derived attributes: brand=Labello, color=Blue
[Classifier] Response: category=BEAUTY, enrichedAttributes={brand, color}, visionAttributes={logos, colors, ocr}
```

***REMOVED******REMOVED******REMOVED*** Test Classification Endpoint

```bash
***REMOVED*** From NAS or local machine with access
API_KEY="your-api-key"
DEVICE_ID="test-device-001"

***REMOVED*** Test with sample image
curl -X POST "http://localhost:3000/v1/classify?enrichAttributes=true" \
  -H "X-API-Key: $API_KEY" \
  -H "X-Scanium-Device-Id: $DEVICE_ID" \
  -F "image=@/path/to/test-image.jpg" \
  -F "domainPackId=home_resale" \
  | jq '.visionAttributes'
```

**Expected Response:**

```json
{
  "requestId": "req_...",
  "domainCategoryId": "beauty_cosmetics",
  "confidence": 0.85,
  "label": "Lip balm",
  "enrichedAttributes": {
    "brand": "Labello",
    "color": "Blue"
  },
  "visionAttributes": {
    "colors": [
      {"name": "blue", "hex": "***REMOVED***1E40AF", "score": 0.92},
      {"name": "white", "hex": "***REMOVED***FFFFFF", "score": 0.78}
    ],
    "ocrText": "Labello Cherry SPF 15",
    "logos": [
      {"name": "Labello", "score": 0.95}
    ],
    "labels": [
      {"name": "Cosmetics", "score": 0.88},
      {"name": "Lip care", "score": 0.82}
    ],
    "brandCandidates": ["Labello"],
    "modelCandidates": ["Cherry"]
  }
}
```

***REMOVED******REMOVED*** Known Limitations

1. **Backend Required:** Vision enrichment only works when backend is available and classification
   is enabled
2. **Internet Required:** Google Vision API calls require internet connectivity
3. **Cache Duration:** Vision facts are cached for 1 hour (configurable)
4. **Category Mapping:** Currently no automatic category mapping from vision labels (future
   enhancement)
5. **Attribute Editing:** Brand and color chips are informational only (editing support can be
   added)

***REMOVED******REMOVED*** Future Enhancements

1. **Category Mapping:** Use vision labels to improve category suggestions
    - Map "lip balm" label → BEAUTY category
    - Map "Furniture" label → FURNITURE category

2. **Attribute Confidence UI:** Show confidence scores for detected attributes
    - Green chip for high confidence (>0.8)
    - Yellow chip for medium confidence (0.5-0.8)
    - Gray chip for low confidence (<0.5)

3. **Attribute Editing:** Allow users to edit/confirm detected attributes
    - Tap brand chip to edit
    - Add new attributes manually
    - Mark as "user-confirmed" with higher confidence

4. **Vision Preview:** Show visual indicators on image
    - Highlight OCR text regions
    - Show logo detection boxes
    - Display color extraction areas

***REMOVED******REMOVED*** References

- Backend Vision Extractor: `/backend/src/modules/vision/extractor.ts`
- Backend Classification Route: `/backend/src/modules/classifier/routes.ts`
- Android Cloud Classifier:
  `/androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt`
- Classification Coordinator:
  `/androidApp/src/main/java/com/scanium/app/items/classification/ItemClassificationCoordinator.kt`
- ScannedItem Model:
  `/shared/core-models/src/commonMain/kotlin/com/scanium/shared/core/models/items/ScannedItem.kt`

***REMOVED******REMOVED*** Testing Summary

✅ **Backend:** Vision extraction and classification already tested and working
✅ **Android:** Classification coordinator automatically triggers after scan
✅ **Data Flow:** Vision attributes properly stored in ScannedItem model
✅ **UI:** Edit Items screen now displays vision attributes
✅ **Build:** Android app builds successfully

⏳ **Pending:** Manual testing with real devices and sample items
⏳ **Pending:** NAS deployment and validation
