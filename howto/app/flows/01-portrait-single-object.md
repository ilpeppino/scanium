# Detection Flow: Portrait Mode - Single Object

This document describes the complete process flow from camera open through detection, classification, and enrichment when scanning a single object in portrait orientation.

## Overview

**Scenario**: User opens camera in portrait mode and captures a single object using the single-shot capture button.

**Key characteristics**:
- Portrait orientation (90° rotation from sensor)
- Single-shot detection (no multi-frame tracking)
- One object per capture
- Immediate detection without frame confirmation

---

## Phase 1: Camera Setup and Frame Capture

### 1.1 CameraX Initialization (`CameraXManager.kt`)

**Orientation Configuration**:
```
Device: Portrait (natural orientation)
Display rotation: 90° (Surface.ROTATION_90)
Sensor orientation: 0° or 270° (typically landscape)
Preview aspect ratio: 4:3
ImageAnalysis aspect ratio: 4:3
```

**Why 4:3?**: Ensures WYSIWYG (What You See Is What You Get) alignment between preview overlay and ML Kit detections. Both streams use identical aspect ratio to prevent coordinate mismatches.

**Bindings** (`CameraXManager.kt` lines 502, 518):
```kotlin
Preview.Builder()
  .setTargetAspectRatio(AspectRatio.RATIO_4_3)
  .setTargetRotation(displayRotation) // 90° for portrait

ImageAnalysis.Builder()
  .setTargetAspectRatio(AspectRatio.RATIO_4_3)
  .setTargetRotation(displayRotation) // 90° for portrait
  .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
```

### 1.2 Single-Shot Capture Flow

**User action**: Taps single capture button

**Capture sequence** (`CameraXManager.kt` lines 1164-1200):
```
1. takeHighResolutionImage() called
2. ImageCapture.takePicture() executes
3. High-resolution image returned (e.g., 3024x4032)
4. Image converted to Bitmap with rotation applied
5. Passed to processHighResImage()
```

**Resolution**: High-res capture typically 3-4x larger than preview/analysis frames for better quality thumbnails.

---

## Phase 2: ML Kit Object Detection

### 2.1 Coordinate System Setup (`ObjectDetectorClient.kt`)

**Critical rotation handling** (lines 86-96):

**Portrait example**:
```
Sensor buffer: 1280x720 (landscape in sensor coordinates)
InputImage created: width=1280, height=720, rotation=90°
ML Kit processing: Applies 90° rotation internally
ML Kit bbox output: In rotated space (720x1280 coordinate system)

uprightWidth = 720   // Swapped due to 90° rotation
uprightHeight = 1280 // Swapped due to 90° rotation
```

**Why swap dimensions?**:
- InputImage.width/height report **original buffer dimensions** (pre-rotation)
- ML Kit returns bboxes **after rotation** is applied
- For 90°/270° rotations, coordinate space dimensions are swapped
- Without swapping, bboxes would be in wrong coordinate space

### 2.2 Single-Shot Detection (`CameraFrameAnalyzer.kt`)

**Method**: `detectObjects()` (lines 358-364, not tracking mode)

```kotlin
inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
detections = objectDetector.detectObjects(inputImage)
// Returns immediately - no multi-frame confirmation needed
```

**ML Kit configuration**:
```kotlin
ObjectDetectorOptions.Builder()
  .setDetectorMode(SINGLE_IMAGE_MODE) // Optimized for single shot
  .enableMultipleObjects()            // Detect all objects in frame
  .enableClassification()             // Get category labels
```

**Detection output**:
```kotlin
List<DetectedObject>:
  - boundingBox: Rect (in ML Kit coordinate space)
  - trackingId: Int (optional, unused in single-shot)
  - labels: List<Label> (category + confidence)
```

### 2.3 Viewport Filtering (`CameraXManager.kt` lines 1787-1816)

**Center-crop calculation**:
```
Preview viewport: Center-cropped portion of full frame
Edge inset: 10% margin from all edges
Purpose: Remove partial/cut-off objects at frame boundaries
```

