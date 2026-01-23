# Detection Flow: Landscape Mode - Single Object

This document describes the complete process flow from camera open through detection, classification, and enrichment when scanning a single object in landscape orientation.

## Overview

**Scenario**: User rotates device to landscape mode and captures a single object using the single-shot capture button.

**Key characteristics**:
- Landscape orientation (0° or 180° rotation from sensor)
- Single-shot detection (no multi-frame tracking)
- One object per capture
- Minimal coordinate transformation (sensor already landscape)

---

## Phase 1: Camera Setup and Frame Capture

### 1.1 CameraX Initialization (`CameraXManager.kt`)

**Orientation Configuration**:
```
Device: Landscape (rotated from natural portrait)
Display rotation: 270° (Surface.ROTATION_270) or 0° (Surface.ROTATION_0)
Sensor orientation: Typically 0° or 270° (naturally landscape)
Preview aspect ratio: 4:3
ImageAnalysis aspect ratio: 4:3
```

**Bindings** (lines 502, 518):
```kotlin
Preview.Builder()
  .setTargetAspectRatio(AspectRatio.RATIO_4_3)
  .setTargetRotation(displayRotation) // 270° or 0° for landscape

ImageAnalysis.Builder()
  .setTargetAspectRatio(AspectRatio.RATIO_4_3)
  .setTargetRotation(displayRotation) // 270° or 0° for landscape
  .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
```

### 1.2 Single-Shot Capture Flow

**User action**: Taps single capture button (identical to portrait)

**Capture sequence**:
```
1. takeHighResolutionImage() called
2. ImageCapture.takePicture() executes
3. High-resolution landscape image returned (e.g., 4032x3024)
4. Image converted to Bitmap with rotation applied (if needed)
5. Passed to processHighResImage()
```

**Key difference from portrait**: Image dimensions already match display orientation (width > height).

---

## Phase 2: ML Kit Object Detection

### 2.1 Coordinate System Setup (`ObjectDetectorClient.kt`)

**Landscape coordinate handling** (lines 86-96):

**Landscape example (0° rotation)**:
```
Sensor buffer: 1280x720 (landscape in sensor coordinates)
InputImage created: width=1280, height=720, rotation=0°
ML Kit processing: No rotation needed
ML Kit bbox output: Same coordinate space (1280x720)

uprightWidth = 1280   // NO swap needed (0° rotation)
uprightHeight = 720   // NO swap needed (0° rotation)
```

**Landscape example (270° rotation)**:
```
Sensor buffer: 1280x720 (landscape in sensor coordinates)
InputImage created: width=1280, height=720, rotation=270°
ML Kit processing: Applies 270° rotation internally
ML Kit bbox output: Rotated space (720x1280)

uprightWidth = 720    // SWAP needed (270° rotation)
uprightHeight = 1280  // SWAP needed (270° rotation)
```

**Code logic**:
```kotlin
val rotationDegrees = displayRotation.toDegrees() // 0° or 270°

val (uprightWidth, uprightHeight) = when (rotationDegrees) {
  0, 180 -> {
    // No swap needed - dimensions already correct
    Pair(inputImage.width, inputImage.height)
  }
  90, 270 -> {
    // Swap needed - rotated coordinate space
    Pair(inputImage.height, inputImage.width)
  }
  else -> throw IllegalArgumentException("Invalid rotation")
}
```

### 2.2 Single-Shot Detection

