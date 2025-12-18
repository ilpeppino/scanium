package com.scanium.test

import com.scanium.core.models.geometry.NormalizedRect
import com.scanium.core.models.items.ScannedItem
import com.scanium.core.models.ml.ItemCategory
import com.scanium.core.tracking.DetectionInfo
import com.scanium.core.tracking.TrackerConfig

/**
 * Pre-defined test fixtures for common test scenarios.
 *
 * Use these fixtures to ensure consistency across tests and reduce boilerplate.
 */

object TestFixtures {

    /**
     * Standard bounding boxes for testing.
     */
    object BoundingBoxes {
        val topLeft = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f)
        val topRight = NormalizedRect(0.7f, 0.1f, 0.9f, 0.3f)
        val bottomLeft = NormalizedRect(0.1f, 0.7f, 0.3f, 0.9f)
        val bottomRight = NormalizedRect(0.7f, 0.7f, 0.9f, 0.9f)
        val center = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f)
        val centerLarge = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
        val centerSmall = NormalizedRect(0.45f, 0.45f, 0.55f, 0.55f)

        /**
         * Returns a bounding box slightly shifted from the given box (for testing spatial matching).
         */
        fun slightlyShifted(box: NormalizedRect, delta: Float = 0.02f): NormalizedRect {
            return NormalizedRect(
                left = box.left + delta,
                top = box.top + delta,
                right = box.right + delta,
                bottom = box.bottom + delta
            )
        }

        /**
         * Returns a bounding box far from the given box (for testing non-matching).
         */
        fun farFrom(box: NormalizedRect): NormalizedRect {
            // Move to opposite quadrant
            val newLeft = if (box.left < 0.5f) 0.7f else 0.1f
            val newTop = if (box.top < 0.5f) 0.7f else 0.1f
            return NormalizedRect(newLeft, newTop, newLeft + 0.2f, newTop + 0.2f)
        }
    }

    /**
     * Standard tracker configurations for testing.
     */
    object TrackerConfigs {
        val default = TrackerConfig(
            minFramesToConfirm = 3,
            minConfidence = 0.4f,
            minBoxArea = 0.001f,
            maxFrameGap = 5,
            minMatchScore = 0.3f,
            expiryFrames = 10
        )

        val strict = TrackerConfig(
            minFramesToConfirm = 5,
            minConfidence = 0.7f,
            minBoxArea = 0.01f,
            maxFrameGap = 2,
            minMatchScore = 0.5f,
            expiryFrames = 5
        )

        val lenient = TrackerConfig(
            minFramesToConfirm = 1,
            minConfidence = 0.1f,
            minBoxArea = 0.0001f,
            maxFrameGap = 10,
            minMatchScore = 0.1f,
            expiryFrames = 20
        )

        val fastConfirmation = TrackerConfig(
            minFramesToConfirm = 2,
            minConfidence = 0.5f,
            minBoxArea = 0.01f,
            maxFrameGap = 5,
            minMatchScore = 0.3f,
            expiryFrames = 10
        )
    }

    /**
     * Standard detection scenarios for testing.
     */
    object Detections {
        val fashionShoe = testDetectionInfo(
            trackingId = "fashion_shoe_1",
            boundingBox = BoundingBoxes.topLeft,
            confidence = 0.85f,
            category = ItemCategory.FASHION,
            labelText = "Shoe"
        )

        val fashionShirt = testDetectionInfo(
            trackingId = "fashion_shirt_1",
            boundingBox = BoundingBoxes.topRight,
            confidence = 0.78f,
            category = ItemCategory.FASHION,
            labelText = "Shirt"
        )

        val electronicsLaptop = testDetectionInfo(
            trackingId = "electronics_laptop_1",
            boundingBox = BoundingBoxes.center,
            confidence = 0.92f,
            category = ItemCategory.ELECTRONICS,
            labelText = "Laptop"
        )

        val homeGoodLamp = testDetectionInfo(
            trackingId = "home_lamp_1",
            boundingBox = BoundingBoxes.bottomLeft,
            confidence = 0.76f,
            category = ItemCategory.HOME_GOOD,
            labelText = "Lamp"
        )

        val collectibleCard = testDetectionInfo(
            trackingId = "collectible_card_1",
            boundingBox = BoundingBoxes.bottomRight,
            confidence = 0.68f,
            category = ItemCategory.FASHION,
            labelText = "Trading Card"
        )

        val lowConfidence = testDetectionInfo(
            trackingId = "low_conf_1",
            boundingBox = BoundingBoxes.centerSmall,
            confidence = 0.25f,
            category = ItemCategory.UNKNOWN,
            labelText = "Unknown"
        )

        val noTrackingId = testDetectionInfo(
            trackingId = null,
            boundingBox = BoundingBoxes.center,
            confidence = 0.75f,
            category = ItemCategory.FASHION,
            labelText = "Item"
        )
    }

    /**
     * Standard scanned items for testing.
     */
    object Items {
        val fashionItem = testScannedItem(
            id = "item_fashion_1",
            category = ItemCategory.FASHION,
            labelText = "Designer Bag",
            confidence = 0.88f,
            boundingBox = BoundingBoxes.center
        )

        val electronicsItem = testScannedItem(
            id = "item_electronics_1",
            category = ItemCategory.ELECTRONICS,
            labelText = "Smartphone",
            confidence = 0.95f,
            boundingBox = BoundingBoxes.topLeft
        )

        val homeGoodItem = testScannedItem(
            id = "item_home_1",
            category = ItemCategory.HOME_GOOD,
            labelText = "Decorative Vase",
            confidence = 0.82f,
            boundingBox = BoundingBoxes.bottomRight
        )

        val mergedItem = testScannedItem(
            id = "item_merged_1",
            category = ItemCategory.FASHION,
            labelText = "Shoes",
            confidence = 0.85f,
            boundingBox = BoundingBoxes.center,
            mergeCount = 3,
            sourceDetectionIds = setOf("det_1", "det_2", "det_3")
        )
    }

    /**
     * Multi-frame detection sequences for testing tracking.
     */
    object Sequences {
        /**
         * A stable detection sequence (same object, same tracking ID, across frames).
         */
        fun stableTracking(frameCount: Int = 5): List<List<DetectionInfo>> {
            return (1..frameCount).map { frameIndex ->
                listOf(
                    testDetectionInfo(
                        trackingId = "stable_1",
                        boundingBox = BoundingBoxes.slightlyShifted(
                            BoundingBoxes.center,
                            delta = 0.01f * frameIndex
                        ),
                        confidence = 0.8f + (frameIndex * 0.02f),
                        category = ItemCategory.FASHION,
                        labelText = "Tracked Item"
                    )
                )
            }
        }

        /**
         * A detection sequence with intermittent gaps.
         */
        fun intermittentDetection(): List<List<DetectionInfo>> {
            return listOf(
                // Frame 1: Detected
                listOf(
                    testDetectionInfo(
                        trackingId = "intermittent_1",
                        boundingBox = BoundingBoxes.center,
                        confidence = 0.75f
                    )
                ),
                // Frame 2-3: Not detected
                emptyList(),
                emptyList(),
                // Frame 4: Detected again
                listOf(
                    testDetectionInfo(
                        trackingId = "intermittent_1",
                        boundingBox = BoundingBoxes.center,
                        confidence = 0.78f
                    )
                ),
                // Frame 5: Detected
                listOf(
                    testDetectionInfo(
                        trackingId = "intermittent_1",
                        boundingBox = BoundingBoxes.center,
                        confidence = 0.80f
                    )
                )
            )
        }

        /**
         * Multiple objects appearing and disappearing.
         */
        fun multipleObjects(): List<List<DetectionInfo>> {
            return listOf(
                // Frame 1: Object A
                listOf(Detections.fashionShoe),
                // Frame 2: Objects A + B
                listOf(Detections.fashionShoe, Detections.electronicsLaptop),
                // Frame 3: Objects A + B
                listOf(Detections.fashionShoe, Detections.electronicsLaptop),
                // Frame 4: Objects B + C
                listOf(Detections.electronicsLaptop, Detections.homeGoodLamp),
                // Frame 5: Object C only
                listOf(Detections.homeGoodLamp)
            )
        }
    }

    /**
     * Category pairs for testing similarity.
     */
    object CategoryPairs {
        val same = ItemCategory.FASHION to ItemCategory.FASHION
        val different = ItemCategory.FASHION to ItemCategory.ELECTRONICS
        val similarDomain = ItemCategory.FASHION to ItemCategory.HOME_GOOD
    }
}