**PHASE 3 filtering**:
```kotlin
detections.filter { detection ->
  val bbox = detection.boundingBox
  val center = bbox.centerPoint()

  // Must be inside visible viewport
  viewport.contains(center) &&

  // Must not be too close to edge (10% inset)
  !isNearEdge(bbox, viewport, insetPercent = 0.10f)
}
```

---

## Phase 3: WYSIWYG Thumbnail Generation

### 3.1 Exact Bounding Box Crop (`CameraFrameAnalyzer.kt` lines 169-228)

**Problem**: ML Kit's `cropThumbnail()` applies 4% tightening, showing different crop than overlay bbox.

**Solution**: Manual cropping from exact bounding box coordinates.

```kotlin
// Convert normalized bbox to pixel coordinates
val left = (bbox.left * bitmapWidth).toInt()
val top = (bbox.top * bitmapHeight).toInt()
val width = (bbox.width * bitmapWidth).toInt()
val height = (bbox.height * bitmapHeight).toInt()

// Crop exact region (no tightening)
val croppedBitmap = Bitmap.createBitmap(
  originalBitmap,
  left, top, width, height
)

// Apply rotation to match display orientation
val matrix = Matrix().apply {
  postRotate(rotationDegrees.toFloat()) // 90° for portrait
}
val rotatedThumbnail = Bitmap.createBitmap(
  croppedBitmap, 0, 0,
  croppedBitmap.width, croppedBitmap.height,
  matrix, true
)

// Scale to max dimension 512px (memory optimization)
val finalThumbnail = scaleThumbnail(rotatedThumbnail, maxDim = 512)
```

**Result**: Thumbnail exactly matches what user sees in camera overlay.

### 3.2 Bitmap Lifecycle Management

**Critical: Each detection gets its own bitmap copy** (lines 376-383):
```kotlin
RawDetection(
  thumbnailRef = ImageRef.Bytes(thumbnailBytes),
  fullFrameBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
)
```

**Why copy?**:
- Prevents bitmap recycling cascade
- If user deletes one pending item, others remain intact
- Each RawDetection owns its bitmap lifecycle

---

## Phase 4: RawDetection Creation

### 4.1 Detection to RawDetection Mapping (`CameraFrameAnalyzer.kt` lines 344-397)

```kotlin
val rawDetection = RawDetection(
  // Bbox in normalized coordinates [0, 1]
  boundingBox = NormalizedRect(
    left = bbox.left.toFloat() / uprightWidth,
    top = bbox.top.toFloat() / uprightHeight,
    width = bbox.width.toFloat() / uprightWidth,
    height = bbox.height.toFloat() / uprightHeight
  ),

  confidence = maxLabel.confidence,
  onDeviceLabel = maxLabel.text,
  onDeviceCategory = mapMLKitCategory(maxLabel.index),

  // Unique ID for this capture
  captureId = UUID.randomUUID().toString(),
  captureType = CaptureType.SINGLE_SHOT,

  // WYSIWYG thumbnail
  thumbnailRef = ImageRef.Bytes(thumbnailPngBytes),

  // Full frame copy
  fullFrameBitmap = bitmapCopy,

  frameSharpness = calculateSharpness(bitmap),
  createdAt = System.currentTimeMillis()
)
```

**Key fields**:
- `captureId`: Unique UUID prevents aggregation with other captures
- `captureType`: SINGLE_SHOT (vs TRACKING)
- No `trackingId` since not using ObjectTracker

### 4.2 Pending Items Flow

```
RawDetection created
  ↓
Added to ItemsStateManager pending items
  ↓
User sees pending card in UI (Compose)
  ↓
User taps "Confirm" or "Delete"
  ↓
If confirmed: RawDetection → ScannedItem
  ↓
Added to session items list
  ↓
Passed to ItemAggregator
```

---

## Phase 5: Session-Level Aggregation

### 5.1 ItemAggregator Processing (`ItemAggregator.kt`)

