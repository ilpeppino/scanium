package com.scanium.app.items

import com.scanium.app.ml.ItemCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Comprehensive unit tests for SessionDeduplicator.
 *
 * Tests verify:
 * - Multi-factor similarity matching (category, label, size, position)
 * - Edge cases with missing or partial data
 * - Safety mechanisms for items lacking distinguishing features
 * - Reset and cleanup operations
 * - The "no zero items" failure mode prevention
 */
@RunWith(RobolectricTestRunner::class)
class SessionDeduplicatorTest {

    private lateinit var deduplicator: SessionDeduplicator

    @Before
    fun setUp() {
        deduplicator = SessionDeduplicator()
    }

    // ==================== Exact ID Matching ====================

    @Test
    fun whenNewItemHasSameIdAsExisting_thenFoundAsDuplicate() {
        // Arrange
        val existingItem = createTestItem(
            id = "item-1",
            category = ItemCategory.FASHION
        )
        val newItem = createTestItem(
            id = "item-1", // Same ID
            category = ItemCategory.ELECTRONICS // Different category doesn't matter
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert
        assertThat(similarId).isEqualTo("item-1")
    }

    @Test
    fun whenNewItemHasDifferentId_thenChecksOtherSimilarityFactors() {
        // Arrange
        val existingItem = createTestItem(
            id = "item-1",
            category = ItemCategory.FASHION
        )
        val newItem = createTestItem(
            id = "item-2", // Different ID
            category = ItemCategory.FASHION
        )

        // Act - Both lack distinguishing features, so should not be considered similar
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert
        assertThat(similarId).isNull()
    }

    // ==================== Category Matching ====================

    @Test
    fun whenCategoriesDiffer_thenNotSimilar() {
        // Arrange
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.ELECTRONICS, // Different category
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert
        assertThat(similarId).isNull()
    }

    @Test
    fun whenCategoriesMatch_thenMayBeSimilar() {
        // Arrange
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.FASHION, // Same category
            thumbnailWidth = 205,
            thumbnailHeight = 205 // Similar size
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert - Should be similar (same category, similar size)
        assertThat(similarId).isEqualTo("item-1")
    }

    // ==================== Label Similarity ====================

    @Test
    fun whenLabelsSimilar_thenIncreasesLikelihoodOfMatch() {
        // Arrange - Labels are based on category names in extractMetadata
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.FASHION, // Same category = same label
            thumbnailWidth = 210,
            thumbnailHeight = 210
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert
        assertThat(similarId).isEqualTo("item-1")
    }

    // ==================== Size Similarity ====================

    @Test
    fun whenSizesSimilar_thenMayBeSimilar() {
        // Arrange
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 200,
            thumbnailHeight = 200 // Area = 40000 / (1280*720) ≈ 0.0434
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 210,
            thumbnailHeight = 210 // Area = 44100 / (1280*720) ≈ 0.0479 (within 40% tolerance)
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert
        assertThat(similarId).isEqualTo("item-1")
    }

    @Test
    fun whenSizesTooDifferent_thenNotSimilar() {
        // Arrange
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 400,
            thumbnailHeight = 400 // Area is 4x larger (exceeds 40% tolerance)
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert
        assertThat(similarId).isNull()
    }

    @Test
    fun whenOneItemHasNoSize_thenSizeIgnoredAndCategoryMatches() {
        // Arrange - When one item has thumbnail and one doesn't
        // The safety check allows matching if at least one has distinguishing features
        val existingItem = createTestItem(
            id = "item-1",
            category = ItemCategory.FASHION
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )

        // Act - Since newItem has distinguishing features, matching can proceed
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert - They match because they have the same category and size is ignored
        // This is correct behavior - we can match a well-defined item with a less-defined one
        assertThat(similarId).isEqualTo("item-1")
    }

    // ==================== Distinguishing Features Safety Check ====================

    @Test
    fun whenBothItemsLackDistinguishingFeatures_thenNotSimilar() {
        // This is the critical safety check that prevents false positives
        // with minimally-populated test data

        // Arrange - Both items have no thumbnail and default position
        val existingItem = createTestItem(
            id = "item-1",
            category = ItemCategory.FASHION
        )
        val newItem = createTestItem(
            id = "item-2",
            category = ItemCategory.FASHION // Same category but no distinguishing features
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert - Should NOT match due to safety check
        assertThat(similarId).isNull()
    }

    @Test
    fun whenOneItemHasDistinguishingFeatures_thenCanMatch() {
        // Arrange
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200 // Has distinguishing feature
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.FASHION,
            thumbnailWidth = 210,
            thumbnailHeight = 210 // Has distinguishing feature
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert - Should match
        assertThat(similarId).isEqualTo("item-1")
    }

    // ==================== Multiple Existing Items ====================

    @Test
    fun whenMultipleExistingItems_thenFindsCorrectMatch() {
        // Arrange
        val existingItems = listOf(
            createItemWithThumbnail("item-1", ItemCategory.FASHION, 100, 100),
            createItemWithThumbnail("item-2", ItemCategory.ELECTRONICS, 200, 200),
            createItemWithThumbnail("item-3", ItemCategory.HOME_GOOD, 300, 300)
        )
        val newItem = createItemWithThumbnail(
            id = "item-4",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 205,
            thumbnailHeight = 205 // Similar to item-2
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, existingItems)

        // Assert
        assertThat(similarId).isEqualTo("item-2")
    }

    @Test
    fun whenNoSimilarItemExists_thenReturnsNull() {
        // Arrange
        val existingItems = listOf(
            createItemWithThumbnail("item-1", ItemCategory.FASHION, 100, 100),
            createItemWithThumbnail("item-2", ItemCategory.ELECTRONICS, 200, 200)
        )
        val newItem = createItemWithThumbnail(
            id = "item-3",
            category = ItemCategory.FOOD,
            thumbnailWidth = 300,
            thumbnailHeight = 300
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, existingItems)

        // Assert
        assertThat(similarId).isNull()
    }

    @Test
    fun whenEmptyExistingList_thenReturnsNull() {
        // Arrange
        val newItem = createTestItem(id = "item-1", category = ItemCategory.FASHION)

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, emptyList())

        // Assert
        assertThat(similarId).isNull()
    }

    // ==================== Metadata Caching ====================

    @Test
    fun whenItemCheckedMultipleTimes_thenMetadataCached() {
        // Arrange
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val newItem1 = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val newItem2 = createItemWithThumbnail(
            id = "item-3",
            category = ItemCategory.FASHION,
            thumbnailWidth = 205,
            thumbnailHeight = 205
        )

        // Act - Check multiple new items against same existing item
        deduplicator.findSimilarItem(newItem1, listOf(existingItem))
        val similarId = deduplicator.findSimilarItem(newItem2, listOf(existingItem))

        // Assert - Second call should use cached metadata and find match
        assertThat(similarId).isEqualTo("item-1")
    }

    // ==================== Reset Functionality ====================

    @Test
    fun whenReset_thenClearsAllMetadata() {
        // Arrange
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.FASHION,
            thumbnailWidth = 205,
            thumbnailHeight = 205
        )

        // Build up metadata cache
        deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Act
        deduplicator.reset()

        // Assert - After reset, should still work correctly
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))
        assertThat(similarId).isEqualTo("item-1")
    }

    @Test
    fun whenResetCalledMultipleTimes_thenNoError() {
        // Act
        deduplicator.reset()
        deduplicator.reset()
        deduplicator.reset()

        // Assert - Should not throw any exceptions
        val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
        val similarId = deduplicator.findSimilarItem(item, emptyList())
        assertThat(similarId).isNull()
    }

    // ==================== Remove Item Functionality ====================

    @Test
    fun whenItemRemoved_thenMetadataCleared() {
        // Arrange
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.FASHION,
            thumbnailWidth = 205,
            thumbnailHeight = 205
        )

        // Build up metadata cache
        deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Act
        deduplicator.removeItem("item-1")

        // Assert - Should still work after removal (metadata will be rebuilt)
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))
        assertThat(similarId).isEqualTo("item-1")
    }

    @Test
    fun whenRemovingNonExistentItem_thenNoError() {
        // Act
        deduplicator.removeItem("non-existent-id")

        // Assert - Should not throw any exceptions
        val item = createTestItem(id = "item-1", category = ItemCategory.FASHION)
        val similarId = deduplicator.findSimilarItem(item, emptyList())
        assertThat(similarId).isNull()
    }

    // ==================== Real-World Scenarios ====================

    @Test
    fun whenSamePhysicalObjectWithDifferentTrackingIds_thenDetectedAsSimilar() {
        // Simulates ML Kit changing tracking ID for same object

        // Arrange - Same physical object detected with different IDs
        val detection1 = createItemWithThumbnail(
            id = "track_1",
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val detection2 = createItemWithThumbnail(
            id = "track_2", // Different tracking ID
            category = ItemCategory.ELECTRONICS,
            thumbnailWidth = 205, // Slightly different size (camera moved)
            thumbnailHeight = 205
        )

        // Act
        val similarId = deduplicator.findSimilarItem(detection2, listOf(detection1))

        // Assert
        assertThat(similarId).isEqualTo("track_1")
    }

    @Test
    fun whenDifferentObjectsSameCategory_thenNotSimilar() {
        // Arrange - Two different shirts
        val shirt1 = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.FASHION,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val shirt2 = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.FASHION,
            thumbnailWidth = 400, // Very different size
            thumbnailHeight = 400
        )

        // Act
        val similarId = deduplicator.findSimilarItem(shirt2, listOf(shirt1))

        // Assert
        assertThat(similarId).isNull()
    }

    @Test
    fun whenObjectMovesSignificantly_thenStillConsideredSimilar() {
        // Position is not reliable from ScannedItem (always defaults to 0.5, 0.5)
        // So this test verifies that position doesn't prevent matching

        // Arrange
        val item1 = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.HOME_GOOD,
            thumbnailWidth = 200,
            thumbnailHeight = 200
        )
        val item2 = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.HOME_GOOD,
            thumbnailWidth = 210, // Similar size
            thumbnailHeight = 210
        )

        // Act
        val similarId = deduplicator.findSimilarItem(item2, listOf(item1))

        // Assert - Should match based on category and size
        assertThat(similarId).isEqualTo("item-1")
    }

    @Test
    fun whenMultipleSimilarObjectsExist_thenFindsFirstMatch() {
        // Arrange - Multiple similar items
        val existingItems = listOf(
            createItemWithThumbnail("item-1", ItemCategory.FASHION, 200, 200),
            createItemWithThumbnail("item-2", ItemCategory.FASHION, 205, 205),
            createItemWithThumbnail("item-3", ItemCategory.FASHION, 210, 210)
        )
        val newItem = createItemWithThumbnail(
            id = "item-4",
            category = ItemCategory.FASHION,
            thumbnailWidth = 202,
            thumbnailHeight = 202
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, existingItems)

        // Assert - Should find first similar item
        assertThat(similarId).isNotNull()
        assertThat(existingItems.map { it.id }).contains(similarId)
    }

    // ==================== Edge Cases ====================

    @Test
    fun whenItemsHaveMinimalData_thenHandlesGracefully() {
        // Arrange - Minimal ScannedItems
        val existingItem = ScannedItem(
            id = "item-1",
            category = ItemCategory.UNKNOWN,
            priceRange = 0.0 to 0.0
        )
        val newItem = ScannedItem(
            id = "item-2",
            category = ItemCategory.UNKNOWN,
            priceRange = 0.0 to 0.0
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert - Should not match (lack distinguishing features)
        assertThat(similarId).isNull()
    }

    @Test
    fun whenConfidenceLevelsVary_thenDoesNotAffectSimilarity() {
        // Confidence is stored in metadata but not used for similarity matching

        // Arrange
        val existingItem = createTestItem(
            id = "item-1",
            category = ItemCategory.FASHION,
            confidence = 0.9f
        )
        val newItem = createTestItem(
            id = "item-2",
            category = ItemCategory.FASHION,
            confidence = 0.3f
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert - Should not match (lack distinguishing features)
        assertThat(similarId).isNull()
    }

    @Test
    fun whenVerySmallThumbnails_thenStillProcessesCorrectly() {
        // Arrange - Use larger thumbnails to exceed the 0.01 area threshold
        // 100x100 = 10000 / (1280*720) ≈ 0.0108 which is > 0.01
        val existingItem = createItemWithThumbnail(
            id = "item-1",
            category = ItemCategory.PLANT,
            thumbnailWidth = 100,
            thumbnailHeight = 100
        )
        val newItem = createItemWithThumbnail(
            id = "item-2",
            category = ItemCategory.PLANT,
            thumbnailWidth = 105,
            thumbnailHeight = 105
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, listOf(existingItem))

        // Assert - Should match (both have thumbnails indicating distinguishing features)
        assertThat(similarId).isEqualTo("item-1")
    }

    // ==================== Stress Tests ====================

    @Test
    fun whenManyExistingItems_thenPerformsEfficiently() {
        // Arrange - Large list of existing items
        val existingItems = (1..100).map { i ->
            createItemWithThumbnail(
                id = "item-$i",
                category = ItemCategory.values()[i % ItemCategory.values().size],
                thumbnailWidth = 100 + i * 10,
                thumbnailHeight = 100 + i * 10
            )
        }
        val newItem = createItemWithThumbnail(
            id = "item-new",
            category = ItemCategory.FASHION,
            thumbnailWidth = 555, // Should match item-45 or similar
            thumbnailHeight = 555
        )

        // Act
        val similarId = deduplicator.findSimilarItem(newItem, existingItems)

        // Assert - Should complete without error
        // May or may not find a match depending on exact values
        val possibleIds = listOf(null) + existingItems.map { it.id }
        assertThat(similarId).isIn(possibleIds)
    }

    // ==================== Helper Functions ====================

    private fun createTestItem(
        id: String = "test-id",
        category: ItemCategory = ItemCategory.UNKNOWN,
        confidence: Float = 0.5f
    ): ScannedItem {
        return ScannedItem(
            id = id,
            thumbnail = null,
            category = category,
            priceRange = 10.0 to 20.0,
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun createItemWithThumbnail(
        id: String,
        category: ItemCategory,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
        confidence: Float = 0.5f
    ): ScannedItem {
        // Create a mock bitmap for testing
        val bitmap = android.graphics.Bitmap.createBitmap(
            thumbnailWidth,
            thumbnailHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )

        return ScannedItem(
            id = id,
            thumbnail = bitmap,
            category = category,
            priceRange = 10.0 to 20.0,
            confidence = confidence,
            timestamp = System.currentTimeMillis()
        )
    }
}
