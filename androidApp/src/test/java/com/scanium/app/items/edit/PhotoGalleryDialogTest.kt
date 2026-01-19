package com.scanium.app.items.edit

import com.google.common.truth.Truth.assertThat
import com.scanium.shared.core.models.model.ImageRef
import org.junit.Test

/**
 * Unit tests for PhotoGalleryDialog data structures.
 *
 * Tests verify:
 * - GalleryPhotoRef types are correctly constructed
 * - Photo list indexing works as expected
 * - Index coercion logic (for out-of-bounds values)
 */
class PhotoGalleryDialogTest {
    @Test
    fun `GalleryPhotoRef FromImageRef creates correct type`() {
        // Arrange
        val imageRef =
            ImageRef.Bytes(
                bytes = byteArrayOf(1, 2, 3),
                mimeType = "image/jpeg",
                width = 100,
                height = 100,
            )

        // Act
        val photoRef = GalleryPhotoRef.FromImageRef(imageRef)

        // Assert
        assertThat(photoRef).isInstanceOf(GalleryPhotoRef.FromImageRef::class.java)
        assertThat((photoRef as GalleryPhotoRef.FromImageRef).imageRef).isEqualTo(imageRef)
    }

    @Test
    fun `GalleryPhotoRef FromFilePath creates correct type`() {
        // Arrange
        val path = "/path/to/photo.jpg"

        // Act
        val photoRef = GalleryPhotoRef.FromFilePath(path)

        // Assert
        assertThat(photoRef).isInstanceOf(GalleryPhotoRef.FromFilePath::class.java)
        assertThat((photoRef as GalleryPhotoRef.FromFilePath).path).isEqualTo(path)
    }

    @Test
    fun `photo list with multiple items has correct size`() {
        // Arrange
        val photos = createTestPhotos(count = 5)

        // Assert
        assertThat(photos).hasSize(5)
    }

    @Test
    fun `initial index zero is valid for non-empty list`() {
        // Arrange
        val photos = createTestPhotos(count = 3)
        val initialIndex = 0

        // Act
        val safeIndex = initialIndex.coerceIn(0, photos.lastIndex)

        // Assert
        assertThat(safeIndex).isEqualTo(0)
    }

    @Test
    fun `initial index at last position is valid`() {
        // Arrange
        val photos = createTestPhotos(count = 5)
        val initialIndex = 4

        // Act
        val safeIndex = initialIndex.coerceIn(0, photos.lastIndex)

        // Assert
        assertThat(safeIndex).isEqualTo(4)
    }

    @Test
    fun `out of bounds initial index is coerced to last index`() {
        // Arrange
        val photos = createTestPhotos(count = 3)
        val initialIndex = 10 // Out of bounds

        // Act
        val safeIndex = initialIndex.coerceIn(0, photos.lastIndex)

        // Assert
        assertThat(safeIndex).isEqualTo(2) // Should be last index (size - 1)
    }

    @Test
    fun `negative initial index is coerced to zero`() {
        // Arrange
        val photos = createTestPhotos(count = 3)
        val initialIndex = -5

        // Act
        val safeIndex = initialIndex.coerceIn(0, photos.lastIndex)

        // Assert
        assertThat(safeIndex).isEqualTo(0)
    }

    @Test
    fun `empty photo list has size zero`() {
        // Arrange
        val photos = emptyList<GalleryPhotoRef>()

        // Assert
        assertThat(photos).isEmpty()
    }

    @Test
    fun `single photo list has correct size and index`() {
        // Arrange
        val photos = createTestPhotos(count = 1)

        // Assert
        assertThat(photos).hasSize(1)
        assertThat(photos.lastIndex).isEqualTo(0)
    }

    @Test
    fun `page indicator calculation is correct`() {
        // Arrange
        val currentPage = 2 // Zero-indexed
        val totalPages = 5

        // Act - Calculate display values (1-indexed)
        val displayCurrent = currentPage + 1
        val displayTotal = totalPages

        // Assert
        assertThat(displayCurrent).isEqualTo(3)
        assertThat(displayTotal).isEqualTo(5)
    }

    // ==================== Test Helpers ====================

    /**
     * Creates a list of test photos using GalleryPhotoRef.FromFilePath.
     */
    private fun createTestPhotos(count: Int): List<GalleryPhotoRef> {
        return List(count) { index ->
            GalleryPhotoRef.FromFilePath("/test/photo$index.jpg")
        }
    }
}
