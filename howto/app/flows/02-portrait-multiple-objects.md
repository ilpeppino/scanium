# Detection Flow: Portrait Mode - Multiple Objects

This document describes the complete process flow from camera open through detection, classification, and enrichment when scanning multiple objects simultaneously in portrait orientation.

## Overview

**Scenario**: User opens camera in portrait mode and captures multiple objects in a single frame using the multi-object capture button.

**Key characteristics**:
- Portrait orientation (90° rotation from sensor)
- Single high-res frame capture
- Multiple objects detected in one pass
- Shared `captureId` across all objects in batch
- Each object gets independent thumbnail and lifecycle

---

## Phase 1: Camera Setup

### 1.1 CameraX Configuration

**Identical to single-object portrait**:
```
Display rotation: 90° (Surface.ROTATION_90)
Preview aspect ratio: 4:3
ImageAnalysis aspect ratio: 4:3
Target rotation: Applied to both streams
```

See [01-portrait-single-object.md](./01-portrait-single-object.md#phase-1-camera-setup-and-frame-capture) for detailed camera setup.

---

## Phase 2: Multi-Object Capture Trigger

### 2.1 User Interaction

**Action**: User taps multi-object capture button in camera UI

**UI indication**:
- Button typically shows icon suggesting multiple items (e.g., grid icon)
- Different from single-shot capture button
- May require holding steady for optimal detection

### 2.2 High-Resolution Capture (`CameraXManager.kt` lines 1202-1250)

**Method**: `captureMultiObjectFrame()`

```kotlin
fun captureMultiObjectFrame(onResult: (List<RawDetection>) -> Unit) {
  // 1. Capture high-res image
  imageCapture.takePicture(
    outputOptions,
    cameraExecutor,
    object : OnImageCapturedCallback() {
      override fun onCaptureSuccess(image: ImageProxy) {
        // 2. Convert to bitmap with rotation
        val bitmap = image.toBitmap().rotate(displayRotation)

        // 3. Detect ALL objects in frame
        val detections = detectMultipleObjects(bitmap)

        // 4. Process each detection
        val rawDetections = detections.map { detection ->
          createRawDetection(detection, bitmap, sharedCaptureId)
        }

        onResult(rawDetections)
      }
    }
  )
}
```

**Key difference from single-object**:
- Expects multiple detections
- All share same `captureId` for batch tracking
- Each object processed independently after detection

---

## Phase 3: ML Kit Multi-Object Detection

### 3.1 Single Detection Pass (`CameraFrameAnalyzer.kt` lines 358-397)

**Method**: `detectObjects()` (single-shot mode)

```kotlin
// Create InputImage with portrait rotation
val inputImage = InputImage.fromBitmap(
  bitmap,
  rotationDegrees = 90  // Portrait
)

// ML Kit configuration for multiple objects
val options = ObjectDetectorOptions.Builder()
  .setDetectorMode(SINGLE_IMAGE_MODE)
  .enableMultipleObjects()  // ← Critical for multiple detection
  .enableClassification()
  .build()

// Single detection pass - returns ALL objects
val detectedObjects: List<DetectedObject> =
  objectDetector.process(inputImage).await()
```

**ML Kit capabilities**:
- Detects up to ~5-8 objects simultaneously (ML Kit limitation)
- Returns all detections in single API call
- Each object has independent bounding box and labels

### 3.2 Coordinate Transformation

**Portrait-specific handling** (`ObjectDetectorClient.kt` lines 86-96):

```kotlin
// High-res example: 3024x4032 image in portrait
val rotationDegrees = 90

// ML Kit returns bboxes in rotated space
val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) {
  inputImage.height  // 4032 → width after rotation
} else {
  inputImage.width
}

val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) {
  inputImage.width  // 3024 → height after rotation
} else {
  inputImage.height
}

// ML Kit bboxes now correctly interpreted
// Bbox coordinates are in 4032x3024 space (portrait)
```

### 3.3 Viewport Filtering (Per-Object)

**Each detection filtered independently** (`CameraXManager.kt` lines 1787-1816):

```kotlin
val filteredDetections = detectedObjects.filter { detection ->
  val bbox = detection.boundingBox
  val center = Point(bbox.centerX(), bbox.centerY())

  // Check viewport bounds
  val inViewport = viewport.contains(center)

  // Check edge inset (10% margin)
  val notOnEdge = !isNearEdge(bbox, viewport, insetPercent = 0.10f)

  inViewport && notOnEdge
}
```

**Example scenario**:
```
ML Kit detects: 4 objects
Viewport filtering removes: 1 object (too close to edge)
Final detections passed forward: 3 objects
```

---

## Phase 4: Per-Object Thumbnail Generation

### 4.1 Independent WYSIWYG Thumbnails

**Critical: Each object gets its own exact crop** (`CameraFrameAnalyzer.kt` lines 169-228)

```kotlin
// Shared captureId for all objects in this batch
val sharedCaptureId = UUID.randomUUID().toString()

val rawDetections = detectedObjects.map { detection ->
  // 1. Get bbox in pixel coordinates
  val bbox = detection.boundingBox
  val left = bbox.left
  val top = bbox.top
  val width = bbox.width()
  val height = bbox.height()

  // 2. Crop exact region from HIGH-RES bitmap
  val croppedBitmap = Bitmap.createBitmap(
    highResBitmap,  // e.g., 3024x4032
    left, top, width, height
  )

  // 3. Rotate thumbnail 90° for portrait display
  val matrix = Matrix().apply {
    postRotate(90f)
  }
  val rotatedThumbnail = Bitmap.createBitmap(
    croppedBitmap,
    0, 0,
    croppedBitmap.width,
    croppedBitmap.height,
    matrix,
    true
  )

  // 4. Scale to max dimension 512px
  val thumbnail = scaleThumbnail(rotatedThumbnail, maxDim = 512)

  // 5. Convert to PNG bytes
  val thumbnailBytes = ByteArrayOutputStream().use { stream ->
    thumbnail.compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.toByteArray()
  }

  // 6. Create RawDetection with shared captureId
  RawDetection(
    captureId = sharedCaptureId,  // ← Shared across batch
    thumbnailRef = ImageRef.Bytes(thumbnailBytes),
    fullFrameBitmap = highResBitmap.copy(),  // ← Each gets own copy
    // ... other fields
  )
}
```

### 4.2 Bitmap Memory Management

**Critical memory consideration**:
```kotlin
// Each RawDetection owns its own bitmap copy
RawDetection(
  fullFrameBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
)
```

**Why separate copies?**:
- If user deletes one pending item, others remain intact
- No shared bitmap reference that could be recycled prematurely
- Each object has independent lifecycle

**Memory cost example**:
```
High-res bitmap: 3024x4032 ARGB_8888 = ~48 MB
3 objects detected = 3 copies = ~144 MB total
Thumbnail for each: ~512x384 = ~0.8 MB each = ~2.4 MB total
Total memory: ~146 MB for 3-object capture

Mitigation: Bitmaps released when user confirms/deletes pending items
```

---

## Phase 5: RawDetection Batch Creation

### 5.1 Batch Structure (`CameraFrameAnalyzer.kt` lines 344-427)

```kotlin
data class RawDetectionBatch(
  val captureId: String,           // Shared across batch
  val detections: List<RawDetection>,  // Individual objects
  val captureTimestamp: Long,
  val captureType: CaptureType.MULTI_OBJECT
)

// Each RawDetection in batch:
RawDetection(
  // Shared fields
  captureId = sharedCaptureId,      // Same for all in batch
  captureType = CaptureType.MULTI_OBJECT,
  captureTimestamp = timestamp,

  // Unique per object
  boundingBox = normalizedBbox,     // Different bbox
  onDeviceLabel = "Coffee mug",     // Different label
  onDeviceCategory = ItemCategory.HOME_GOODS,
  confidence = 0.87f,
  thumbnailRef = ImageRef.Bytes(...),  // Unique thumbnail
  fullFrameBitmap = bitmapCopy,     // Separate copy

  // Generated unique ID (not shared)
  detectionId = UUID.randomUUID().toString()
)
```

**Key distinction**:
- `captureId`: **Shared** - identifies the capture event
- `detectionId`: **Unique** - identifies individual object

### 5.2 Example Batch

**Scenario**: User captures photo with 3 objects (coffee mug, book, phone)

```kotlin
Batch {
  captureId: "550e8400-e29b-41d4-a716-446655440000"

  detections: [
    RawDetection {
      detectionId: "661f9511-f3ac-52e5-b827-557766551111"
      captureId: "550e8400-e29b-41d4-a716-446655440000"  // ← Shared
      label: "Coffee mug"
      bbox: { left: 0.1, top: 0.2, width: 0.3, height: 0.4 }
      thumbnail: [PNG bytes of mug]
    },

    RawDetection {
      detectionId: "772fa622-g4bd-63f6-c938-668877662222"
      captureId: "550e8400-e29b-41d4-a716-446655440000"  // ← Shared
      label: "Book"
      bbox: { left: 0.5, top: 0.3, width: 0.4, height: 0.5 }
      thumbnail: [PNG bytes of book]
    },

    RawDetection {
      detectionId: "883gb733-h5ce-74g7-d049-779988773333"
      captureId: "550e8400-e29b-41d4-a716-446655440000"  // ← Shared
      label: "Mobile phone"
      bbox: { left: 0.2, top: 0.6, width: 0.3, height: 0.3 }
      thumbnail: [PNG bytes of phone]
    }
  ]
}
```

---

## Phase 6: Pending Items UI Flow

### 6.1 Multiple Pending Cards

**UI presentation**:
```
[Pending Items] (3)

┌─────────────────────┐
│ [Thumbnail: Mug]    │
│ Coffee mug          │
│ Confidence: 87%     │
│ [Confirm] [Delete]  │
└─────────────────────┘

┌─────────────────────┐
│ [Thumbnail: Book]   │
│ Book                │
│ Confidence: 92%     │
│ [Confirm] [Delete]  │
└─────────────────────┘

┌─────────────────────┐
│ [Thumbnail: Phone]  │
│ Mobile phone        │
│ Confidence: 78%     │
│ [Confirm] [Delete]  │
└─────────────────────┘
```

### 6.2 Independent User Actions

**User can act on each item independently**:

**Scenario 1**: Confirm all
```
User taps "Confirm" on mug → ScannedItem created → Added to session
User taps "Confirm" on book → ScannedItem created → Added to session
User taps "Confirm" on phone → ScannedItem created → Added to session

Result: 3 items in session
```

**Scenario 2**: Selective confirmation
```
User taps "Confirm" on mug → Added to session
User taps "Delete" on book → RawDetection discarded (bitmap recycled)
User taps "Confirm" on phone → Added to session

Result: 2 items in session (book rejected)
```

---

## Phase 7: Session-Level Aggregation

### 7.1 Batch Processing (`ItemAggregator.kt`)

**Each confirmed item processed independently**:

```kotlin
// User confirms mug
val scannedMug = ScannedItem.fromRawDetection(mugDetection)
itemAggregator.addOrMerge(scannedMug)

// User confirms book (separate call)
val scannedBook = ScannedItem.fromRawDetection(bookDetection)
itemAggregator.addOrMerge(scannedBook)

// User confirms phone (separate call)
val scannedPhone = ScannedItem.fromRawDetection(phoneDetection)
itemAggregator.addOrMerge(scannedPhone)
```

### 7.2 Aggregation Logic Per Item

**For each item, check against existing aggregated items**:

```kotlin
fun addOrMerge(detection: ScannedItem) {
  for (existingItem in aggregatedItems) {
    // 1. Hard filters
    val timeDiff = abs(detection.timestamp - existingItem.lastSeenTimestamp)
    if (timeDiff > 2000) continue  // More than 2 seconds apart

    if (detection.category != existingItem.category) continue

    // 2. Check if same capture batch
    if (detection.captureId == existingItem.captureId) {
      // Same batch - DO NOT MERGE
      // These are different objects from same photo
      continue
    }

    // 3. Similarity check
    val similarity = calculateSimilarity(detection, existingItem)
    if (similarity >= 0.55) {
      // Different capture, but same object → merge
      existingItem.merge(detection)
      return
    }
  }

  // No match - create new aggregated item
  aggregatedItems.add(AggregatedItem.fromDetection(detection))
}
```

**Critical captureId check** (line 276):
```kotlin
if (detection.captureId == candidate.captureId) {
  // Same batch - these are DIFFERENT objects in same photo
  // DO NOT merge even if spatially close
  continue
}
```

**Why this matters**:
```
Scenario: User places 3 identical coffee mugs in frame
ML Kit detects: 3 separate bounding boxes
Result: 3 separate RawDetections with shared captureId
Aggregation: All 3 treated as DISTINCT items (no merging)
```

### 7.3 Aggregation Example

**Initial state**: Empty session

**After confirming 3-object batch**:
```
AggregatedItems: [
  AggregatedItem {
    aggregatedId: "agg-001"
    labelText: "Coffee mug"
    category: HOME_GOODS
    mergeCount: 1
    sourceDetectionIds: ["661f9511-..."]
  },

  AggregatedItem {
    aggregatedId: "agg-002"
    labelText: "Book"
    category: MEDIA
    mergeCount: 1
    sourceDetectionIds: ["772fa622-..."]
  },

  AggregatedItem {
    aggregatedId: "agg-003"
    labelText: "Mobile phone"
    category: ELECTRONICS
    mergeCount: 1
    sourceDetectionIds: ["883gb733-..."]
  }
]
```

**Subsequent re-scan of same mug**:
```
User rescans coffee mug (new photo, different captureId)
ML Kit detects: Coffee mug with high similarity
Aggregation: Merges with agg-001 (same object, different capture)

AggregatedItem agg-001 {
  mergeCount: 2  // ← Incremented
  maxConfidence: 0.92f  // Higher of two detections
  sourceDetectionIds: ["661f9511-...", "994hc844-..."]  // Both included
}
```

---

## Phase 8: Cloud Enrichment (Multiple Items)

### 8.1 Sequential Enrichment

**User can enrich each item independently**:

```
User taps "Enrich" on mug
  ↓ POST /v1/items/enrich (mug thumbnail)
  ↓ Returns: requestId-mug

User taps "Enrich" on book
  ↓ POST /v1/items/enrich (book thumbnail)
  ↓ Returns: requestId-book

User taps "Enrich" on phone
  ↓ POST /v1/items/enrich (phone thumbnail)
  ↓ Returns: requestId-phone
```

**Backend processes each request independently**:
- Each item has own Vision API call
- Each item has own attribute normalization
- Each item has own GPT draft generation
- Requests processed concurrently (if API limits allow)

### 8.2 Vision API Caching Benefit

**Scenario**: User accidentally captures same objects twice

```
Capture 1: 3 objects (mug, book, phone) → 3 thumbnails
Enrichment request 1 (mug): Vision API called → SHA-256 hash cached

Capture 2: Same 3 objects (identical framing) → 3 new thumbnails
Enrichment request 2 (mug): SHA-256 hash matches → Cache hit!

Result: Second mug enrichment instant (no Vision API call)
```

**Cache key**: SHA-256 of image bytes (not captureId)
**Cache TTL**: 6 hours
**Cache limit**: 1000 entries (LRU eviction)

### 8.3 Enrichment Result Per Item

**Each item receives independent enriched data**:

```kotlin
// Mug enrichment result
EnrichedData {
  title: "Ceramic Coffee Mug - White"
  description: "Standard 12oz white ceramic mug in good condition"
  suggestedPrice: { min: 5.00, max: 12.00 }
  attributes: {
    product_type: "mug",
    material: "ceramic",
    color: "white",
    capacity: "12oz"
  }
}

// Book enrichment result
EnrichedData {
  title: "Hardcover Book - Fiction Novel"
  description: "Fiction novel in hardcover format, gently used"
  suggestedPrice: { min: 8.00, max: 15.00 }
  attributes: {
    product_type: "book",
    format: "hardcover",
    genre: "fiction"
  }
}

// Phone enrichment result
EnrichedData {
  title: "Smartphone - Black"
  description: "Modern smartphone with touchscreen display"
  suggestedPrice: { min: 150.00, max: 400.00 }
  attributes: {
    product_type: "smartphone",
    color: "black"
  }
}
```

---

## Portrait Mode Specifics

### Coordinate Handling (Same as Single-Object)

See [01-portrait-single-object.md](./01-portrait-single-object.md#portrait-mode-specifics) for detailed coordinate space handling.

**Key points**:
- ML Kit returns all bboxes in rotated space (720x1280 for portrait)
- Each bbox independently transformed to normalized coordinates
- Each thumbnail independently rotated 90°
- All objects maintain WYSIWYG alignment

---

## Flow Diagram

```
[User opens camera in portrait]
         ↓
[CameraX binds with 90° rotation]
         ↓
[User taps multi-object capture button]
         ↓
[High-res image captured (3024x4032)]
         ↓
[ML Kit single-shot detection]
         ↓
[ML Kit returns multiple DetectedObjects (e.g., 3)]
         ↓
[Coordinate swap for portrait (90°)]
         ↓
[Viewport filtering per object]
         ↓
[Generate shared captureId = UUID]
         ↓
┌─────────────────────────────────────────┐
│ For each detected object:              │
│   - Crop exact bbox from high-res      │
│   - Rotate thumbnail 90°               │
│   - Create RawDetection with:          │
│     * Shared captureId                 │
│     * Unique detectionId               │
│     * Own bitmap copy                  │
│     * Own thumbnail                    │
└─────────────────────────────────────────┘
         ↓
[Batch of RawDetections (e.g., 3) → Pending items in UI]
         ↓
[User reviews each pending card independently]
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
[Multiple aggregated items in session (e.g., 3)]
         ↓
[User taps "Enrich" on each item (optional)]
         ↓
┌─────────────────────────────────────────┐
│ For each enrichment request:           │
│   - Backend: Vision API (or cache)     │
│   - Normalize attributes               │
│   - GPT draft generation               │
│   - Return enriched data               │
└─────────────────────────────────────────┘
         ↓
[Each item updated with enhanced metadata]
```

---

## Key Differences from Single-Object

| Aspect | Single-Object | Multiple Objects |
|--------|---------------|------------------|
| **ML Kit call** | Returns 1 detection | Returns N detections (3-8 typical) |
| **CaptureId** | Unique per object | Shared across batch |
| **Thumbnails** | 1 thumbnail | N thumbnails (one per object) |
| **Bitmap copies** | 1 copy | N copies (memory intensive) |
| **Pending items** | 1 card | N cards (independently actionable) |
| **Aggregation** | Check similarity only | Check similarity + captureId |
| **Enrichment** | 1 API call | N API calls (can be concurrent) |
| **Memory usage** | ~50 MB | ~150 MB for 3 objects |

---

## Key Takeaways

1. **Single ML Kit call**: One detection pass returns all objects
2. **Shared captureId**: Prevents aggregation of objects from same photo
3. **Independent thumbnails**: Each object gets exact WYSIWYG crop
4. **Memory considerations**: N objects = N full-bitmap copies (~48 MB each)
5. **Selective confirmation**: User can confirm/delete each item independently
6. **Aggregation protection**: captureId check ensures same-batch objects stay separate
7. **Concurrent enrichment**: Multiple enrichment requests can run in parallel
8. **Cache benefits**: Vision API caching reduces duplicate requests
9. **Portrait rotation**: All bboxes/thumbnails rotated 90° for display
10. **WYSIWYG guarantee**: Each thumbnail exactly matches overlay bbox

---

## Related Files

- `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt` - Multi-object capture
- `androidApp/src/main/java/com/scanium/app/camera/CameraFrameAnalyzer.kt` - Batch detection, thumbnails
- `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` - ML Kit multi-object config
- `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt` - captureId handling
- `backend/src/routes/v1/items/enrich.ts` - Enrichment endpoint
- `backend/src/services/enrichment/pipeline.ts` - Vision API caching