**Identical to portrait mode** - see [01-portrait-single-object.md](./01-portrait-single-object.md#phase-2-ml-kit-object-detection) for ML Kit configuration.

**Key points**:
- `SINGLE_IMAGE_MODE`: Optimized for high-res capture
- `enableMultipleObjects()`: Can still detect multiple (but user expects one)
- `enableClassification()`: Returns category labels
- Returns immediately without multi-frame confirmation

### 2.3 Viewport Filtering

**Identical filtering logic as portrait**:
- Center-crop calculation
- 10% edge inset margin
- Removes partial/cut-off objects

**Landscape specifics**:
```
Viewport aspect ratio: Wider than portrait (e.g., 16:9 vs 9:16)
Edge inset: Still 10% from all edges
Result: Larger acceptable detection area in horizontal direction
```

---

## Phase 3: WYSIWYG Thumbnail Generation

### 3.1 Exact Bounding Box Crop

**Identical process to portrait** (`CameraFrameAnalyzer.kt` lines 169-228):

```kotlin
// 1. Convert normalized bbox to pixel coordinates
val left = (bbox.left * bitmapWidth).toInt()
val top = (bbox.top * bitmapHeight).toInt()
val width = (bbox.width * bitmapWidth).toInt()
val height = (bbox.height * bitmapHeight).toInt()

// 2. Crop exact region (no ML Kit tightening)
val croppedBitmap = Bitmap.createBitmap(
  originalBitmap,
  left, top, width, height
)

// 3. Apply rotation to match display orientation
val matrix = Matrix().apply {
  // Landscape 0°: No rotation needed
  // Landscape 270°: Rotate 270°
  postRotate(rotationDegrees.toFloat())
}

val rotatedThumbnail = if (rotationDegrees != 0) {
  Bitmap.createBitmap(
    croppedBitmap, 0, 0,
    croppedBitmap.width, croppedBitmap.height,
    matrix, true
  )
} else {
  croppedBitmap // No rotation needed
}

// 4. Scale to max dimension 512px
val finalThumbnail = scaleThumbnail(rotatedThumbnail, maxDim = 512)
```

**Landscape-specific optimization**:
```
Landscape 0° rotation: Skip matrix transform entirely (no rotation needed)
Landscape 270° rotation: Apply 270° rotation to match display
```

### 3.2 Thumbnail Dimensions

**Landscape thumbnails typically wider**:
```
Portrait thumbnail: 384x512 (height > width)
Landscape thumbnail: 512x384 (width > height)

Both constrained to max dimension 512px
```

### 3.3 Bitmap Lifecycle Management

**Identical to portrait**:
- Each RawDetection gets own bitmap copy
- Prevents recycling cascade
- Independent lifecycle per object

---

## Phase 4: RawDetection Creation

### 4.1 Detection to RawDetection Mapping

**Structure identical to portrait** (`CameraFrameAnalyzer.kt` lines 344-397):

```kotlin
val rawDetection = RawDetection(
  // Normalized coordinates [0, 1]
  boundingBox = NormalizedRect(
    left = bbox.left.toFloat() / uprightWidth,
    top = bbox.top.toFloat() / uprightHeight,
    width = bbox.width.toFloat() / uprightWidth,
    height = bbox.height.toFloat() / uprightHeight
  ),

  confidence = maxLabel.confidence,
  onDeviceLabel = maxLabel.text,
  onDeviceCategory = mapMLKitCategory(maxLabel.index),

  captureId = UUID.randomUUID().toString(),  // Unique per capture
  captureType = CaptureType.SINGLE_SHOT,

  thumbnailRef = ImageRef.Bytes(thumbnailPngBytes),
  fullFrameBitmap = bitmapCopy,

  frameSharpness = calculateSharpness(bitmap),
  createdAt = System.currentTimeMillis()
)
```

**Landscape-specific notes**:
- Bbox dimensions reflect landscape aspect (wider than tall typically)
- Thumbnail bytes represent landscape-oriented image
- No functional difference from portrait in data structure

### 4.2 Pending Items Flow

**Identical to portrait flow**:
```
RawDetection → ItemsStateManager pending items
  → User sees pending card in UI
  → User taps "Confirm" or "Delete"
  → If confirmed: RawDetection → ScannedItem
  → Added to session items list
  → Passed to ItemAggregator
```

---

## Phase 5: Session-Level Aggregation

### 5.1 ItemAggregator Processing

**Identical aggregation logic as portrait** (`ItemAggregator.kt`):

```kotlin
// Check against existing aggregated items
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
    existingItem.merge(detection)
    return
  }
}

// No match - create new aggregated item
aggregatedItems.add(AggregatedItem.fromDetection(detection))
```

**Landscape-specific considerations**:

**Size similarity** (line 303-309):
```kotlin
// Bounding box area used for size comparison
val area1 = detection.boundingBox.width * detection.boundingBox.height
val area2 = existing.boundingBox.width * existing.boundingBox.height

// Landscape objects may have different aspect ratio
// Example: Same object scanned in portrait vs landscape
// Portrait bbox: 0.3 wide × 0.6 tall = 0.18 area
// Landscape bbox: 0.5 wide × 0.4 tall = 0.20 area
// Size similarity: min(0.18, 0.20) / max(0.18, 0.20) = 0.90 ✓

val sizeSimilarity = min(area1, area2) / max(area1, area2)
if (sizeSimilarity < 0.40) continue // Reject if <60% size difference
```

**Distance similarity** (line 317-322):
```kotlin
// Center-to-center distance in normalized space
val distance = sqrt(
  (detection.boundingBox.centerX - existing.boundingBox.centerX).pow(2) +
  (detection.boundingBox.centerY - existing.boundingBox.centerY).pow(2)
)

// Diagonal length of frame
val diagonal = sqrt(1.0f.pow(2) + 1.0f.pow(2)) // Always ~1.414

// Distance as percentage of diagonal
val normalizedDistance = distance / diagonal

// Landscape captures may have objects at different positions
// Aggregation handles this via normalized coordinates (orientation-independent)
```

**Key insight**: Normalized coordinates make aggregation **orientation-agnostic**. Same object captured in portrait vs landscape will still aggregate correctly.

---

## Phase 6: Cloud Enrichment

### 6.1 Enrichment Request

**Identical to portrait flow** - see [01-portrait-single-object.md](./01-portrait-single-object.md#phase-6-cloud-enrichment-optional) for detailed enrichment process.

**Landscape-specific considerations**:

**Thumbnail upload**:
```
Landscape thumbnail: 512x384 PNG
Portrait thumbnail: 384x512 PNG

Backend Vision API: Agnostic to orientation
Google Vision API: Handles both orientations equally
Result: No difference in enrichment quality
```

### 6.2 Vision API Processing

**Vision API handles landscape images natively**:
```typescript
// Backend: No special handling needed
const visionResult = await visionClient.objectLocalization(imageBuffer)

// Vision API returns same quality results regardless of orientation
// May detect objects more reliably in landscape for wide items (e.g., furniture)
```

**Example: Landscape advantages**:
```
Object: Wide bookshelf
Portrait capture: Bookshelf appears narrow, may be cropped
Landscape capture: Full width visible, better detection confidence

ML Kit confidence: 0.67 (portrait) vs 0.89 (landscape)
Vision API: More web entities found in landscape due to better framing
```

### 6.3 Enrichment Result

**Structure identical to portrait**:
```typescript
EnrichedData {
  title: "Wooden Bookshelf - 5 Shelves"
  description: "Large wooden bookshelf with 5 shelves, good condition"
  suggestedPrice: { min: 50.00, max: 120.00 }
  attributes: {
    product_type: "bookshelf",
    material: "wood",
    shelves: "5"
  }
}
```

---

## Landscape Mode Specifics

### Coordinate Space Summary

**Landscape 0° (no rotation)**:

| Stage | Width | Height | Notes |
|-------|-------|--------|-------|
| Sensor buffer | 1280 | 720 | Landscape orientation |
| InputImage | 1280 | 720 | Original dimensions |
| ML Kit output | 1280 | 720 | **Same space** (no rotation) |
| Normalized bbox | 1.0 | 1.0 | Device-independent |
| Thumbnail | 512 | 384 | No rotation, max dim 512px |

**Landscape 270° (rotated)**:

| Stage | Width | Height | Notes |
|-------|-------|--------|-------|
| Sensor buffer | 1280 | 720 | Landscape orientation |
| InputImage | 1280 | 720 | Original dimensions |
| ML Kit output | 720 | 1280 | **Rotated space** (swapped) |
| Normalized bbox | 1.0 | 1.0 | Device-independent |
| Thumbnail | 384 | 512 | Rotated 270°, max dim 512px |

### Display Alignment

**Landscape 0°**:
```
Camera preview: No rotation needed (sensor already landscape)
ML Kit bboxes: Already in correct coordinate space
Compose overlay: Draws bboxes directly (aligned)
Thumbnail: No rotation needed
```

**Landscape 270°**:
```
Camera preview: Rotated 270° via CameraX targetRotation
ML Kit bboxes: In rotated coordinate space (270°)
Compose overlay: Draws bboxes directly (aligned)
Thumbnail: Rotated 270° to match display
```

**Result**: Perfect WYSIWYG alignment regardless of rotation angle.

---

## Flow Diagram

```
[User rotates device to landscape]
         ↓
[CameraX binds with 0° or 270° rotation]
         ↓
[User taps single-shot capture button]
         ↓
[High-res landscape image captured (4032x3024)]
         ↓
[ML Kit single-shot detection]
         ↓
[Coordinate handling based on rotation]
   ├─ 0°: No dimension swap
   └─ 270°: Swap width/height
         ↓
[Viewport filtering (center crop + 10% inset)]
         ↓
[WYSIWYG thumbnail crop]
   ├─ 0°: No rotation needed
   └─ 270°: Rotate 270°
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

## Key Differences from Portrait Mode

| Aspect | Portrait | Landscape |
|--------|----------|-----------|
| **Display rotation** | 90° or 270° | 0° or 270° |
| **Sensor orientation** | Rotated from sensor | Aligned with sensor (0°) or rotated (270°) |
| **Dimension swap** | Always needed (90°/270°) | Only if 270°, not if 0° |
| **Thumbnail rotation** | Always rotated | Only if 270°, not if 0° |
| **Thumbnail aspect** | Typically portrait (H > W) | Typically landscape (W > H) |
| **Viewport shape** | Tall and narrow | Wide and short |
| **Object framing** | Better for tall items | Better for wide items |

---

## Key Takeaways

1. **Orientation simplification**: Landscape 0° requires no rotation transformations
2. **Conditional rotation**: Landscape 270° requires same handling as portrait 90°
3. **Coordinate normalization**: Makes aggregation orientation-agnostic
4. **WYSIWYG thumbnails**: Exact match with overlay in both landscape orientations
5. **Object framing**: Landscape better for wide objects (shelves, tables, etc.)
6. **Single-shot = No tracking**: Detection happens immediately without multi-frame confirmation
7. **Unique captureId**: Each single-shot gets unique UUID
8. **First detection**: Creates new aggregated item (no merging on first scan)
9. **Cloud enrichment**: Identical process to portrait, orientation-agnostic
10. **Performance optimization**: 0° rotation skips matrix transforms entirely

---

## Use Case Recommendations

**Best for landscape capture**:
- Wide furniture (shelves, tables, dressers)
- Large flat items (rugs, artwork)
- Multiple small items arranged horizontally
- Items that benefit from wide field of view

**Better in portrait**:
- Tall items (lamps, bottles, vases)
- Vertical artwork or posters
- Single isolated objects
- Items requiring vertical framing

**Orientation-agnostic**:
- Boxes, books, electronics
- Most household items
- Aggregation handles both equally well

---

## Related Files

- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` - Camera setup, capture
- `androidApp/src/main/java/com/scanium/app/camera/CameraFrameAnalyzer.kt` - Detection, thumbnails
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` - ML Kit wrapper, coordinate handling
- `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt` - Orientation-agnostic aggregation
- `backend/src/routes/v1/items/enrich.ts` - Enrichment endpoint
- `backend/src/services/enrichment/pipeline.ts` - Vision API processing
