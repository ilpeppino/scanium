package com.scanium.app.model

import com.scanium.shared.core.models.model.ImageRef
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageRefTest {

    @Test
    fun bytesVariant_acceptsValidInput() {
        val bytes = ByteArray(4) { it.toByte() }

        val ref = ImageRef.Bytes(bytes, mimeType = "image/png", width = 100, height = 200)

        assertEquals("image/png", ref.mimeType)
        assertEquals(100, ref.width)
        assertEquals(200, ref.height)
        assertArrayEquals(bytes, ref.bytes)
    }

    @Test
    fun bytesVariant_rejectsEmptyPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageRef.Bytes(ByteArray(0), mimeType = "image/png", width = 1, height = 1)
        }
    }

    @Test
    fun bytesVariant_rejectsBlankMimeType() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageRef.Bytes(ByteArray(1) { 1 }, mimeType = " ", width = 1, height = 1)
        }
    }

    @Test
    fun bytesVariant_rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageRef.Bytes(ByteArray(1) { 1 }, mimeType = "image/jpeg", width = 0, height = 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ImageRef.Bytes(ByteArray(1) { 1 }, mimeType = "image/jpeg", width = 10, height = -1)
        }
    }
}