**Single object scenario**: First detection for this object.

**Similarity check** (lines 247-262):
```kotlin
// Check against all existing aggregated items
for (existingItem in aggregatedItems) {
  val similarity = calculateSimilarity(
    detection,
    existingItem,
    weights = AggregationWeights(
      categoryWeight = 0.4,
      labelWeight = 0.15,
      sizeWeight = 0.20,
      distanceWeight = 0.25
    )
  )

  if (similarity >= 0.55) { // REALTIME threshold
    // Merge with existing item
    existingItem.merge(detection)
    return
  }
}

// No match found - create new aggregated item
aggregatedItems.add(AggregatedItem.fromDetection(detection))
```

**Hard filters before similarity** (lines 270-317):
- Time difference: <2 seconds
- Category must match
- Size difference: <60%
- Center distance: <30% of diagonal

**Result for first detection**:
- No existing items to match against
- Creates new `AggregatedItem` with `mergeCount = 1`

### 5.2 Aggregated Item Structure

```kotlin
AggregatedItem(
  aggregatedId = UUID.randomUUID().toString(),
  category = detection.onDeviceCategory,
  labelText = detection.onDeviceLabel,
  boundingBox = detection.boundingBox,
  maxConfidence = detection.confidence,
  averageConfidence = detection.confidence,
  mergeCount = 1,
  sourceDetectionIds = setOf(detection.captureId),
  thumbnail = detection.thumbnailRef,
  firstSeenTimestamp = now,
  lastSeenTimestamp = now,
  priceRange = detection.priceRange
)
```

---

## Phase 6: Cloud Enrichment (Optional)

### 6.1 Enrichment Request (`backend/src/routes/v1/items/enrich.ts`)

**Trigger**: User taps "Enrich" on scanned item

**Request flow**:
```
Android app
  ↓ POST /v1/items/enrich
  ├─ Headers: x-api-key, x-scanium-correlation-id
  ├─ Multipart form data:
  │  ├─ image: thumbnail JPEG file
  │  └─ body: JSON {
  │       itemId,
  │       itemContext: { title, category, condition, priceCents }
  │     }
  ↓
Backend receives request
  ↓
Returns: { requestId, status: "pending" }
```

### 6.2 Enrichment Pipeline (`backend/src/services/enrichment/pipeline.ts`)

**Stage A: Vision Facts Extraction** (lines 123-150):
```typescript
// 1. Compute SHA-256 hash of image
const imageHash = crypto.createHash('sha256')
  .update(imageBuffer)
  .digest('hex')

// 2. Check cache (6-hour TTL, 1000 entry limit)
let visionFacts = visionCache.get(imageHash)

if (!visionFacts) {
  // 3. Call Google Vision API
  const [result] = await visionClient.objectLocalization(imageBuffer)
  const [webResult] = await visionClient.webDetection(imageBuffer)

  visionFacts = {
    detectedObjects: result.localizedObjectAnnotations.map(obj => ({
      name: obj.name,
      score: obj.score
    })),
    webEntities: webResult.webDetection.webEntities.map(entity => ({
      description: entity.description,
      score: entity.score
    })),
    bestGuessLabels: webResult.webDetection.bestGuessLabels
  }

  // 4. Cache for future requests
  visionCache.set(imageHash, visionFacts)
}
```

**Google Vision API detection**:
- Object localization: Physical objects in image
- Web detection: Similar images/products online
- Label detection: General categories

**Stage B: Attribute Normalization** (lines 51-70):
```typescript
// Map detected objects to product types
const productType = PRODUCT_TYPE_MAPPINGS[visionObject.name] ||
                   normalizeProductType(visionObject.name)

// Examples:
// "Tissue box" → "tissue box"
// "Footwear" → "shoe"
// "Coffee mug" → "mug"

const attributes = {
  product_type: productType,
  category: resolveCategory(productType),
  condition: itemContext.condition || "used",
  material: inferMaterial(visionFacts),
  brand: extractBrand(visionFacts.webEntities)
}
```

