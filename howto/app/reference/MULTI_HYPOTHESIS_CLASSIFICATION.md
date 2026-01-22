***REMOVED*** Multi-Hypothesis Classification Implementation

**Status**: ✅ Complete (Phases 1-4)
**Date**: January 2025
**Commit**: dc404254

***REMOVED******REMOVED*** Table of Contents

1. [Overview](***REMOVED***overview)
2. [Problem Statement](***REMOVED***problem-statement)
3. [Solution Architecture](***REMOVED***solution-architecture)
4. [Implementation Phases](***REMOVED***implementation-phases)
5. [File Reference](***REMOVED***file-reference)
6. [Data Flow](***REMOVED***data-flow)
7. [Edge Cases & Error Handling](***REMOVED***edge-cases--error-handling)
8. [Testing & Verification](***REMOVED***testing--verification)
9. [Future Enhancements](***REMOVED***future-enhancements)

---

***REMOVED******REMOVED*** Overview

The multi-hypothesis classification system transforms Scanium's object detection workflow from a **premature commitment** model to a **collaborative confirmation** model. Instead of immediately creating items with potentially incorrect classifications, the system now:

1. Holds detections in a **pending state**
2. Requests **multiple ranked hypotheses** from the backend
3. Presents **3 classification options** to the user
4. Creates items only after **explicit user confirmation**
5. Auto-commits **high-confidence** detections with brief visual confirmation

This implementation eliminates the perception of "the app messed up" by ensuring users are part of the decision-making process before items are committed to the database.

---

***REMOVED******REMOVED*** Problem Statement

***REMOVED******REMOVED******REMOVED*** The Premature Commitment Problem

**Before this implementation:**

```
Timeline:
T+0ms   : User captures object
T+10ms  : ScannedItem created and visible in UI
T+2000ms: Backend classification completes
T+2001ms: Item label updated in place
```

**Issues:**
1. **Trust Erosion**: Users see item appear with wrong label, then watch it change
2. **Correction Mindset**: Feels like correcting mistakes rather than confirming AI suggestions
3. **Disconnected Workflow**: Classification result has no visual connection to the original proposal
4. **No Exploration**: Users can't see alternative classifications or reasoning

***REMOVED******REMOVED******REMOVED*** The Collaborative Solution

**After this implementation:**

```
Timeline:
T+0ms   : User captures object
T+10ms  : RawDetection created (not visible to user)
T+2000ms: Backend returns 3 ranked hypotheses
T+2001ms: Modal sheet appears showing options
T+????ms: User taps to confirm (or auto-confirm after 700ms if HIGH confidence)
T+????ms: ScannedItem created with confirmed label
```

**Benefits:**
1. **Trust Building**: User confirms before commitment
2. **Collaborative**: AI proposes, user decides
3. **Transparency**: Multiple options with explanations
4. **Efficiency**: High-confidence items auto-confirm with visual feedback

---

***REMOVED******REMOVED*** Solution Architecture

***REMOVED******REMOVED******REMOVED*** Core Components

```
┌─────────────────────────────────────────────────────────────────┐
│                    Camera Detection Layer                        │
│  CameraFrameAnalyzer → ObjectDetector → RawDetection (pending)  │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    State Management Layer                        │
│        ItemsViewModel (pending queue, handlers, routing)        │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Backend Classification Layer                    │
│    CloudClassifier → CloudClassifierApi (mode=multi-hypothesis) │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                        UI Presentation Layer                     │
│  HypothesisSelectionSheet → User Confirmation → ScannedItem     │
└─────────────────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** Data Models

***REMOVED******REMOVED******REMOVED******REMOVED*** PendingDetectionState (Sealed Class)

```kotlin
sealed class PendingDetectionState {
    /** No pending detections */
    object None : PendingDetectionState()

    /** Detection received, waiting for backend classification */
    data class AwaitingClassification(
        val detectionId: String,
        val rawDetection: RawDetection,
        val thumbnailRef: ImageRef?,
        val timestamp: Long
    ) : PendingDetectionState()

