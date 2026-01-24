package com.scanium.app.items

import com.scanium.app.ItemCategory
import com.scanium.app.classification.hypothesis.MultiHypothesisResult
import com.scanium.shared.core.models.model.ImageRef

/**
 * Represents the state of a detection awaiting user confirmation.
 *
 * This sealed class implements the "no items before confirmation" principle:
 * detections are held in pending state until user explicitly confirms a hypothesis,
 * preventing premature commitment to incorrect classifications.
 *
 * State transitions:
 * ```
 * None → AwaitingClassification (detection received)
 *     → ShowingHypotheses (backend returns hypotheses)
 *     → None (user confirms or dismisses)
 * ```
 */
sealed class PendingDetectionState {
    /** No pending detection currently active */
    object None : PendingDetectionState()

    /**
     * Detection received from camera, waiting for backend classification.
     *
     * @property detectionId Unique identifier for this pending detection
     * @property rawDetection Raw detection data from ML Kit/ObjectTracker
     * @property thumbnailRef Optional reference to detection thumbnail
     * @property timestamp Epoch milliseconds when detection was created
     */
    data class AwaitingClassification(
        val detectionId: String,
        val rawDetection: RawDetection,
        val thumbnailRef: ImageRef?,
        val timestamp: Long,
    ) : PendingDetectionState()

    /**
     * Backend returned multi-hypothesis classification results, ready for user selection.
     *
     * @property detectionId Unique identifier for this pending detection
     * @property rawDetection Raw detection data from ML Kit/ObjectTracker
     * @property hypothesisResult Multi-hypothesis classification result from backend
     * @property thumbnailRef WYSIWYG thumbnail image reference for displaying in hypothesis sheet
     */
    data class ShowingHypotheses(
        val detectionId: String,
        val rawDetection: RawDetection,
        val hypothesisResult: MultiHypothesisResult,
        val thumbnailRef: ImageRef?,
    ) : PendingDetectionState()
}

/**
 * Raw detection data before item creation.
 *
 * Holds minimal information about a detected object, sufficient for
 * triggering classification and creating a ScannedItem after confirmation.
 *
 * @property boundingBox Object bounding box in normalized coordinates (0-1)
 * @property confidence ML Kit detection confidence (0.0-1.0)
 * @property onDeviceLabel ML Kit's initial label (fallback if user dismisses)
 * @property onDeviceCategory ML Kit's category assignment
 * @property trackingId Optional tracking ID from ObjectTracker (null for single-shot)
 * @property frameSharpness Image quality metric (0.0-1.0)
 * @property captureType How this detection was captured
 * @property thumbnailRef Optional thumbnail image reference for display in UI
 * @property fullFrameBitmap Optional full frame bitmap for classification (cleared after use)
 */
data class RawDetection(
    val boundingBox: com.scanium.shared.core.models.model.NormalizedRect?,
    val confidence: Float,
    val onDeviceLabel: String,
    val onDeviceCategory: ItemCategory,
    val trackingId: String?,
    val frameSharpness: Float,
    val captureType: CaptureType,
    val thumbnailRef: ImageRef? = null,
    val fullFrameBitmap: android.graphics.Bitmap? = null,
)

/**
 * How a detection was captured.
 */
enum class CaptureType {
    /** Single-shot capture from camera button */
    SINGLE_SHOT,

    /** Continuous tracking mode (ObjectTracker confirmed) */
    TRACKING,
}
