# Scanium Processing Pipeline: Camera Capture to Backend Posting

**Version**: 1.0
**Last Updated**: 2026-01-23
**Purpose**: Complete reference for the item processing flow from camera capture through backend sync

---

## Table of Contents

1. [Overview](#overview)
2. [Camera Geometry & WYSIWYG](#1-camera-geometry--wysiwyg)
3. [ML Kit Detection](#2-ml-kit-detection)
4. [Object Tracking](#3-object-tracking)
5. [Item Aggregation](#4-item-aggregation)
6. [Classification & Enrichment](#5-classification--enrichment)
7. [State Management](#6-state-management)
8. [Backend Sync](#7-backend-sync)
9. [Image Handling](#8-image-handling)
10. [Complete Data Flow](#9-complete-data-flow-diagram)

---

## Overview

Scanium processes items through an 8-stage pipeline:

```
Camera → ML Kit → Tracking → Aggregation → Classification → State → Backend → Listing
```

**Key Principles**:
- **WYSIWYG Camera**: Preview and analysis streams use identical aspect ratios
- **Progressive Enrichment**: 3-layer classification (local → cloud → LLM)
- **Permissive → Strict**: Tracking is lenient (1 frame), aggregation is strict (55% threshold)
- **Thumbnail Preservation**: Original WYSIWYG thumbnails are never replaced
- **Multi-Object Support**: Each detected item gets its own cropped thumbnail

---

## 1. Camera Geometry & WYSIWYG

**File**: `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`

### 1.1 Aspect Ratio Consistency

**Lines 496-519**: Both Preview and ImageAnalysis use `AspectRatio.RATIO_4_3`

```kotlin
preview.surfaceProvider = previewSurfaceProvider
val imageAnalysis = ImageAnalysis.Builder()
    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
    .build()
```

**Why**: Ensures WYSIWYG behavior. Previous 16:9 mismatch caused ML Kit to detect objects outside the visible preview area, confusing users.

### 1.2 Viewport Calculation

**Lines 1787-1816**: `calculateVisibleViewport()`

Determines which portion of the full camera frame is actually visible in the preview:

1. **Input**: Full frame dimensions (e.g., 640x480) + preview view dimensions (e.g., 1080x1920)
2. **Logic**: Center-crop matching CameraX's default scaling
3. **Output**: `NormalizedRect` representing visible region
4. **Usage**: Filters detections that fall outside the visible area

**Critical**: Filtering happens AFTER ML Kit analysis, not before. ML Kit processes the entire frame; we discard out-of-view detections geometrically.

### 1.3 Coordinate Systems

**Lines 584-603**: Three distinct coordinate spaces

| System | Purpose | Used For |
|--------|---------|----------|
| **Preview Stream Resolution** | User-visible area | Overlay drawing |
| **ImageAnalysis Resolution** | ML Kit input | Detection bboxes |
| **High-Res Capture** | Full quality JPEG | Enrichment, listings |

**Gotcha**: Overlay coordinates must use Preview dimensions, NOT ImageAnalysis dimensions, or bboxes will be misaligned.

### 1.4 Image Capture

**Lines 1436-1500**: `captureHighResImage()`

```kotlin
val imageSavedCallback = object : ImageCapture.OnImageSavedCallback {
    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
        // Full JPEG saved to cache
    }
}
```

**Specs**:
- Format: JPEG (quality 90)
- Resolution: 720p / 1080p / 4K (configurable)
- Naming: `SCANIUM_yyyyMMdd_HHmmss_epochMillis_uuid.jpg`
- Storage: App cache directory (`Context.cacheDir`)

---

## 2. ML Kit Detection

**File**: `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`

### 2.1 Detection Pipeline

**Lines 56-136**: `detectObjects()`

```kotlin
fun detectObjects(
    inputImage: InputImage,
    bitmapProvider: () -> Bitmap?
): Pair<List<ScannedItem>, DetectionResults>
```

**Flow**:
1. Invoke ML Kit ObjectDetector.process()
2. Filter results by confidence threshold (configurable, default 0.2)
3. Transform coordinates (handles rotation)
4. Extract WYSIWYG thumbnails (lazy bitmap provider)
5. Return both items (for list) and results (for overlay)

### 2.2 Coordinate Transformation

**Lines 86-96**: Rotation handling

ML Kit returns bboxes in **rotated** coordinate space. For 90°/270° rotations:
- Swap width/height dimensions
- Transform bbox coordinates accordingly

```kotlin
if (rotation == 90 || rotation == 270) {
    // Swap dimensions
    val rotatedWidth = inputImage.height
    val rotatedHeight = inputImage.width
}
```

**Why**: Camera sensor is physically rotated; ML Kit compensates, but we need to convert back for overlay drawing.

### 2.3 Filtering

**Lines 99-109**: Post-detection filters

1. **Edge gating**: `isDetectionInsideSafeZone()` with 10% inset margin
   - Rejects detections touching frame edges
   - Prevents partial/clipped objects

2. **Bbox validation**: Rejects oversized or extreme aspect ratios
   - Max area: 90% of frame
   - Aspect ratio: 0.1 to 10.0

3. **Confidence**: Minimum threshold (default 0.2 for permissive tracking)

### 2.4 Detection Modes

**Lines 45-50**: ObjectDetectorOptions

```kotlin
val options = ObjectDetectorOptions.Builder()
    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
    .enableMultipleObjects()
    .enableClassification()
    .build()
```

**SINGLE_IMAGE_MODE**: Used for stability
- Prevents ML Kit tracking ID churn
- More predictable results frame-to-frame
- Alternative: STREAM_MODE provides ML Kit tracking IDs but causes blinking bboxes

---

## 3. Object Tracking

**File**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ObjectTracker.kt`

### 3.1 Configuration

**Lines 936-981**: TrackerConfig

```kotlin
val config = TrackerConfig(
    minFramesToConfirm = 1,          // Immediate confirmation
    minConfidence = 0.2f,            // Very low threshold (20%)
    maxFrameGap = 8,                 // Allow 8 frames gap
    expiryFrames = 15,               // Keep candidates for 15 frames
    spatialIoUThreshold = 0.3f,      // 30% overlap for matching
    centerDistanceWeight = 0.4f      // 40% weight on distance
)
```

**Design Rationale**: Permissive tracking (1 frame confirmation) relies on ItemAggregator for quality filtering.

### 3.2 Matching Strategy

**Lines 588-669**: `findBestMatch()`

**Priority order**:
1. **Direct ML Kit tracking ID match** (preferred)
   - When available, use ML Kit's built-in tracking
   - Bypasses spatial heuristics

2. **Fallback spatial matching** (IoU + center distance)
   - IoU score: 60% weight
   - Center distance: 40% weight
   - Combined score must exceed threshold (0.3)

3. **Spatial grid index** (for >8 candidates)
   - Partitions frame into 3x3 grid
   - Only checks candidates in same/adjacent cells
   - Optimization for crowded scenes

### 3.3 Center-Weighted Selection

**Lines 108-161**: `getCenteredConfirmedObject()`

**Logic**:
1. Filter to confirmed objects (>= minFramesToConfirm)
2. Calculate center distance from frame center
3. Prioritize objects closer to center
4. Require stability: consecutive frames + time threshold
5. **High confidence override**: >0.8 confidence bypasses center distance gate

**Why**: Prioritizes items user is actively focusing on, prevents background items from stealing focus.

---

## 4. Item Aggregation

**File**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt`

### 4.1 Aggregation Strategy

**Lines 264-336**: `findExistingItemForAggregation()`

**Hard filters** (immediate rejection):
- **Timestamp diff >2 seconds**: Prevents aggregation across separate capture sessions
- **Category mismatch**: Items must have same category
- **Disabled aggregation**: Recent commit `96609c9a` disabled this by default

**Similarity score** (weighted blend):
```
Total Score = Category(30%) + Label(25%) + Size(20%) + Center(25%)
```

| Component | Weight | Calculation |
|-----------|--------|-------------|
| Category | 30% | 1.0 if match, 0.0 otherwise |
| Label | 25% | 1.0 - (Levenshtein distance / max length) |
| Size Ratio | 20% | 1.0 - abs(sizeA - sizeB) |
| Center Distance | 25% | 1.0 - (distance / maxCenterDist) |

**Threshold**: Must exceed `aggregationThreshold` to merge (default 0.55 for REALTIME preset)

### 4.2 Spatial-Temporal Fallback

**Lines 74-95**: Handles tracker ID churn

**Problem**: ML Kit sometimes generates new tracking IDs for same object across captures.

**Solution**:
- Maintain lightweight candidate metadata cache
- When regular similarity fails, check spatial-temporal proximity
- If object reappears in same location within 2 seconds, treat as same item

### 4.3 Aggregation Presets

**Lines 553-671**: Predefined configurations

| Preset | Threshold | Max Center Dist | Use Case |
|--------|-----------|-----------------|----------|
| REALTIME | 0.55 | 0.30 | Default (used in ItemsViewModel) |
| BALANCED | 0.60 | 0.25 | Medium strictness |
| STRICT | 0.75 | 0.15 | Requires label match |

**Current**: REALTIME preset used in `ItemsViewModel.kt`

**Note**: As of commit `96609c9a`, aggregation is disabled entirely. Each capture creates a unique item.

---

## 5. Classification & Enrichment

Scanium uses a **3-layer progressive enrichment pipeline**:

```
Layer A (Local) → Layer B (Cloud Insights) → Layer C (Full Enrichment)
   ~100ms              ~1-2s                       ~5-15s
```

### 5.1 Layer A: Local Extraction

**File**: `androidApp/src/main/java/com/scanium/app/ml/LocalVisionExtractor.kt`

**Technology**:
- ML Kit Text Recognition (OCR)
- Android Palette API (dominant colors)

**Latency**: ~100-200ms

**Output**:
- Detected text lines
- Dominant colors (RGB hex)
- Confidence scores

**Applied**: Immediately for instant feedback in UI

### 5.2 Layer B: Cloud Insights

**File**: `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsPrefiller.kt`

**Endpoint**: `POST /v1/vision/insights`

**Capabilities**:
- Logo detection (brand identification)
- Better object categorization
- Text localization improvements

**Merge Strategy**: Cloud results take precedence over local when available

### 5.3 Layer C: Full Enrichment

**File**: `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsPrefiller.kt` (lines 336-387)

**Endpoint**: `POST /v1/items/enrich`

**Pipeline**:
1. **Check enrichment policy**: Budget limits, completeness thresholds
2. **Submit multipart request**: Image + metadata JSON
3. **Poll for completion**: Exponential backoff (1s, 2s, 4s, ...)
4. **Apply results**: Normalized attributes + LLM-generated draft

**Latency**: ~5-15 seconds

**Output**:
- Title draft
- Description draft
- Bullet points
- Normalized attributes (key-value pairs with confidence)
- Vision facts (logos, colors, text)

### 5.4 Crop-Based Enrichment

**File**: `androidApp/src/main/java/com/scanium/app/ml/CropBasedEnricher.kt`

**Problem**: Multi-object scenes confuse classifiers (shared brands/labels)

**Solution**: Crop-based strategy (lines 68-230)

1. **Crop bbox from high-res image**
2. **Run Vision on CROP first**
3. **If weak results**: Expand crop (+25% padding) OR use full image
4. **Adaptive padding based on bbox area**:
   - Large objects (>50% frame): 5% padding
   - Medium objects (20-50%): 10-15% padding
   - Small objects (<20%): 25% padding

**Rationale**: Ensures logos/text on individual items are detected even when full image contains multiple objects.

---

## 6. State Management

**File**: `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt`

### 6.1 Architecture: Facade Pattern

**Lines 69-1112**: ItemsViewModel delegates to specialized managers

```
ItemsViewModel (Thin Facade)
├── ItemsStateManager (StateFlow, CRUD, persistence)
├── ItemClassificationCoordinator (Classification, retry, cloud gating)
├── OverlayTrackManager (Camera overlay state)
└── ListingStatusManager (eBay posting status)
```

**Why**: Separation of concerns, testability, maintainability

### 6.2 Multi-Hypothesis Classification

**Lines 693-1003**: Pending detection flow

**States**:
```kotlin
sealed class PendingDetectionState {
    object None                           // No pending detections
    data class AwaitingClassification     // Waiting for cloud response
    data class ShowingHypotheses          // Displaying bottom sheet
}
```

**Flow**:
1. Detection enters pending queue (lines 813-838)
2. WYSIWYG thumbnail sent to cloud classifier
3. Multi-hypothesis results displayed in bottom sheet
4. **Auto-commit**: High confidence (>0.9, single hypothesis) skips user confirmation
5. User confirms/dismisses → creates ScannedItem

**UI**: Bottom sheet with:
- Hypothesis options (category + confidence)
- Thumbnail preview
- Confirm/Dismiss buttons

### 6.3 StateFlow Updates

**Pattern**: Reactive UI updates via Compose

```kotlin
val items: StateFlow<List<ScannedItem>> = itemsStateManager.items
```

**Compose**: `collectAsState()` triggers recomposition on changes

---

## 7. Backend Sync

### 7.1 Items Sync API

**File**: `androidApp/src/main/java/com/scanium/app/items/network/ItemsApi.kt`

**Endpoints** (lines 243-479):

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | /v1/items | Fetch items modified since timestamp |
| POST | /v1/items | Create new item |
| PATCH | /v1/items/:id | Update with optimistic locking |
| DELETE | /v1/items/:id | Soft delete |
| POST | /v1/items/sync | Batch sync with conflict resolution |

### 7.2 ItemDto Structure

**Lines 31-88**: Complete item payload

**Core fields**:
- `title`, `description`, `category`
- `confidence`, `lowEstimate`, `highEstimate`

**JSON fields** (serialized):
- `attributes`: Key-value attributes from ML
- `visionAttributes`: Cloud vision insights
- `enrichmentStatus`: Tracking enrichment progress

**Quality metrics**:
- `completenessScore`: 0.0-1.0 indicating readiness
- `missingAttributes`: List of missing required fields
- `readyForListing`: Boolean derived from completeness

**Export fields**:
- `exportTitle`, `exportDescription`, `exportBullets`
- Generated by LLM during enrichment

**Classification tracking**:
- `classificationStatus`: pending, success, failed
- `domainCategoryId`: Link to Domain Pack category
- `classificationError`: Error message if failed

**Photo metadata**:
- `photos`: JSON array of photo metadata
  - `type`: thumbnail, full, etc.
  - `hash`: SHA256 for deduplication
  - `width`, `height`: Dimensions

**Multi-object support**:
- `sourcePhotoId`: Links items from same capture

**Listing integration**:
- `listingStatus`: draft, listed, sold, etc.
- `ebayListingId`, `ebayListingUrl`

### 7.3 Optimistic Locking

**Pattern**: `syncVersion` field

1. Client reads item with `syncVersion = 5`
2. Client modifies locally, increments to `syncVersion = 6`
3. Client PATCH with `syncVersion = 6`
4. **Conflict**: Server has `syncVersion = 7` (modified elsewhere)
5. Server rejects with 409 Conflict
6. Client refetches, merges, retries

**Why**: Prevents lost updates in multi-device scenarios

### 7.4 Enrichment API

**File**: `backend/src/modules/enrich/routes.ts`

**Endpoints** (lines 80-282):

#### POST /v1/items/enrich (line 86)

**Request**: Multipart form
- `image`: JPEG file (high-res capture)
- `metadata`: JSON with category, confidence, etc.

**Response**: 202 Accepted
```json
{
  "requestId": "uuid",
  "status": "pending"
}
```

**Auth**: HMAC signature in `X-Scanium-Signature` header

#### GET /v1/items/enrich/status/:requestId (line 214)

**Response**:
```json
{
  "stage": "vision" | "normalization" | "draft" | "complete",
  "visionFacts": { ... },
  "normalizedAttributes": [ ... ],
  "draft": {
    "title": "...",
    "description": "...",
    "bullets": [ ... ]
  }
}
```

**Polling**: Client uses exponential backoff (1s, 2s, 4s, ...)

### 7.5 Backend Enrichment Pipeline

**File**: `backend/src/modules/enrich/service.ts`

**Stages**:

1. **Vision extraction** (Google Vision API)
   - Logo detection
   - Text recognition
   - Label detection
   - Color analysis

2. **Attribute normalization**
   - Structured key-value pairs
   - Confidence scores
   - Domain Pack attribute mapping

3. **LLM draft generation** (OpenAI)
   - Title (short, descriptive)
   - Description (paragraph)
   - Bullet points (5-7 key features)

4. **Provenance tracking**
   - Links attributes to source (vision, ml, llm)
   - Confidence scores preserved

---

## 8. Image Handling

### 8.1 Image Types Throughout Pipeline

| Type | Format | Resolution | Created In | Used For |
|------|--------|------------|------------|----------|
| **Preview Frames** | YUV_420_888 | ~480p-720p (4:3) | CameraXManager | Real-time ML Kit detection |
| **WYSIWYG Thumbnails** | Bitmap → PNG | Varies (cropped) | ObjectDetectorClient | Per-item classification, UI display |
| **High-Res Captures** | JPEG (quality 90) | 1080p-4K | CameraXManager | Enrichment, listing photos |
| **Cropped Enrichment** | JPEG | Varies (bbox crop) | CropBasedEnricher | Cloud Vision, LLM enrichment |

### 8.2 WYSIWYG Thumbnail Creation

**File**: `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt` (lines 86-96)

**Critical fix**: Coordinate transformation for rotation

```kotlin
// For 90°/270° rotation, swap width/height before cropping
val thumbnailCrop = if (rotation == 90 || rotation == 270) {
    // Transform bbox coordinates
    swapDimensions(bbox)
} else {
    bbox
}
```

**Why**: Camera sensor physically rotated; ML Kit compensates, but cropping needs original orientation.

### 8.3 Thumbnail Preservation Rules

**File**: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt` (line 79)

**Comment**: "NEVER replace original WYSIWYG thumbnails"

**Recent commits**:
- `79257159`: "fix: preserve original thumbnails during item aggregation"
- `18066fe4`: "fix: prevent classification from replacing original WYSIWYG thumbnails"

**Rationale**: Original thumbnail shows exactly what user saw at moment of capture. Replacing it breaks WYSIWYG contract.

### 8.4 Multi-Object Crop Strategy

**File**: `androidApp/src/main/java/com/scanium/app/ml/CropBasedEnricher.kt` (lines 68-230)

**Adaptive padding**:

```kotlin
val padding = when {
    bboxArea > 0.5f -> 0.05f  // Large object: 5% padding
    bboxArea > 0.2f -> 0.10f  // Medium object: 10% padding
    else -> 0.25f              // Small object: 25% padding
}
```

**Fallback strategy**:
1. Try crop with adaptive padding
2. If weak results (confidence <0.5): Expand padding to 25%
3. If still weak: Use full image instead

**Why**: Balances context (full image) vs. specificity (crop). Small items need context; large items dominate frame.

---

## 9. Complete Data Flow Diagram

```
┌────────────────────────────────────────────────────────────────────┐
│ STAGE 1: CAMERA CAPTURE                                            │
│                                                                    │
│ CameraXManager.kt:496-519                                          │
│   ├─ Preview (4:3 aspect ratio)                                    │
│   └─ ImageAnalysis (4:3 aspect ratio)                              │
│       └─ ImageProxy (YUV_420_888) → InputImage                     │
│                                                                    │
│ CameraXManager.kt:1436-1500                                        │
│   └─ captureHighResImage() → JPEG (1080p-4K)                       │
└────────────────────────┬───────────────────────────────────────────┘
                         ▼
┌────────────────────────────────────────────────────────────────────┐
│ STAGE 2: ML KIT DETECTION                                          │
│                                                                    │
│ ObjectDetectorClient.kt:56-136                                     │
│   ├─ detectObjects(InputImage, bitmapProvider)                     │
│   ├─ ML Kit ObjectDetector.process()                               │
│   │    └─ SINGLE_IMAGE_MODE (stable tracking)                      │
│   ├─ Filter by confidence (min 0.2)                                │
│   ├─ Edge filtering (10% inset) :99-109                            │
│   ├─ Coordinate transformation :86-96                              │
│   │    └─ Handle 90°/270° rotation (swap width/height)             │
│   └─ Extract WYSIWYG thumbnails (crop bbox from bitmap)            │
│       └─ Returns: List<ScannedItem> + DetectionResults             │
└────────────────────────┬───────────────────────────────────────────┘
                         ▼
┌────────────────────────────────────────────────────────────────────┐
│ STAGE 3: OBJECT TRACKING                                           │
│                                                                    │
│ ObjectTracker.kt:588-669                                           │
│   ├─ findBestMatch()                                               │
│   │    ├─ Priority 1: ML Kit tracking ID match                     │
│   │    ├─ Priority 2: Spatial matching (IoU 60% + distance 40%)    │
│   │    └─ Spatial grid index (>8 candidates)                       │
│   │                                                                │
│   ├─ Config :936-981                                               │
│   │    ├─ minFramesToConfirm = 1 (immediate)                       │
│   │    ├─ minConfidence = 0.2f (permissive)                        │
│   │    ├─ maxFrameGap = 8                                          │
│   │    └─ expiryFrames = 15                                        │
│   │                                                                │
│   └─ getCenteredConfirmedObject() :108-161                         │
│       ├─ Center-weighted selection                                 │
│       ├─ Prioritize objects near frame center                      │
│       └─ High confidence override (>0.8)                           │
└────────────────────────┬───────────────────────────────────────────┘
                         ▼
┌────────────────────────────────────────────────────────────────────┐
│ STAGE 4: ITEM AGGREGATION                                          │
│                                                                    │
│ ItemAggregator.kt:264-336                                          │
│   ├─ findExistingItemForAggregation()                              │
│   │    ├─ Hard filters:                                            │
│   │    │    ├─ Timestamp diff >2s → reject                         │
│   │    │    └─ Category mismatch → reject                          │
│   │    │                                                           │
│   │    ├─ Similarity score (weighted):                             │
│   │    │    ├─ Category: 30%                                       │
│   │    │    ├─ Label (Levenshtein): 25%                            │
│   │    │    ├─ Size ratio: 20%                                     │
│   │    │    └─ Center distance: 25%                                │
│   │    │                                                           │
│   │    └─ Threshold: 0.55 (REALTIME preset)                        │
│   │                                                                │
│   ├─ Spatial-temporal fallback :74-95                              │
│   │    └─ Handle tracker ID churn                                  │
│   │                                                                │
│   └─ NOTE: Currently DISABLED (commit 96609c9a)                    │
│       └─ Each capture creates unique item                          │
└────────────────────────┬───────────────────────────────────────────┘
                         ▼
┌────────────────────────────────────────────────────────────────────┐
│ STAGE 5: CLASSIFICATION & ENRICHMENT (3 Layers)                    │
│                                                                    │
│ ┌──────────────────────────────────────────────────────────────┐   │
│ │ LAYER A: LOCAL EXTRACTION (~100ms)                           │   │
│ │ LocalVisionExtractor.kt                                      │   │
│ │   ├─ ML Kit Text Recognition (OCR)                           │   │
│ │   └─ Android Palette API (dominant colors)                   │   │
│ └──────────────────────────────────────────────────────────────┘   │
│                         ▼                                          │
│ ┌──────────────────────────────────────────────────────────────┐   │
│ │ LAYER B: CLOUD INSIGHTS (~1-2s)                              │   │
│ │ VisionInsightsPrefiller.kt                                   │   │
│ │   └─ POST /v1/vision/insights                                │   │
│ │       ├─ Logo detection                                      │   │
│ │       ├─ Better categorization                               │   │
│ │       └─ Merge with local (cloud takes precedence)           │   │
│ └──────────────────────────────────────────────────────────────┘   │
│                         ▼                                          │
│ ┌──────────────────────────────────────────────────────────────┐   │
│ │ LAYER C: FULL ENRICHMENT (~5-15s)                            │   │
│ │ VisionInsightsPrefiller.kt:336-387                           │   │
│ │   └─ POST /v1/items/enrich                                   │   │
│ │       ├─ 202 Accepted → requestId                            │   │
│ │       ├─ Poll /v1/items/enrich/status/:requestId             │   │
│ │       │    └─ Exponential backoff (1s, 2s, 4s, ...)          │   │
│ │       │                                                      │   │
│ │       └─ Results:                                            │   │
│ │           ├─ Normalized attributes (key-value + confidence)  │   │
│ │           ├─ Vision facts (logos, colors, text)              │   │
│ │           └─ LLM draft (title, description, bullets)         │   │
│ └──────────────────────────────────────────────────────────────┘   │
│                                                                    │
│ CropBasedEnricher.kt:68-230                                        │
│   ├─ Multi-object handling: Crop bbox from high-res image          │
│   ├─ Adaptive padding based on bbox area:                          │
│   │    ├─ Large (>50%): 5% padding                                 │
│   │    ├─ Medium (20-50%): 10-15% padding                          │
│   │    └─ Small (<20%): 25% padding                                │
│   └─ Fallback: Expand crop or use full image if weak results       │
└────────────────────────┬───────────────────────────────────────────┘
                         ▼
┌────────────────────────────────────────────────────────────────────┐
│ STAGE 6: STATE MANAGEMENT                                          │
│                                                                    │
│ ItemsViewModel.kt:69-1112                                          │
│   ├─ Facade pattern (delegates to managers)                        │
│   │    ├─ ItemsStateManager (StateFlow, CRUD, persistence)         │
│   │    ├─ ItemClassificationCoordinator (classification, retry)    │
│   │    ├─ OverlayTrackManager (camera overlay state)               │
│   │    └─ ListingStatusManager (eBay posting status)               │
│   │                                                                │
│   ├─ Multi-hypothesis classification :693-1003                     │
│   │    ├─ PendingDetectionState:                                   │
│   │    │    ├─ None                                                │
│   │    │    ├─ AwaitingClassification                              │
│   │    │    └─ ShowingHypotheses (bottom sheet)                    │
│   │    │                                                           │
│   │    ├─ Flow:                                                    │
│   │    │    ├─ Detection → pending queue :813-838                  │
│   │    │    ├─ WYSIWYG thumbnail → cloud classifier                │
│   │    │    ├─ Multi-hypothesis results → bottom sheet             │
│   │    │    ├─ Auto-commit if high confidence (>0.9, single)       │
│   │    │    └─ User confirms/dismisses → ScannedItem created       │
│   │    │                                                           │
│   │    └─ StateFlow updates → Compose recomposition                │
│   │                                                                │
│   └─ val items: StateFlow<List<ScannedItem>>                       │
│       └─ UI: items.collectAsState()                                │
└────────────────────────┬───────────────────────────────────────────┘
                         ▼
┌────────────────────────────────────────────────────────────────────┐
│ STAGE 7: BACKEND SYNC                                              │
│                                                                    │
│ ItemsApi.kt:243-479                                                │
│   ├─ POST /v1/items (create)                                       │
│   │    └─ ItemDto payload :31-88                                   │
│   │        ├─ Core: title, description, category, confidence       │
│   │        ├─ JSON: attributes, visionAttributes, enrichmentStatus │
│   │        ├─ Quality: completenessScore, missingAttributes        │
│   │        ├─ Export: exportTitle, exportDescription, exportBullets│
│   │        ├─ Photos: metadata JSON (type, hash, dimensions)       │
│   │        ├─ Multi-object: sourcePhotoId (links items)            │
│   │        └─ Listing: status, ebayListingId, ebayListingUrl       │
│   │                                                                │
│   ├─ PATCH /v1/items/:id (update)                                  │
│   │    └─ Optimistic locking via syncVersion                       │
│   │        ├─ Client increments syncVersion                        │
│   │        ├─ Server validates against current version             │
│   │        └─ 409 Conflict if mismatch → refetch + merge           │
│   │                                                                │
│   ├─ GET /v1/items (fetch modified since timestamp)                │
│   ├─ DELETE /v1/items/:id (soft delete)                            │
│   └─ POST /v1/items/sync (batch sync + conflict resolution)        │
│                                                                    │
│ Backend: backend/src/modules/enrich/routes.ts:80-282               │
│   ├─ POST /v1/items/enrich :86                                     │
│   │    ├─ Multipart: image + metadata JSON                         │
│   │    ├─ Auth: HMAC signature (X-Scanium-Signature)               │
│   │    └─ Response: 202 Accepted + requestId                       │
│   │                                                                │
│   ├─ GET /v1/items/enrich/status/:requestId :214                   │
│   │    └─ Returns: stage, visionFacts, normalizedAttributes, draft │
│   │                                                                │
│   └─ Enrichment pipeline (backend/src/modules/enrich/service.ts):  │
│       ├─ Stage 1: Google Vision API extraction                     │
│       ├─ Stage 2: Attribute normalization (key-value + confidence) │
│       ├─ Stage 3: LLM draft generation (OpenAI)                    │
│       └─ Stage 4: Provenance tracking (source + confidence)        │
└────────────────────────┬───────────────────────────────────────────┘
                         ▼
┌────────────────────────────────────────────────────────────────────┐
│ STAGE 8: READY FOR LISTING                                         │
│                                                                    │
│ Item with:                                                         │
│   ├─ Original WYSIWYG thumbnail (preserved)                        │
│   ├─ High-res capture image                                        │
│   ├─ Normalized attributes                                         │
│   ├─ LLM-generated draft (title, description, bullets)             │
│   ├─ Completeness score                                            │
│   └─ Ready for eBay/Mercari/etc. posting                           │
└────────────────────────────────────────────────────────────────────┘
```

---

## Key Design Principles

### 1. WYSIWYG is Sacred
- Preview and ImageAnalysis both use 4:3 aspect ratio
- Viewport filtering ensures only visible detections are shown
- Original thumbnails are NEVER replaced (see commits `79257159`, `18066fe4`)

### 2. Progressive Enhancement
- Layer A (local): Instant feedback (~100ms)
- Layer B (cloud): Better accuracy (~1-2s)
- Layer C (LLM): Full listing draft (~5-15s)
- Users see results progressively, not blocked on slowest step

### 3. Permissive → Strict Pipeline
- ObjectTracker: Lenient (1 frame confirmation, 0.2 confidence)
- ItemAggregator: Strict (0.55 similarity threshold)
- Rationale: Better to show candidates and let aggregation filter quality

### 4. Multi-Object Support
- Each detected item gets its own cropped thumbnail
- Prevents shared brand/label confusion in multi-object scenes
- Crop-based enrichment with adaptive padding

### 5. Conflict Resolution
- Optimistic locking via `syncVersion` field
- Prevents lost updates in multi-device scenarios
- Client refetches on conflict, merges, retries

### 6. Thumbnail Preservation
- Original WYSIWYG thumbnail shows exactly what user saw at capture
- Never replaced during classification/enrichment
- Maintains user trust and WYSIWYG contract

---

## Performance Characteristics

| Stage | Latency | Blocking | Notes |
|-------|---------|----------|-------|
| Camera Capture | <100ms | No | Asynchronous capture callback |
| ML Kit Detection | ~50-150ms | Yes | Frame-by-frame, blocks next frame |
| Object Tracking | <10ms | Yes | In-memory state management |
| Item Aggregation | <5ms | Yes | Lightweight similarity scoring |
| Layer A (Local) | ~100-200ms | No | Async, immediate UI update |
| Layer B (Cloud) | ~1-2s | No | Async, progressive merge |
| Layer C (Enrichment) | ~5-15s | No | Async polling, optional |
| Backend Sync | ~200-500ms | No | Async, optimistic updates |

**Critical path**: Camera → ML Kit → Tracking → Aggregation (~200ms total)

**Non-critical**: Classification layers run asynchronously, progressive results

---

## Error Handling

### Detection Failures
- **ML Kit error**: Log + skip frame (silent failure, resume next frame)
- **Out of bounds bbox**: Reject during filtering

### Classification Failures
- **Layer A failure**: Proceed without local attributes
- **Layer B failure**: Retry with exponential backoff (3 attempts)
- **Layer C failure**: Mark `classificationStatus = failed`, store error message

### Backend Sync Failures
- **Network error**: Queue for retry (exponential backoff)
- **409 Conflict**: Refetch, merge, retry once
- **Other errors**: Display error toast, keep local state

---

## Testing Strategy

### Unit Tests
- `ObjectTrackerTest.kt`: Matching logic, center-weighted selection
- `ItemAggregatorTest.kt`: Similarity scoring, spatial-temporal fallback
- `VisionInsightsPrefillerTest.kt`: Merge logic, layer precedence

### Instrumented Tests
- `CameraXManagerTest.kt`: Aspect ratio consistency, coordinate transforms
- `ItemsViewModelTest.kt`: Multi-hypothesis flow, state transitions

### Integration Tests
- `backend/test/enrich.test.ts`: Full enrichment pipeline
- `backend/test/items.test.ts`: Sync, conflict resolution

---

## Common Pitfalls

### 1. Coordinate Space Confusion
**Problem**: Overlay bboxes misaligned
**Solution**: Use Preview stream dimensions, NOT ImageAnalysis dimensions

### 2. Thumbnail Replacement
**Problem**: Classification replaces original WYSIWYG thumbnail
**Solution**: See commit `18066fe4` - never replace, always preserve

### 3. Aspect Ratio Mismatch
**Problem**: ML Kit detects objects outside visible preview
**Solution**: Both Preview and ImageAnalysis must use same aspect ratio (4:3)

### 4. Multi-Object Brand Confusion
**Problem**: Full image classification assigns shared brand to all items
**Solution**: Use crop-based enrichment (CropBasedEnricher.kt)

### 5. Sync Version Conflicts
**Problem**: Lost updates in multi-device scenario
**Solution**: Optimistic locking via `syncVersion`, handle 409 Conflict

---

## Recent Changes (Git History)

| Commit | Date | Change |
|--------|------|--------|
| `fe01907a` | Recent | Increase scan zone ROI from 65% to 80% for better UX |
| `a2cc561f` | Recent | Disable long press scanning on camera shutter |
| `96609c9a` | Recent | Disable item aggregation - each capture creates unique item |
| `79257159` | Recent | Fix: prevent classification from replacing WYSIWYG thumbnails |
| `18066fe4` | Recent | Fix: preserve original thumbnails during aggregation |

---

## References

### Key Files
- Camera: `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`
- Detection: `androidApp/src/main/java/com/scanium/app/ml/ObjectDetectorClient.kt`
- Tracking: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ObjectTracker.kt`
- Aggregation: `shared/core-tracking/src/commonMain/kotlin/com/scanium/core/tracking/ItemAggregator.kt`
- Classification: `androidApp/src/main/java/com/scanium/app/ml/VisionInsightsPrefiller.kt`
- State: `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt`
- Sync: `androidApp/src/main/java/com/scanium/app/items/network/ItemsApi.kt`
- Backend: `backend/src/modules/enrich/routes.ts`

### Configuration
- Tracker: `minFramesToConfirm=1, minConfidence=0.2f`
- Aggregator: `AggregationPresets.REALTIME` (threshold 0.55)
- Camera: `AspectRatio.RATIO_4_3` for Preview + ImageAnalysis
- Enrichment: HMAC auth, exponential backoff polling

---

**Document Version**: 1.0
**Last Updated**: 2026-01-23
**Maintained By**: AI Assistant + Dev Team
**Next Review**: On architectural changes to pipeline