    /** Backend returned hypotheses, showing to user */
    data class ShowingHypotheses(
        val detectionId: String,
        val rawDetection: RawDetection,
        val hypothesisResult: MultiHypothesisResult,
        val thumbnailUri: Uri?
    ) : PendingDetectionState()
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** RawDetection (Lightweight Detection Data)

```kotlin
data class RawDetection(
    val boundingBox: NormalizedRect?,
    val confidence: Float,
    val onDeviceLabel: String,
    val onDeviceCategory: ItemCategory,
    val trackingId: String?,
    val frameSharpness: Float,
    val captureType: CaptureType,
    val fullFrameBitmap: Bitmap? = null  // For cloud classification
)
```

***REMOVED******REMOVED******REMOVED******REMOVED*** ClassificationHypothesis (Single Option)

```kotlin
data class ClassificationHypothesis(
    val categoryId: String,
    val categoryName: String,
    val explanation: String,
    val confidence: Float,
    val confidenceBand: String = "MED",  // "HIGH", "MED", "LOW"
    val attributes: Map<String, String> = emptyMap()
)
```

***REMOVED******REMOVED******REMOVED******REMOVED*** MultiHypothesisResult (Backend Response)

```kotlin
data class MultiHypothesisResult(
    val hypotheses: List<ClassificationHypothesis>,
    val globalConfidence: Float,
    val needsRefinement: Boolean,
    val requestId: String
)
```

---

***REMOVED******REMOVED*** Implementation Phases

***REMOVED******REMOVED******REMOVED*** Phase 1: Pending Detection State (Foundation)

**Goal**: Prevent immediate ScannedItem creation; introduce pending state.

***REMOVED******REMOVED******REMOVED******REMOVED*** Changes Made

**1. PendingDetectionState.kt** (NEW FILE)
- Location: `androidApp/src/main/java/com/scanium/app/items/PendingDetectionState.kt`
- Purpose: Define sealed class hierarchy for pending detection lifecycle
- Key Types:
  - `PendingDetectionState` (sealed): None | AwaitingClassification | ShowingHypotheses
  - `RawDetection` (data): Lightweight detection info without commitment
  - `CaptureType` (enum): SINGLE_SHOT | TRACKING

**2. ItemsViewModel.kt** (Major Update)
- Location: `androidApp/src/main/java/com/scanium/app/items/ItemsViewModel.kt`
- Lines Added: ~200
- Key Additions:

```kotlin
// State flows for pending detections
private val _pendingDetections = MutableStateFlow<List<PendingDetectionState>>(emptyList())
val pendingDetectionCount: StateFlow<Int> = _pendingDetections.map { it.size }
val currentPendingDetection: StateFlow<PendingDetectionState> =
    _pendingDetections.map { it.firstOrNull() ?: PendingDetectionState.None }

// Handlers
fun onDetectionReady(rawDetection: RawDetection)
fun confirmPendingDetection(detectionId: String, hypothesis: ClassificationHypothesis)
fun dismissPendingDetection(detectionId: String)
private suspend fun triggerMultiHypothesisClassification(detectionId: String, rawDetection: RawDetection)
private suspend fun createItemFromDetection(detectionId: String, rawDetection: RawDetection, hypothesis: ClassificationHypothesis?)
```

**3. CameraFrameAnalyzer.kt** (Return Type Changes)
- Location: `androidApp/src/main/java/com/scanium/app/camera/CameraFrameAnalyzer.kt`
- Changed: `processObjectDetectionWithTracking()` return type from `List<ScannedItem>` to `List<RawDetection>`
- Changed: `processObjectDetectionMode()` return type
- Key Change at line ~452 (tracking mode):

```kotlin
// BEFORE:
val item = objectDetector.candidateToScannedItem(candidate)
objectTracker.markCandidateConsumed(candidate.internalId)
item

// AFTER:
val rawDetection = RawDetection(
    boundingBox = candidate.boundingBox,
    confidence = candidate.maxConfidence,
    onDeviceLabel = candidate.labelText.takeIf { it.isNotBlank() } ?: "Unknown",
    onDeviceCategory = candidate.category,
    trackingId = candidate.internalId,
    frameSharpness = frameSharpness,
    captureType = CaptureType.TRACKING,
    fullFrameBitmap = fullFrameBitmap
)
objectTracker.markCandidateConsumed(candidate.internalId)
rawDetection
```

**4. CameraXManager.kt** (Function Signature Updates)
- Location: `androidApp/src/main/java/com/scanium/app/camera/CameraXManager.kt`
- Changed: `captureSingleFrame()` callback from `(List<ScannedItem>) -> Unit` to `(List<RawDetection>) -> Unit`
- Changed: `startScanning()` callback similarly

**5. CameraScreen.kt** (Callback Routing)
- Location: `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`
- Changed: Capture callbacks to route RawDetections to ItemsViewModel

```kotlin
// BEFORE:
itemsViewModel.addItemsFromMultiObjectCapture(context, captureResult)

// AFTER:
rawDetections.forEach { rawDetection ->
    itemsViewModel.onDetectionReady(rawDetection)
}
soundManager.play(AppSound.CAPTURE)
```

---

***REMOVED******REMOVED******REMOVED*** Phase 2: Backend Multi-Hypothesis Integration

**Goal**: Request and parse multi-hypothesis responses from backend.

***REMOVED******REMOVED******REMOVED******REMOVED*** Changes Made

**1. CloudClassifierApi.kt** (Mode Parameter)
- Location: `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifierApi.kt`
- Line 78: Added `mode` parameter to `classify()` method

```kotlin
suspend fun classify(
    bitmap: Bitmap,
    config: CloudClassifierConfig,
    mode: String = "single",  // NEW: "single" or "multi-hypothesis"
    recentCorrections: String? = null,
    onAttempt: (suspend (Int, ApiError?) -> Unit)? = null,
): ApiResult
```

- Line 86: Updated endpoint URL

```kotlin
val endpoint = "${config.baseUrl.trimEnd('/')}/v1/classify?mode=$mode&enrichAttributes=true"
```

**2. CloudClassifierApi.kt** (Response Models)
- Lines 370-398: Added multi-hypothesis response models

```kotlin
@Serializable
data class CloudClassificationResponse(
    // Existing single-hypothesis fields
    val domainCategoryId: String? = null,
    val confidence: Float? = null,
    val label: String? = null,

    // NEW: Multi-hypothesis fields
    val hypotheses: List<HypothesisResponse>? = null,
    val globalConfidence: Float? = null,
    val needsRefinement: Boolean? = null,
    val provider: String? = null,
)

@Serializable
data class HypothesisResponse(
    val categoryId: String,
    val categoryName: String,
    val explanation: String,
    val confidence: Float,
    val confidenceBand: String,
    val attributes: Map<String, String> = emptyMap(),
)
```

**3. CloudClassifier.kt** (Multi-Hypothesis Method)
- Location: `androidApp/src/main/java/com/scanium/app/ml/classification/CloudClassifier.kt`
- Lines 230-270: Added `classifyMultiHypothesis()` method

```kotlin
suspend fun classifyMultiHypothesis(bitmap: Bitmap): MultiHypothesisResult? =
    withContext(Dispatchers.IO) {
        val config = currentConfig()
        if (!config.isConfigured) {
            return@withContext null
        }

        val recentCorrectionsJson = correctionDao?.let { dao ->
            try {
                val corrections = dao.getRecentCorrections(limit = 20)
                CorrectionHistoryHelper.toBackendJson(corrections)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch recent corrections", e)
                null
            }
        }

        try {
            val apiResult = api.classify(
                bitmap = bitmap,
                config = config,
                mode = "multi-hypothesis",  // NEW
                recentCorrections = recentCorrectionsJson,
            ) { attempt, error ->
                if (error != null) {
                    Log.w(TAG, "Multi-hypothesis attempt $attempt error: ${error.message}")
                }
            }

            when (apiResult) {
                is ApiResult.Success -> {
                    return@withContext parseMultiHypothesisResponse(apiResult.response)
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Multi-hypothesis classification error: ${apiResult.error.message}")
                    return@withContext null
                }
                is ApiResult.ConfigError -> {
                    Log.w(TAG, "Multi-hypothesis config error: ${apiResult.message}")
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in multi-hypothesis classification", e)
            return@withContext null
        }
    }
```

**4. CloudClassifier.kt** (Response Parser)
- Lines 380-402: Added `parseMultiHypothesisResponse()` method

```kotlin
private fun parseMultiHypothesisResponse(apiResponse: CloudClassificationResponse): MultiHypothesisResult? {
    val hypotheses = apiResponse.hypotheses?.take(3)?.map { h ->
        ClassificationHypothesis(
            categoryId = h.categoryId,
            categoryName = h.categoryName,
            explanation = h.explanation,
            confidence = h.confidence,
            confidenceBand = h.confidenceBand,
            attributes = h.attributes
        )
    } ?: emptyList()

    if (hypotheses.isEmpty()) {
        Log.w(TAG, "No hypotheses in multi-hypothesis response")
        return null
    }

    return MultiHypothesisResult(
        hypotheses = hypotheses,
        globalConfidence = apiResponse.globalConfidence ?: 0f,
        needsRefinement = apiResponse.needsRefinement ?: false,
        requestId = apiResponse.requestId ?: ""
    )
}
```

**5. ItemsViewModel.kt** (Classification Trigger)
- Lines 849-922: Updated `triggerMultiHypothesisClassification()` from placeholder to actual implementation

```kotlin
private suspend fun triggerMultiHypothesisClassification(
    detectionId: String,
    rawDetection: RawDetection
) {
    withContext(workerDispatcher) {
        try {
            val bitmap = rawDetection.fullFrameBitmap
            if (bitmap == null) {
                // Fallback: No bitmap
                createItemFromDetection(detectionId, rawDetection, hypothesis = null)
                removeFromPendingQueue(detectionId)
                return@withContext
            }

            val cloudClassifierImpl = cloudClassifier as? CloudClassifier
            if (cloudClassifierImpl == null) {
                // Fallback: No cloud classifier
                createItemFromDetection(detectionId, rawDetection, hypothesis = null)
                removeFromPendingQueue(detectionId)
                return@withContext
            }

            // Call backend with multi-hypothesis mode
            val result = cloudClassifierImpl.classifyMultiHypothesis(bitmap)

            if (result != null && result.hypotheses.isNotEmpty()) {
                // Phase 4 auto-commit logic (see Phase 4 section)
                // ...or show hypothesis sheet for user selection
            } else {
                // Fallback: No hypotheses
                createItemFromDetection(detectionId, rawDetection, hypothesis = null)
                removeFromPendingQueue(detectionId)
            }
        } catch (e: Exception) {
            // Fallback: Exception
            createItemFromDetection(detectionId, rawDetection, hypothesis = null)
            removeFromPendingQueue(detectionId)
        }
    }
}
```

**6. CameraFrameAnalyzer.kt** (Bitmap Capture)
- Line ~290: Capture bitmap for single-shot mode

```kotlin
val fullFrameBitmap = lazyBitmapProvider()

val rawDetections = response.scannedItems.map { item ->
    RawDetection(
        // ...
        fullFrameBitmap = fullFrameBitmap
    )
}
```

- Line ~369: Capture bitmap for tracking mode

```kotlin
val fullFrameBitmap = lazyBitmapProvider()
val frameSharpness = fullFrameBitmap?.let { bitmap ->
    SharpnessCalculator.calculateSharpness(bitmap)
} ?: 0f

// Later used in RawDetection:
val rawDetection = RawDetection(
    // ...
    frameSharpness = frameSharpness,
    fullFrameBitmap = fullFrameBitmap
)
```

---

***REMOVED******REMOVED******REMOVED*** Phase 3: Hypothesis Sheet Connection

**Goal**: Display hypothesis sheet in UI when classification completes.

***REMOVED******REMOVED******REMOVED******REMOVED*** Changes Made

**1. CameraScreen.kt** (State Collection)
- Location: `androidApp/src/main/java/com/scanium/app/camera/CameraScreen.kt`
- Lines ~200-207: Collect pending detection state

```kotlin
// Pending detection state for multi-hypothesis workflow
val currentPendingDetection by itemsViewModel.currentPendingDetection.collectAsState()
val pendingDetectionCount by itemsViewModel.pendingDetectionCount.collectAsState()

// Hypothesis selection state
val hypothesisSelectionState by itemsViewModel.hypothesisSelectionState.collectAsState()
```

**2. CameraScreen.kt** (Hypothesis Sheet)
- Lines ~1120-1150: Add HypothesisSelectionSheet composable

```kotlin
// Hypothesis selection sheet (Phase 3)
if (hypothesisSelectionState is HypothesisSelectionState.Showing) {
    val state = hypothesisSelectionState as HypothesisSelectionState.Showing
    HypothesisSelectionSheet(
        result = state.result,
        itemId = state.itemId,
        imageHash = "",
        thumbnailUri = state.thumbnailUri,
        onHypothesisConfirmed = { hypothesis ->
            itemsViewModel.confirmPendingDetection(state.itemId, hypothesis)
        },
        onNoneOfThese = { imageHash, predicted, confidence ->
            itemsViewModel.showCorrectionDialog(state.itemId, imageHash, predicted, confidence)
        },
        onAddPhoto = {
            itemsViewModel.dismissPendingDetection(state.itemId)
        },
        onDismiss = {
            itemsViewModel.dismissPendingDetection(state.itemId)
        }
    )
}
```

**3. ItemsListScreen.kt** (Pending Count)
- Location: `androidApp/src/main/java/com/scanium/app/items/ItemsListScreen.kt`
- Line ~104: Collect pending count

```kotlin
val pendingDetectionCount by itemsViewModel.pendingDetectionCount.collectAsState()
```

- Line ~451: Pass to ItemsListContent

```kotlin
ItemsListContent(
    items = items,
    pendingDetectionCount = pendingDetectionCount,  // NEW
    state = listState,
    // ...
)
```

**4. ItemsListContent.kt** (Pending Banner)
- Location: `androidApp/src/main/java/com/scanium/app/items/ItemsListContent.kt`
- Line 31: Import icon

```kotlin
import androidx.compose.material.icons.filled.HourglassEmpty
```

- Line 79: Update function signature

```kotlin
internal fun ItemsListContent(
    items: List<ScannedItem>,
    pendingDetectionCount: Int,  // NEW
    state: ItemsListState,
    // ...
)
```

- Lines ~113-140: Add pending indicator banner

```kotlin
// Pending detection indicator (Phase 3)
if (pendingDetectionCount > 0) {
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "$pendingDetectionCount ${if (pendingDetectionCount == 1) "item" else "items"} awaiting confirmation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
```

---

***REMOVED******REMOVED******REMOVED*** Phase 4: Auto-Commit for HIGH Confidence

**Goal**: Automatically confirm high-confidence detections with brief visual feedback.

***REMOVED******REMOVED******REMOVED******REMOVED*** Changes Made

**1. ClassificationHypothesis.kt** (confidenceBand Field)
- Location: `androidApp/src/main/java/com/scanium/app/classification/hypothesis/ClassificationHypothesis.kt`
- Line 13: Added confidenceBand

```kotlin
data class ClassificationHypothesis(
    val categoryId: String,
    val categoryName: String,
    val explanation: String,
    val confidence: Float,
    val confidenceBand: String = "MED",  // "HIGH", "MED", "LOW"
    val attributes: Map<String, String> = emptyMap()
)
```

**2. CloudClassifier.kt** (Include confidenceBand in Parsing)
- Line 387: Added confidenceBand to ClassificationHypothesis construction

```kotlin
ClassificationHypothesis(
    categoryId = h.categoryId,
    categoryName = h.categoryName,
    explanation = h.explanation,
    confidence = h.confidence,
    confidenceBand = h.confidenceBand,  // NEW
    attributes = h.attributes
)
```

**3. ItemsViewModel.kt** (Auto-Commit Logic)
- Lines 878-952: Implemented auto-commit decision logic

```kotlin
if (result != null && result.hypotheses.isNotEmpty()) {
    Log.d(TAG, "Received ${result.hypotheses.size} hypotheses for detection $detectionId")

    // Phase 4: Check if we should auto-commit
    val topHypothesis = result.hypotheses.first()
    val shouldAutoCommit =
        result.globalConfidence >= 0.9f &&
        result.hypotheses.size == 1 &&
        topHypothesis.confidenceBand == "HIGH"

    if (shouldAutoCommit) {
        // Auto-commit path: Show sheet briefly, then auto-confirm
        Log.d(TAG, "Auto-committing high-confidence hypothesis for detection $detectionId")

        withContext(mainDispatcher) {
            // Update to ShowingHypotheses
            val updatedQueue = _pendingDetections.value.map { ... }
            _pendingDetections.value = updatedQueue

            // Show hypothesis sheet
            if (updatedQueue.firstOrNull() is PendingDetectionState.ShowingHypotheses &&
                (updatedQueue.firstOrNull() as PendingDetectionState.ShowingHypotheses).detectionId == detectionId
            ) {
                showHypothesisSelection(
                    result = result,
                    itemId = detectionId,
                    thumbnailUri = null
                )
            }
        }

        // Wait 700ms for visual confirmation
        kotlinx.coroutines.delay(700)

        // Auto-confirm with top hypothesis
        withContext(mainDispatcher) {
            confirmPendingDetection(detectionId, topHypothesis)
        }
    } else {
        // Normal path: Show sheet and wait for user tap
        withContext(mainDispatcher) {
            // Update to ShowingHypotheses
            val updatedQueue = _pendingDetections.value.map { ... }
            _pendingDetections.value = updatedQueue

            // Show hypothesis sheet
            val currentPending = updatedQueue.firstOrNull()
            if (currentPending is PendingDetectionState.ShowingHypotheses &&
                currentPending.detectionId == detectionId
            ) {
                showHypothesisSelection(...)
            }
        }
    }
}
```

***REMOVED******REMOVED******REMOVED******REMOVED*** Auto-Commit Criteria

Items are automatically confirmed when **ALL** three conditions are met:

1. **Global Confidence ≥ 90%**: `result.globalConfidence >= 0.9f`
2. **Single Hypothesis**: `result.hypotheses.size == 1`
3. **HIGH Confidence Band**: `topHypothesis.confidenceBand == "HIGH"`

**Visual Confirmation**: Sheet appears for 700ms before auto-confirming, giving user brief visual feedback.

---

***REMOVED******REMOVED*** File Reference

***REMOVED******REMOVED******REMOVED*** New Files Created

| File | Lines | Purpose |
|------|-------|---------|
| `PendingDetectionState.kt` | 91 | Data models for pending detection lifecycle |

***REMOVED******REMOVED******REMOVED*** Files Modified

| File | Lines Changed | Primary Changes |
|------|---------------|-----------------|
| `ItemsViewModel.kt` | +200, -20 | Pending queue management, classification handlers |
| `CameraFrameAnalyzer.kt` | +30, -25 | RawDetection creation, bitmap capture |
| `CameraScreen.kt` | +40, -5 | HypothesisSelectionSheet integration |
| `CloudClassifierApi.kt` | +15, -5 | Multi-hypothesis mode parameter |
| `CloudClassifier.kt` | +80, -10 | classifyMultiHypothesis() method |
| `ItemsListScreen.kt` | +5, -2 | Pending count state collection |
| `ItemsListContent.kt` | +35, -5 | Pending indicator banner |
| `ClassificationHypothesis.kt` | +3, -2 | confidenceBand field |
| `CameraXManager.kt` | +10, -10 | Function signature updates |

**Total Changes**: ~690 insertions, ~129 deletions across 10 files.

---

***REMOVED******REMOVED*** Data Flow

***REMOVED******REMOVED******REMOVED*** Complete Detection-to-Item Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Camera Detection                                             │
│    User captures object → CameraFrameAnalyzer                   │
│    ├─ Single-shot: captureSingleFrame()                         │
│    └─ Tracking: processObjectDetectionWithTracking()            │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼ RawDetection created
┌─────────────────────────────────────────────────────────────────┐
│ 2. State Management                                             │
│    ItemsViewModel.onDetectionReady()                            │
│    ├─ Add to _pendingDetections queue (max 5)                   │
│    ├─ State: None → AwaitingClassification                      │
│    └─ Trigger: triggerMultiHypothesisClassification()           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼ Calls backend
┌─────────────────────────────────────────────────────────────────┐
│ 3. Backend Classification                                       │
│    CloudClassifier.classifyMultiHypothesis()                    │
│    ├─ Extract bitmap from RawDetection                          │
│    ├─ Fetch recent corrections for local learning               │
│    ├─ Call: CloudClassifierApi.classify(mode="multi-hypothesis")│
│    ├─ Parse: parseMultiHypothesisResponse()                     │
│    └─ Return: MultiHypothesisResult (3 hypotheses)              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼ Result received
┌─────────────────────────────────────────────────────────────────┐
│ 4. Decision Point: Auto-Commit Check                           │
│    if (globalConfidence >= 0.9 && size == 1 && band == "HIGH") │
│    ├─ YES: Auto-commit path →                                   │
│    │   ├─ State: AwaitingClassification → ShowingHypotheses     │
│    │   ├─ Show: HypothesisSelectionSheet                        │
│    │   ├─ Wait: 700ms delay                                     │
│    │   ├─ Auto: confirmPendingDetection()                       │
│    │   └─ Create: ScannedItem with confirmed hypothesis         │
│    └─ NO: Normal path →                                         │
│        ├─ State: AwaitingClassification → ShowingHypotheses     │
│        ├─ Show: HypothesisSelectionSheet                        │
│        └─ Wait: User tap                                        │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼ User taps hypothesis
┌─────────────────────────────────────────────────────────────────┐
│ 5. Item Creation                                                │
│    ItemsViewModel.confirmPendingDetection()                     │
│    ├─ Call: createItemFromDetection()                           │
│    ├─ Create: ScannedItem with confirmed label                  │
│    ├─ Remove: from _pendingDetections queue                     │
│    ├─ Hide: HypothesisSelectionSheet                            │
│    └─ Add: to items list via facade.addItem()                   │
└─────────────────────────────────────────────────────────────────┘
```

***REMOVED******REMOVED******REMOVED*** State Transitions

```
PendingDetectionState Flow:

None
  ↓ onDetectionReady()
AwaitingClassification
  ↓ Backend returns hypotheses
ShowingHypotheses
  ↓ confirmPendingDetection() OR dismissPendingDetection()
None (ScannedItem created)
```

***REMOVED******REMOVED******REMOVED*** Queue Management

```
Pending Queue (Max 5):

[Detection 1 (ShowingHypotheses)] ← Currently shown to user
[Detection 2 (AwaitingClassification)]
[Detection 3 (AwaitingClassification)]
[Detection 4 (AwaitingClassification)]
[Detection 5 (AwaitingClassification)]

When queue is full:
- New detection triggers auto-commit of oldest with fallback
- Prevents memory bloat from rapid scanning

When user confirms Detection 1:
- Detection 1 removed
- Detection 2 becomes ShowingHypotheses
- Hypothesis sheet updates to show Detection 2
```

---

***REMOVED******REMOVED*** Edge Cases & Error Handling

***REMOVED******REMOVED******REMOVED*** Fallback Strategy

**Philosophy**: Never lose a detection. If classification fails, create item with on-device label.

| Scenario | Behavior | Code Location |
|----------|----------|---------------|
| **No bitmap available** | Create item with on-device label | ItemsViewModel.kt:856-861 |
| **Cloud classifier unavailable** | Create item with on-device label | ItemsViewModel.kt:865-870 |
| **Backend returns no hypotheses** | Create item with on-device label | ItemsViewModel.kt:910-913 |
| **Backend throws exception** | Create item with on-device label | ItemsViewModel.kt:915-920 |
| **User dismisses sheet** | Create item with on-device label | ItemsViewModel.kt:902-916 |
| **Queue exceeds 5 items** | Auto-commit oldest with fallback | ItemsViewModel.kt:799-805 |

***REMOVED******REMOVED******REMOVED*** Queue Management Edge Cases

**Multiple Rapid Scans**:
- Detections queued in order (FIFO)
- Hypothesis sheets shown sequentially
- Only first detection's sheet is visible at a time
- User confirms → next detection's sheet appears

**Queue Full (5 items)**:
```kotlin
if (currentQueue.size >= 5) {
    Log.w(TAG, "Pending queue full, auto-confirming oldest with fallback")
    confirmPendingWithFallback(currentQueue.first())
}
```

**App Backgrounding**:
- Pending state preserved in ViewModel
- Sheets dismissed on background (modal behavior)
- Items created with fallback if state expires

***REMOVED******REMOVED******REMOVED*** Network & Timeout Handling

**Classification Timeout**:
- OkHttp timeout: 120 seconds (configurable in CloudClassifierApi)
- Retry logic: 3 attempts with exponential backoff
- Final failure: Falls back to on-device label

**Offline Detection**:
- CloudClassifierApi detects `UnknownHostException`
- Immediate fallback, no retries
- User sees item with on-device label instantly

***REMOVED******REMOVED******REMOVED*** UI State Synchronization

**Concurrent Confirmations**:
- State updates use `withContext(mainDispatcher)` for thread safety
- StateFlow ensures UI consistency
- Queue mutations are atomic operations

**Sheet Dismissal Race Condition**:
- User dismisses sheet while auto-commit is pending
- `dismissPendingDetection()` checks if detection still exists
- Safe no-op if already removed

---

***REMOVED******REMOVED*** Testing & Verification

***REMOVED******REMOVED******REMOVED*** Build Verification

```bash
***REMOVED*** Build successful for all phases
./gradlew :androidApp:assembleDevDebug

***REMOVED*** Results:
✅ Phase 1: BUILD SUCCESSFUL
✅ Phase 2: BUILD SUCCESSFUL
✅ Phase 3: BUILD SUCCESSFUL
✅ Phase 4: BUILD SUCCESSFUL
```

***REMOVED******REMOVED******REMOVED*** Manual Testing Checklist

***REMOVED******REMOVED******REMOVED******REMOVED*** Phase 1: Pending State

- [ ] Scan object in tracking mode → No item appears immediately
- [ ] Check logs: "onDetectionReady called"
- [ ] Check logs: "Placeholder: Create item with on-device label"
- [ ] Item appears after 500ms delay (simulated classification)

***REMOVED******REMOVED******REMOVED******REMOVED*** Phase 2: Backend Integration

- [ ] Enable cloud classification in settings
- [ ] Scan object → Backend receives `mode=multi-hypothesis` request
- [ ] Check backend logs: "Processing multi-hypothesis request"
- [ ] Check Android logs: "Received 3 hypotheses from backend"

***REMOVED******REMOVED******REMOVED******REMOVED*** Phase 3: Hypothesis Sheet

- [ ] Scan object → Hypothesis sheet appears with 3 options
- [ ] Verify: Top hypothesis has explanation text
- [ ] Verify: Confidence percentages displayed
- [ ] Tap option ***REMOVED***2 → Item created with that category
- [ ] Check items list: Pending indicator shows "N items awaiting"
- [ ] Dismiss sheet → Item created with on-device label

***REMOVED******REMOVED******REMOVED******REMOVED*** Phase 4: Auto-Commit

- [ ] Scan high-confidence object (e.g., Apple iPhone with logo)
- [ ] Expected: Sheet appears briefly (700ms)
- [ ] Expected: Sheet auto-dismisses
- [ ] Expected: Item appears with confirmed label
- [ ] Check logs: "Auto-committing high-confidence hypothesis"

***REMOVED******REMOVED******REMOVED******REMOVED*** Edge Cases

- [ ] Scan 6 objects rapidly → Oldest auto-commits
- [ ] Disable network → Item created with on-device label
- [ ] Dismiss sheet → Item appears with fallback label
- [ ] Background app mid-classification → No crash on resume

***REMOVED******REMOVED******REMOVED*** Regression Testing

- [ ] Existing ItemAggregator deduplication still works
- [ ] Price estimation triggers correctly
- [ ] Export functionality (CSV/ZIP) works
- [ ] Item editing preserves pending detection state
- [ ] Settings changes don't crash pending detections

---

***REMOVED******REMOVED*** Future Enhancements

***REMOVED******REMOVED******REMOVED*** Short-Term

1. **Thumbnail Display**
   - Currently: `thumbnailUri = null` in HypothesisSelectionSheet
   - Enhancement: Capture and display detection thumbnail in sheet
   - Benefit: Visual context for user confirmation

2. **Image Hash Computation**
   - Currently: `imageHash = ""` for correction tracking
   - Enhancement: Compute SHA-256 hash of detection bitmap
   - Benefit: Better correction history tracking

3. **Confidence Threshold Tuning**
   - Currently: Hard-coded 0.9f (90%) for auto-commit
   - Enhancement: Make configurable in Developer Options
   - Benefit: A/B testing optimal threshold

4. **Pending Queue Persistence**
   - Currently: Queue cleared on app restart
   - Enhancement: Persist to Room database
   - Benefit: Survive app crashes, system kills

***REMOVED******REMOVED******REMOVED*** Medium-Term

1. **Multi-Photo Refinement**
   - Add: "Add Photo" button triggers camera return
   - Capture: Additional angle of same object
   - Backend: Combine multiple images for better classification
   - UI: Show photo count in hypothesis sheet

2. **Hypothesis Explanation Expansion**
   - Add: Tap explanation to show detailed reasoning
   - Display: Evidence refs (logos, OCR, colors detected)
   - UI: Bottom sheet expansion with visual evidence

3. **Batch Confirmation**
   - Add: "Confirm All" button when multiple pending
   - Logic: Auto-confirm all HIGH confidence, show sheet for rest
   - Benefit: Faster workflow for bulk scanning

4. **Undo/Redo for Confirmations**
   - Track: Last N confirmed items in memory
   - UI: "Undo" snackbar after confirmation
   - Action: Revert to pending state, show sheet again

***REMOVED******REMOVED******REMOVED*** Long-Term

1. **On-Device Multi-Hypothesis**
   - Run: Multiple ML Kit models concurrently
   - Generate: 3 hypotheses from on-device models
   - Benefit: Works offline, instant feedback

2. **Active Learning Integration**
   - Track: User correction patterns over time
   - Adapt: Adjust auto-commit thresholds per category
   - Backend: Send learning signals for model improvement

3. **Collaborative Scanning**
   - Share: Pending detections across team members
   - Vote: Multiple users confirm same detection
   - Use Case: Warehouse teams scanning bulk items

4. **Voice Confirmation**
   - Add: Voice commands for hands-free confirmation
   - Example: "Select option 1" or "None of these"
   - Accessibility: Benefit for users with mobility issues

---

***REMOVED******REMOVED*** Appendix: API Contract

***REMOVED******REMOVED******REMOVED*** Backend Endpoint

```
POST {SCANIUM_API_BASE_URL}/v1/classify?mode=multi-hypothesis&enrichAttributes=true

Headers:
  X-API-Key: {apiKey}
  X-Scanium-Correlation-Id: {correlationId}
  X-Client: Scanium-Android
  X-App-Version: {version}

Body (multipart/form-data):
  image: [JPEG binary, 85% quality]
  domainPackId: "home_resale"
  recentCorrections: [JSON array, optional]
```

***REMOVED******REMOVED******REMOVED*** Response Schema

```json
{
  "hypotheses": [
    {
      "categoryId": "furniture_sofa",
      "categoryName": "Sofa",
      "explanation": "Three-seater fabric sofa with visible cushions and armrests",
      "confidence": 0.92,
      "confidenceBand": "HIGH",
      "attributes": {
        "material": "fabric",
        "color": "gray"
      }
    },
    {
      "categoryId": "furniture_couch",
      "categoryName": "Couch",
      "explanation": "Could be a couch based on similar seating arrangement",
      "confidence": 0.78,
      "confidenceBand": "MED",
      "attributes": {}
    },
    {
      "categoryId": "furniture_loveseat",
      "categoryName": "Loveseat",
      "explanation": "Smaller seating furniture, though appears larger",
      "confidence": 0.65,
      "confidenceBand": "LOW",
      "attributes": {}
    }
  ],
  "globalConfidence": 0.92,
  "needsRefinement": false,
  "requestId": "clf-20250122-abc123",
  "provider": "openai"
}
```

***REMOVED******REMOVED******REMOVED*** Confidence Bands

| Band | Range | Meaning |
|------|-------|---------|
| HIGH | 0.85-1.0 | Very confident, eligible for auto-commit |
| MED | 0.60-0.84 | Moderately confident, requires confirmation |
| LOW | 0.0-0.59 | Low confidence, alternative suggestion |

---

***REMOVED******REMOVED*** References

- **Original Plan**: `/.claude/plans/misty-puzzling-pearl.md`
- **Commit**: dc404254 (January 2025)
- **Related Issues**:
  - Premature Commitment Problem
  - User Trust & Collaboration
  - Multi-Hypothesis Classification

---

**Document Version**: 1.0
**Last Updated**: January 22, 2026
**Author**: Claude Sonnet 4.5
**Reviewers**: [Pending]
