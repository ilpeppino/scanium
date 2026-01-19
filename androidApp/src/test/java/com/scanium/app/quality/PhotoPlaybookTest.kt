package com.scanium.app.quality

import com.scanium.app.ItemCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PhotoPlaybook.
 *
 * Tests:
 * - Shot recommendations based on category
 * - Filtering by missing attributes
 * - Filtering by already taken shots
 * - Shot type inference from extracted attributes
 */
class PhotoPlaybookTest {
    @Test
    fun `fashion category has brand and size shots`() {
        val playbook = PhotoPlaybook.getPlaybookForCategory(ItemCategory.FASHION)

        assertTrue(playbook.any { it.shotType == PhotoPlaybook.ShotType.BRAND_LABEL })
        assertTrue(playbook.any { it.shotType == PhotoPlaybook.ShotType.SIZE_TAG })
        assertTrue(playbook.any { it.shotType == PhotoPlaybook.ShotType.FULL_ITEM })
    }

    @Test
    fun `electronics category has model number shot`() {
        val playbook = PhotoPlaybook.getPlaybookForCategory(ItemCategory.ELECTRONICS)

        assertTrue(playbook.any { it.shotType == PhotoPlaybook.ShotType.MODEL_NUMBER })
    }

    @Test
    fun `guidance filters by missing attributes`() {
        val guidance =
            PhotoPlaybook.getGuidance(
                category = ItemCategory.FASHION,
                missingAttributes = listOf("brand"),
                currentPhotoCount = 0,
                takenShotTypes = emptySet(),
            )

        // Should recommend brand label shot
        assertTrue(
            guidance.recommendedShots.any {
                it.shotType == PhotoPlaybook.ShotType.BRAND_LABEL
            },
        )
    }

    @Test
    fun `guidance excludes already taken shots`() {
        val takenShots = setOf(PhotoPlaybook.ShotType.FULL_ITEM, PhotoPlaybook.ShotType.BRAND_LABEL)

        val guidance =
            PhotoPlaybook.getGuidance(
                category = ItemCategory.FASHION,
                missingAttributes = listOf("brand", "size", "condition"),
                currentPhotoCount = 2,
                takenShotTypes = takenShots,
            )

        // Should not recommend already taken shots
        assertFalse(
            guidance.recommendedShots.any {
                it.shotType == PhotoPlaybook.ShotType.FULL_ITEM
            },
        )
        assertFalse(
            guidance.recommendedShots.any {
                it.shotType == PhotoPlaybook.ShotType.BRAND_LABEL
            },
        )
    }

    @Test
    fun `guidance returns primary instruction`() {
        val guidance =
            PhotoPlaybook.getGuidance(
                category = ItemCategory.FASHION,
                missingAttributes = listOf("brand"),
                currentPhotoCount = 0,
                takenShotTypes = emptySet(),
            )

        assertTrue(guidance.primaryGuidance.isNotBlank())
    }

    @Test
    fun `getNextPhotoHint returns first available hint`() {
        val hint =
            PhotoPlaybook.getNextPhotoHint(
                category = ItemCategory.FASHION,
                missingAttributes = listOf("brand", "size"),
                takenShotTypes = emptySet(),
            )

        assertNotNull(hint)
        assertTrue(hint!!.isNotBlank())
    }

    @Test
    fun `getNextPhotoHint returns null when all shots taken`() {
        val allShotTypes = PhotoPlaybook.ShotType.entries.toSet()

        val hint =
            PhotoPlaybook.getNextPhotoHint(
                category = ItemCategory.FASHION,
                missingAttributes = listOf("brand"),
                takenShotTypes = allShotTypes,
            )

        assertNull(hint)
    }

    @Test
    fun `inferShotType detects brand label from extracted attributes`() {
        val extractedAttrs = setOf("brand")

        val shotType =
            PhotoPlaybook.inferShotType(
                category = ItemCategory.FASHION,
                extractedAttributes = extractedAttrs,
            )

        assertEquals(PhotoPlaybook.ShotType.BRAND_LABEL, shotType)
    }

    @Test
    fun `inferShotType detects full item from multiple attributes`() {
        val extractedAttrs = setOf("itemType", "color", "style")

        val shotType =
            PhotoPlaybook.inferShotType(
                category = ItemCategory.FASHION,
                extractedAttributes = extractedAttrs,
            )

        assertEquals(PhotoPlaybook.ShotType.FULL_ITEM, shotType)
    }

    @Test
    fun `inferShotType returns null for no matches`() {
        val extractedAttrs = setOf("unknownAttr")

        val shotType =
            PhotoPlaybook.inferShotType(
                category = ItemCategory.FASHION,
                extractedAttributes = extractedAttrs,
            )

        assertNull(shotType)
    }

    @Test
    fun `shots are ordered by priority`() {
        val playbook = PhotoPlaybook.getPlaybookForCategory(ItemCategory.FASHION)

        // First shot should have priority 1
        assertEquals(1, playbook.first().priority)

        // Each subsequent shot should have equal or higher priority
        for (i in 1 until playbook.size) {
            assertTrue(playbook[i].priority >= playbook[i - 1].priority)
        }
    }

    @Test
    fun `each shot type has icon defined`() {
        val playbook = PhotoPlaybook.getPlaybookForCategory(ItemCategory.FASHION)

        playbook.forEach { shot ->
            assertTrue("Shot ${shot.shotType} should have icon", shot.icon.isNotBlank())
        }
    }

    @Test
    fun `guidance hasMoreRecommendations when shots available`() {
        val guidance =
            PhotoPlaybook.getGuidance(
                category = ItemCategory.FASHION,
                missingAttributes = listOf("brand", "size"),
                currentPhotoCount = 0,
                takenShotTypes = emptySet(),
            )

        assertTrue(guidance.hasMoreRecommendations)
        assertNotNull(guidance.nextShot)
    }

    @Test
    fun `unknown category uses default playbook`() {
        val playbook = PhotoPlaybook.getPlaybookForCategory(ItemCategory.UNKNOWN)

        assertTrue(playbook.isNotEmpty())
        assertTrue(playbook.any { it.shotType == PhotoPlaybook.ShotType.FULL_ITEM })
    }
}
