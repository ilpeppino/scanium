package com.scanium.app.assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TTS text sanitizer (buildSpeakableText).
 *
 * Verifies that:
 * - Only AI-generated content is included (no labels)
 * - Title, description, and bullets are properly combined
 * - Empty content returns empty string
 * - Whitespace is handled correctly
 */
class TtsControllerTest {

    @Test
    fun `buildSpeakableText with all fields returns combined text`() {
        val title = "Vintage Nike Sneakers"
        val description = "Classic Nike Air Max 90 in excellent condition. Original box included."
        val bullets = listOf(
            "Size 10 US",
            "Authentic Nike product",
            "No visible wear",
        )

        val result = buildSpeakableText(title, description, bullets)

        // Should contain all parts
        assertTrue(result.contains(title))
        assertTrue(result.contains(description))
        assertTrue(result.contains("Size 10 US"))
        assertTrue(result.contains("Authentic Nike product"))
        assertTrue(result.contains("No visible wear"))

        // Should have natural pauses (double newlines between sections)
        assertTrue(result.contains("\n\n"))
    }

    @Test
    fun `buildSpeakableText with only title returns title`() {
        val title = "Vintage Nike Sneakers"

        val result = buildSpeakableText(title, null, emptyList())

        assertEquals(title, result)
    }

    @Test
    fun `buildSpeakableText with only description returns description`() {
        val description = "Classic Nike Air Max 90 in excellent condition."

        val result = buildSpeakableText(null, description, emptyList())

        assertEquals(description, result)
    }

    @Test
    fun `buildSpeakableText with only bullets returns bullets`() {
        val bullets = listOf("Size 10 US", "Authentic Nike product")

        val result = buildSpeakableText(null, null, bullets)

        assertTrue(result.contains("Size 10 US"))
        assertTrue(result.contains("Authentic Nike product"))
        assertTrue(result.contains("\n")) // Bullets separated by newlines
    }

    @Test
    fun `buildSpeakableText with empty content returns empty string`() {
        val result = buildSpeakableText(null, null, emptyList())

        assertEquals("", result)
    }

    @Test
    fun `buildSpeakableText with blank content returns empty string`() {
        val result = buildSpeakableText("  ", "  ", listOf("  ", ""))

        assertEquals("", result)
    }

    @Test
    fun `buildSpeakableText trims whitespace`() {
        val title = "  Sneakers  "
        val description = "  Nice shoes  "
        val bullets = listOf("  Size 10  ")

        val result = buildSpeakableText(title, description, bullets)

        assertTrue(result.contains("Sneakers"))
        assertTrue(result.contains("Nice shoes"))
        assertTrue(result.contains("Size 10"))
        // Should not have leading/trailing spaces
        assertEquals(result, result.trim())
    }

    @Test
    fun `buildSpeakableText does not include UI labels`() {
        // This is the key test: the function should only include AI content
        // No labels like "Title:", "Description:", "Highlights:" should appear
        val title = "Test Title"
        val description = "Test Description"
        val bullets = listOf("Bullet 1", "Bullet 2")

        val result = buildSpeakableText(title, description, bullets)

        // Should not contain common label patterns
        assertTrue(!result.contains("Title:"))
        assertTrue(!result.contains("Description:"))
        assertTrue(!result.contains("Highlights:"))
        assertTrue(!result.contains("Bullet:"))

        // Should only contain the actual content
        assertTrue(result.contains(title))
        assertTrue(result.contains(description))
        assertTrue(result.contains("Bullet 1"))
        assertTrue(result.contains("Bullet 2"))
    }

    @Test
    fun `buildSpeakableText handles special characters`() {
        val title = "Item with €100 price & 50% discount"
        val description = "Features: USB-C, Wi-Fi 6, Bluetooth 5.0"
        val bullets = listOf("Color: Blue/Green", "Model: XYZ-123")

        val result = buildSpeakableText(title, description, bullets)

        // Special characters should be preserved
        assertTrue(result.contains("€100"))
        assertTrue(result.contains("&"))
        assertTrue(result.contains("50%"))
        assertTrue(result.contains("USB-C"))
        assertTrue(result.contains("Blue/Green"))
        assertTrue(result.contains("XYZ-123"))
    }

    @Test
    fun `buildSpeakableText handles multi-line description`() {
        val title = "Test Item"
        val description = "First paragraph.\n\nSecond paragraph with more details."
        val bullets = listOf("Feature 1")

        val result = buildSpeakableText(title, description, bullets)

        assertTrue(result.contains("First paragraph"))
        assertTrue(result.contains("Second paragraph"))
        // Multi-line content should be preserved
        assertTrue(result.contains(description))
    }
}
