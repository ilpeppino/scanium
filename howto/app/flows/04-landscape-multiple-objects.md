# Detection Flow: Landscape Mode - Multiple Objects

This document describes the complete process flow from camera open through detection, classification, and enrichment when scanning multiple objects simultaneously in landscape orientation.

## Overview

**Scenario**: User rotates device to landscape mode and captures multiple objects in a single frame using the multi-object capture button.

**Key characteristics**:
- Landscape orientation (0° or 270° rotation from sensor)
- Single high-res frame capture
- Multiple objects detected in one pass
- Shared `captureId` across all objects in batch
- Each object gets independent thumbnail and lifecycle
- Wider field of view enables capturing more objects horizontally

---

## Phase 1: Camera Setup

### 1.1 CameraX Configuration

**Identical to single-object landscape** - see [03-landscape-single-object.md](./03-landscape-single-object.md#phase-1-camera-setup-and-frame-capture) for detailed camera setup.

**Key points**:
```
Display rotation: 0° or 270°
Preview aspect ratio: 4:3
ImageAnalysis aspect ratio: 4:3
Target rotation: Applied to both streams
```

**Landscape advantage for multiple objects**:
```
Portrait viewport: ~720x960 effective area
Landscape viewport: ~960x720 effective area

Result: Landscape allows arranging more objects horizontally
        Better for scanning multiple items on a table/shelf
```

---

## Phase 2: Multi-Object Capture Trigger

### 2.1 User Interaction

**Action**: User taps multi-object capture button in landscape camera UI

**Landscape UI layout**:
- Controls typically positioned on right side (vertical)
- More preview space for horizontal arrangements
- Multi-object button clearly indicates batch capture mode

### 2.2 High-Resolution Capture

**Method**: `captureMultiObjectFrame()` (identical to portrait)

```kotlin
fun captureMultiObjectFrame(onResult: (List<RawDetection>) -> Unit) {
  imageCapture.takePicture(
    outputOptions,
    cameraExecutor,
    object : OnImageCapturedCallback() {
      override fun onCaptureSuccess(image: ImageProxy) {
        // Landscape high-res: e.g., 4032x3024 (W > H)
        val bitmap = image.toBitmap().rotate(displayRotation)

        val detections = detectMultipleObjects(bitmap)

        val rawDetections = detections.map { detection ->
          createRawDetection(detection, bitmap, sharedCaptureId)
        }

        onResult(rawDetections)
      }
    }
  )
}
```

**Landscape capture example**:
```
Sensor resolution: 4032x3024 (already landscape)
Display rotation: 0° → No rotation needed
Display rotation: 270° → Rotate 270° to align with display

Result: High-res landscape bitmap ready for detection
```

---

## Phase 3: ML Kit Multi-Object Detection

### 3.1 Single Detection Pass

**ML Kit configuration** - same as portrait multi-object:

```kotlin
val options = ObjectDetectorOptions.Builder()
  .setDetectorMode(SINGLE_IMAGE_MODE)
  .enableMultipleObjects()  // Critical for multiple detection
  .enableClassification()
  .build()

val inputImage = InputImage.fromBitmap(
  bitmap,
  rotationDegrees = displayRotation.toDegrees()  // 0° or 270°
)

val detectedObjects: List<DetectedObject> =
  objectDetector.process(inputImage).await()
```

**ML Kit performance in landscape**:
```
Portrait: Detects 3-5 objects vertically arranged
Landscape: Detects 5-8 objects horizontally arranged

Reason: Wider field of view → more objects fit in frame
        Better for scanning shelves, tables, collections
```

### 3.2 Coordinate Transformation

**Landscape-specific handling** (`ObjectDetectorClient.kt` lines 86-96):

**Landscape 0° (no rotation)**:
```kotlin
rotationDegrees = 0
uprightWidth = inputImage.width   // 4032 (no swap)
uprightHeight = inputImage.height  // 3024 (no swap)

// ML Kit bboxes already in correct coordinate space
// No transformation needed
```

**Landscape 270° (rotated)**:
```kotlin
rotationDegrees = 270
uprightWidth = inputImage.height   // 3024 (swapped)
uprightHeight = inputImage.width   // 4032 (swapped)

// ML Kit bboxes in rotated space
// Dimensions swapped to match
```

### 3.3 Viewport Filtering (Per-Object)

**Each detection filtered independently**:

```kotlin
val filteredDetections = detectedObjects.filter { detection ->
  val bbox = detection.boundingBox
  val center = Point(bbox.centerX(), bbox.centerY())

  val inViewport = viewport.contains(center)
  val notOnEdge = !isNearEdge(bbox, viewport, insetPercent = 0.10f)

  inViewport && notOnEdge
}
```

**Landscape-specific edge cases**:
```
Landscape layout: Wide objects may extend to left/right edges
10% inset: Removes objects within 403px of left/right edge (for 4032px width)

Example: Scanning 5 books on shelf
- Book 1 (far left): Too close to edge → filtered out
- Books 2-5 (center region): Pass filter → included
- Result: 4 books detected and processed
```

---

## Phase 4: Per-Object Thumbnail Generation

### 4.1 Independent WYSIWYG Thumbnails

**Process identical to portrait multi-object** (`CameraFrameAnalyzer.kt` lines 169-228):

```kotlin
val sharedCaptureId = UUID.randomUUID().toString()

val rawDetections = detectedObjects.map { detection ->
  val bbox = detection.boundingBox

  // 1. Crop exact region from high-res landscape bitmap
  val croppedBitmap = Bitmap.createBitmap(
    highResBitmap,  // 4032x3024
    bbox.left, bbox.top,
    bbox.width(), bbox.height()
  )

  // 2. Apply rotation if needed
  val rotatedThumbnail = if (rotationDegrees == 270) {
    val matrix = Matrix().apply { postRotate(270f) }
    Bitmap.createBitmap(
      croppedBitmap, 0, 0,
      croppedBitmap.width, croppedBitmap.height,
      matrix, true
    )
  } else {
    croppedBitmap  // No rotation for 0°
  }

  // 3. Scale to max dimension 512px
  val thumbnail = scaleThumbnail(rotatedThumbnail, maxDim = 512)

  // 4. Convert to PNG bytes
  val thumbnailBytes = ByteArrayOutputStream().use { stream ->
    thumbnail.compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.toByteArray()
  }

  // 5. Create RawDetection with shared captureId
  RawDetection(
    captureId = sharedCaptureId,  // Shared across batch
    thumbnailRef = ImageRef.Bytes(thumbnailBytes),
    fullFrameBitmap = highResBitmap.copy(),  // Each gets own copy
    // ... other fields
  )
}
```

### 4.2 Landscape Thumbnail Characteristics

**Typical dimensions**:
```
Landscape objects: Wider than tall
Example book spine: 200px × 600px → thumbnail 170x512 (max dim 512)
Example mug: 400px × 400px → thumbnail 512x512 (square)
Example shelf: 800px × 300px → thumbnail 512x192 (very wide)

Portrait objects: Taller than wide
Example bottle: 300px × 800px → thumbnail 192x512 (tall)
```

**Memory considerations**:
```
High-res landscape bitmap: 4032x3024 ARGB_8888 = ~48 MB
5 objects detected = 5 copies = ~240 MB total
5 thumbnails: ~0.8 MB each = ~4 MB total
Total memory: ~244 MB for 5-object landscape capture

Landscape typically captures MORE objects than portrait
→ Higher memory usage for landscape multi-object captures
→ Important for low-memory devices
```

---

## Phase 5: RawDetection Batch Creation

### 5.1 Batch Structure

**Identical to portrait multi-object**:

```kotlin
data class RawDetectionBatch(
  val captureId: String,  // Shared across all objects
  val detections: List<RawDetection>,
  val captureTimestamp: Long,
  val captureType: CaptureType.MULTI_OBJECT
)
```

### 5.2 Example Landscape Batch

**Scenario**: User captures shelf with 5 books in landscape

```kotlin
Batch {
  captureId: "770e9511-f3ac-52e5-b827-557766551111"

  detections: [
    RawDetection {
      detectionId: "881fa622-g4bd-63f6-c938-668877662222"
      captureId: "770e9511-f3ac-52e5-b827-557766551111"  // Shared
      label: "Book"
      category: MEDIA
      bbox: { left: 0.10, top: 0.30, width: 0.15, height: 0.40 }
      thumbnail: [PNG bytes - book 1]
    },

    RawDetection {
      detectionId: "992gb733-h5ce-74g7-d049-779988773333"
      captureId: "770e9511-f3ac-52e5-b827-557766551111"  // Shared
      label: "Book"
      category: MEDIA
      bbox: { left: 0.28, top: 0.32, width: 0.14, height: 0.38 }
      thumbnail: [PNG bytes - book 2]
    },

    // ... 3 more books with same captureId
  ]
}
```

**Key characteristic**: All 5 books share `captureId`, preventing aggregation across batch.

---

## Phase 6: Pending Items UI Flow

### 6.1 Landscape UI Layout

**Multiple pending cards in landscape**:

```
[Pending Items] (5)

┌───────────────┬───────────────┬───────────────┐
│ [Thumb: Book1]│ [Thumb: Book2]│ [Thumb: Book3]│
│ Book          │ Book          │ Book          │
│ 89%           │ 85%           │ 92%           │
│ [✓]  [✗]      │ [✓]  [✗]      │ [✓]  [✗]      │
└───────────────┴───────────────┴───────────────┘

┌───────────────┬───────────────┐
│ [Thumb: Book4]│ [Thumb: Book5]│
│ Book          │ Book          │
│ 78%           │ 81%           │
│ [✓]  [✗]      │ [✓]  [✗]      │
└───────────────┴───────────────┘
```

**UI advantages in landscape**:
- More cards visible horizontally (3 vs 1 in portrait)
- Faster batch review for user
- Better for large collections

### 6.2 Selective Confirmation

**User can act on each item independently**:

**Scenario**: Confirm 4 out of 5 books

```
User taps "Confirm" on Book 1 → ScannedItem created
User taps "Confirm" on Book 2 → ScannedItem created
User taps "Delete" on Book 3 → RawDetection discarded (bitmap recycled)
User taps "Confirm" on Book 4 → ScannedItem created
User taps "Confirm" on Book 5 → ScannedItem created

Result: 4 books in session (Book 3 rejected)
```

---

## Phase 7: Session-Level Aggregation

### 7.1 Batch Processing

**Each confirmed item processed independently** (`ItemAggregator.kt`):

```kotlin
// Book 1 confirmed
itemAggregator.addOrMerge(scannedBook1)

// Book 2 confirmed
itemAggregator.addOrMerge(scannedBook2)

// Book 4 confirmed (Book 3 was deleted)
itemAggregator.addOrMerge(scannedBook4)

// Book 5 confirmed
itemAggregator.addOrMerge(scannedBook5)
```

### 7.2 Handling Similar Objects in Landscape

**Common landscape scenario**: Multiple identical/similar items

**Example**: 5 books on shelf, 3 of which are identical copies of same book

```
Batch captured:
- Book A (copy 1): captureId = "batch-001", label = "Fiction Novel"
- Book A (copy 2): captureId = "batch-001", label = "Fiction Novel"
- Book A (copy 3): captureId = "batch-001", label = "Fiction Novel"
- Book B: captureId = "batch-001", label = "Non-Fiction"
- Book C: captureId = "batch-001", label = "Textbook"

Aggregation logic:
1. Check captureId first
   → All 5 have same captureId → DO NOT merge any within batch

2. Result: 5 separate AggregatedItems created
   → Even though Books A are identical
   → captureId protection ensures they stay separate

Final session items: 5 distinct books
```

**Why this matters**:
```
User intent: "I want to list 3 copies of this book for sale"
System behavior: Creates 3 separate listings (correct)
Without captureId check: Would merge into 1 item (incorrect)
```

### 7.3 Subsequent Rescans

**Scenario**: User rescans one of the books later (different photo)

```
Later scan:
- Book A (new capture): captureId = "batch-002", label = "Fiction Novel"

Aggregation check:
1. captureId ≠ any existing → Can potentially merge
2. Category match: MEDIA = MEDIA ✓
3. Label similarity: "Fiction Novel" vs "Fiction Novel" = 1.0 ✓
4. Size similarity: 0.95 ✓
5. Distance similarity: 0.80 ✓
6. Overall similarity: 0.92 > 0.55 threshold ✓

Result: Merges with one of the existing Book A items
        (First match in aggregated list)

AggregatedItem "agg-book-a-1" {
  mergeCount: 2  // Original + rescan
  maxConfidence: 0.94f
  sourceDetectionIds: ["det-001", "det-006"]
}
```

**Landscape-specific aggregation notes**:
- Horizontal arrangements often have similar items (collections, sets)
- CaptureId protection critical for preventing unwanted merges
- Spatial distance may be larger in landscape (items spread horizontally)
- Distance similarity weighted at 25% to handle varied layouts

---

## Phase 8: Cloud Enrichment (Multiple Items)

### 8.1 Batch Enrichment Strategy

**Sequential vs Concurrent**:

```typescript
// Option 1: Sequential (current implementation)
for (const item of confirmedItems) {
  const enrichmentRequest = await enrichItem(item)
  results.push(enrichmentRequest)
}

// Option 2: Concurrent (possible optimization)
const enrichmentPromises = confirmedItems.map(item => enrichItem(item))
const results = await Promise.all(enrichmentPromises)
```

**Landscape advantage**:
```
Landscape: More items captured (5-8 typical)
Concurrent enrichment: 5 items enriched in ~3 seconds (vs 15 seconds sequential)
Rate limiting: Google Vision API allows 1800 requests/minute
Practical limit: ~10 concurrent requests safe
```

### 8.2 Vision API Efficiency

**Landscape collections often have duplicates**:

```
Scenario: 5 books on shelf, 3 are identical copies

Enrichment request 1 (Book A copy 1):
- Vision API called
- SHA-256: "abc123..."
- Cache miss → API call
- Result cached

Enrichment request 2 (Book A copy 2):
- SHA-256: "abc123..." (identical thumbnail if WYSIWYG crop same)
- Cache HIT → Instant return
- No API call

Enrichment request 3 (Book A copy 3):
- SHA-256: "abc123..."
- Cache HIT → Instant return

Result: Only 3 Vision API calls for 5 books (2 cache hits)
```

**Cache effectiveness**:
```
Portrait multi-object: Typically diverse items → Low cache hit rate
Landscape multi-object: Often collections/sets → High cache hit rate (20-40%)

Example: Scanning 20 identical DVDs
- First DVD: API call
- Remaining 19 DVDs: Cache hits
- API cost: 1 request instead of 20
```

### 8.3 Per-Item Enrichment Results

**Each item receives independent enriched data**:

```typescript
// Book A (copies receive identical enrichment from cache)
EnrichedData {
  title: "The Great Gatsby - F. Scott Fitzgerald"
  description: "Classic American novel from 1925, paperback edition"
  suggestedPrice: { min: 5.00, max: 12.00 }
  attributes: {
    product_type: "book",
    format: "paperback",
    author: "F. Scott Fitzgerald",
    genre: "fiction"
  }
}

// Book B
EnrichedData {
  title: "Sapiens - Yuval Noah Harari"
  description: "Non-fiction hardcover about human history"
  suggestedPrice: { min: 15.00, max: 25.00 }
  attributes: {
    product_type: "book",
    format: "hardcover",
    author: "Yuval Noah Harari",
    genre: "non-fiction"
  }
}

// ... and so on for remaining books
```

---

## Landscape Mode Specifics

### Coordinate Space Summary

**Landscape 0° (no rotation)**:

| Stage | Width | Height | Notes |
|-------|-------|--------|-------|
| Sensor buffer | 1280 | 720 | Landscape orientation |
| InputImage | 1280 | 720 | Original dimensions |
| ML Kit output | 1280 | 720 | Same space (no rotation) |
| Per-object bbox | Varies | Varies | Each object unique |
| Thumbnails | ~512 | ~384 | Landscape-oriented typically |

**Landscape 270° (rotated)**:

| Stage | Width | Height | Notes |
|-------|-------|--------|-------|
| Sensor buffer | 1280 | 720 | Landscape orientation |
| InputImage | 1280 | 720 | Original dimensions |
| ML Kit output | 720 | 1280 | Rotated space (swapped) |
| Per-object bbox | Varies | Varies | Each object unique |
| Thumbnails | ~384 | ~512 | Rotated 270° |

### Object Density Advantages

**Landscape layout efficiency**:

```
Portrait frame: 720 wide × 960 tall
- Vertical stacking: 3-4 objects typical
- Horizontal space underutilized

Landscape frame: 960 wide × 720 tall
- Horizontal arrangement: 5-8 objects typical
- Better utilization of sensor resolution
- Ideal for shelves, tables, countertops
```

**Real-world examples**:

```
Use case: Scanning book collection
Portrait: 2-3 books per capture → 10 captures for 30 books
Landscape: 5-6 books per capture → 5-6 captures for 30 books
Time savings: ~50% reduction in captures needed
```

---

## Flow Diagram

```
[User rotates device to landscape]
         ↓
[CameraX binds with 0° or 270° rotation]
         ↓
[User taps multi-object capture button]
         ↓
[High-res landscape image captured (4032x3024)]
         ↓
[ML Kit single-shot detection]
         ↓
[ML Kit returns multiple DetectedObjects (e.g., 5)]
         ↓
[Coordinate handling based on rotation]
   ├─ 0°: No dimension swap
   └─ 270°: Swap width/height
         ↓
[Viewport filtering per object]
         ↓
[Generate shared captureId = UUID]
         ↓
┌─────────────────────────────────────────┐
│ For each detected object:              │
│   - Crop exact bbox from high-res      │
│   - Rotate thumbnail if needed (270°)  │
│   - Create RawDetection with:          │
│     * Shared captureId                 │
│     * Unique detectionId               │
│     * Own bitmap copy (~48 MB)         │
│     * Own thumbnail (~0.8 MB)          │
└─────────────────────────────────────────┘
         ↓
[Batch of RawDetections (e.g., 5) → Pending items in landscape UI]
         ↓
[User reviews each pending card (visible 3 at a time in landscape)]
         ↓
┌─────────────────────────────────────────┐
│ For each confirmed item:               │
│   - Convert to ScannedItem             │
│   - Pass to ItemAggregator             │
│   - Check captureId ≠ existing         │
│   - Calculate similarity               │
│   - Merge or create new aggregated item│
└─────────────────────────────────────────┘
         ↓
[Multiple aggregated items in session (e.g., 5)]
         ↓
[User taps "Enrich" on each item (optional)]
         ↓
┌─────────────────────────────────────────┐
│ For each enrichment request:           │
│   - Backend: Vision API (or cache)     │
│   - Cache hit rate higher for similar  │
│     items in collections               │
│   - Normalize attributes               │
│   - GPT draft generation               │
│   - Return enriched data               │
└─────────────────────────────────────────┘
         ↓
[Each item updated with enhanced metadata]
```

---

## Key Differences from Portrait Multi-Object

| Aspect | Portrait | Landscape |
|--------|----------|-----------|
| **Field of view** | Vertical (tall) | Horizontal (wide) |
| **Typical object count** | 3-5 objects | 5-8 objects |
| **Object arrangement** | Vertical stacking | Horizontal layout |
| **Thumbnail aspect** | Portrait (H > W) | Landscape (W > H) |
| **Memory usage** | ~150 MB for 3 objects | ~240 MB for 5 objects |
| **UI layout** | 1 card per row | 3 cards per row |
| **Edge filtering** | Top/bottom edges | Left/right edges |
| **Best for** | Individual tall items | Collections, shelves |
| **Cache hit rate** | Low (diverse items) | Higher (similar items) |
| **Capture efficiency** | Lower (fewer objects) | Higher (more objects) |

---

## Key Takeaways

1. **Landscape = More objects**: Wider field of view enables 5-8 objects vs 3-5 in portrait
2. **Rotation handling**: 0° needs no transformation, 270° needs dimension swap
3. **Shared captureId**: Critical for preventing merges of items from same photo
4. **Memory intensive**: Each object gets ~48 MB bitmap copy
5. **UI efficiency**: Landscape shows 3 cards per row (faster review)
6. **Collection advantage**: Ideal for scanning shelves, sets, collections
7. **Cache efficiency**: Higher hit rate for similar items (20-40%)
8. **Concurrent enrichment**: Possible optimization for large batches
9. **Independent lifecycle**: Each object can be confirmed/deleted independently
10. **WYSIWYG guarantee**: Each thumbnail exactly matches overlay bbox

---

## Use Case Recommendations

**Best for landscape multi-object**:
- Book collections on shelves
- DVD/media collections
- Kitchen items on counter
- Tools on workbench
- Toys arranged on floor
- Products on retail shelf
- Any horizontal arrangement of items

**Better in portrait**:
- Vertical stacking of boxes
- Tall items (bottles, vases)
- Limited horizontal space
- Single isolated items

**Optimization tips**:
- Arrange items horizontally for landscape capture
- Keep items within 10% edge inset for reliable detection
- Good lighting reduces detection errors
- Similar items benefit from Vision API caching
- Confirm/delete quickly to free bitmap memory

---

## Related Files

- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` - Multi-object capture
- `androidApp/src/main/java/com/scanium/app/camera/CameraFrameAnalyzer.kt` - Batch detection
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` - ML Kit multi-object
- `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt` - captureId handling
- `backend/src/routes/v1/items/enrich.ts` - Enrichment endpoint
- `backend/src/services/enrichment/pipeline.ts` - Vision API caching, concurrent processing
