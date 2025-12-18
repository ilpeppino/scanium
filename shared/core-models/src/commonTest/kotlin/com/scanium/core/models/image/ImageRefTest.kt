package com.scanium.core.models.image

import com.scanium.shared.core.models.model.ImageRef
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageRefTest {

    @Test
    fun bytesVariant_acceptsValidInput() {
        val bytes = ByteArray(4) { it.toByte() }

        val ref = ImageRef.Bytes(bytes, mimeType = "image/png", width = 100, height = 200)

        assertEquals("image/png", ref.mimeType)
        assertEquals(100, ref.width)
        assertEquals(200, ref.height)
        assertContentEquals(bytes, ref.bytes)
    }

    @Test
    fun bytesVariant_rejectsEmptyPayload() {
        assertFailsWith<IllegalArgumentException> {
            ImageRef.Bytes(ByteArray(0), mimeType = "image/png", width = 1, height = 1)
        }
    }

    @Test
    fun bytesVariant_rejectsBlankMimeType() {
        assertFailsWith<IllegalArgumentException> {
            ImageRef.Bytes(ByteArray(1) { 1 }, mimeType = " ", width = 1, height = 1)
        }
    }

    @Test
    fun bytesVariant_rejectsNonPositiveDimensions() {
        assertFailsWith<IllegalArgumentException> {
            ImageRef.Bytes(ByteArray(1) { 1 }, mimeType = "image/jpeg", width = 0, height = 10)
        }

        assertFailsWith<IllegalArgumentException> {
            ImageRef.Bytes(ByteArray(1) { 1 }, mimeType = "image/jpeg", width = 10, height = -1)
        }
    }
}