**Stage C: Draft Generation**:
```typescript
// Use OpenAI GPT-4o-mini to generate listing
const prompt = `
Generate a marketplace listing for this item:
- Product type: ${attributes.product_type}
- Category: ${attributes.category}
- Condition: ${attributes.condition}
- Vision facts: ${JSON.stringify(visionFacts)}
- User context: ${itemContext.title}

Create:
1. Title (50 chars max)
2. Description (200 chars)
3. Suggested price range
`

const completion = await openai.chat.completions.create({
  model: "gpt-4o-mini",
  messages: [{ role: "user", content: prompt }]
})

return {
  title: completion.choices[0].title,
  description: completion.choices[0].description,
  suggestedPrice: completion.choices[0].priceRange,
  attributes: attributes
}
```

### 6.3 Response Polling

**Client polls for completion**:
```
GET /v1/items/enrich/:requestId/status
  ↓
Returns: { status: "completed", result: EnrichedData }
  ↓
Android updates ScannedItem with enriched data
```

---

## Portrait Mode Specifics

### Coordinate Space Summary

| Stage | Width | Height | Notes |
|-------|-------|--------|-------|
| Sensor buffer | 1280 | 720 | Landscape orientation |
| InputImage | 1280 | 720 | Original dimensions |
| ML Kit output | 720 | 1280 | **Rotated space** (swapped) |
| Normalized bbox | 1.0 | 1.0 | Device-independent |
| Thumbnail | 384 | 512 | Rotated 90°, max dim 512px |

### Display Alignment

```
Camera preview: Rotated 90° via CameraX targetRotation
ML Kit bboxes: Already in rotated coordinate space
Compose overlay: Draws bboxes directly (already aligned)
Thumbnail: Manually rotated 90° to match display
```

**Result**: Perfect WYSIWYG alignment - user sees exact crop in thumbnail that matches overlay bbox.

---

## Flow Diagram

```
[User opens camera in portrait]
         ↓
[CameraX binds with 90° rotation]
         ↓
[User taps single-shot capture button]
         ↓
[High-res image captured (3024x4032)]
         ↓
[ML Kit single-shot detection]
         ↓
[Coordinate swap for portrait (90°)]
         ↓
[Viewport filtering (center crop + 10% inset)]
         ↓
[WYSIWYG thumbnail crop + rotate]
         ↓
[RawDetection created (captureId = UUID)]
         ↓
[Pending item shown in UI]
         ↓
[User confirms]
         ↓
[ScannedItem created]
         ↓
[ItemAggregator: No existing match → new aggregated item]
         ↓
[User taps "Enrich" (optional)]
         ↓
[Backend: Vision API → Normalize attributes → GPT draft]
         ↓
[Enriched data returned to Android]
         ↓
[Item updated with enhanced title/description/price]
```

---

## Key Takeaways

1. **Single-shot = No tracking**: Detection happens immediately without multi-frame confirmation
2. **Portrait requires coordinate swap**: ML Kit outputs in rotated space, must swap width/height
3. **WYSIWYG thumbnails**: Manual crop + rotation ensures exact match with overlay
4. **Unique captureId**: Each single-shot gets unique UUID, preventing unwanted aggregation
5. **First detection**: Creates new aggregated item (no merging on first scan)
6. **Cloud enrichment**: Optional, asynchronous, uses Google Vision + GPT for enhanced metadata
7. **4:3 aspect ratio**: Ensures preview and analysis streams have identical coordinate mapping

---

## Related Files

- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` - Camera setup, capture
- `androidApp/src/main/java/com/scanium/app/camera/CameraFrameAnalyzer.kt` - Detection, thumbnails
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` - ML Kit wrapper, coordinates
- `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt` - Session deduplication
- `backend/src/routes/v1/items/enrich.ts` - Enrichment endpoint
- `backend/src/services/enrichment/pipeline.ts` - Vision API, GPT integration
