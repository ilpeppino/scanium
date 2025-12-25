package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.app.model.ItemAttributeSnapshot
import com.scanium.app.model.ConfidenceTier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalAssistantHelperTest {
    private val helper = LocalAssistantHelper()

    @Test
    fun brandQuestion_withoutAttributes_returnsCannotConfirm() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Vintage Lamp",
            description = null,
            category = "home decor",
            confidence = 0.6f,
            attributes = emptyList(),
            priceEstimate = 30.0,
            photosCount = 1
        )

        val response = helper.buildResponse(listOf(snapshot), "What brand is this?")

        assertThat(response.text.lowercase()).contains("can't confirm")
        assertThat(response.confidenceTier).isEqualTo(ConfidenceTier.LOW)
    }

    @Test
    fun categoryQuestion_usesSnapshotCategory() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Laptop",
            description = null,
            category = "Electronics",
            confidence = 0.8f,
            attributes = emptyList(),
            priceEstimate = null,
            photosCount = 2
        )

        val response = helper.buildResponse(listOf(snapshot), "What category is it?")

        assertThat(response.text).contains("Electronics")
        assertThat(response.confidenceTier).isEqualTo(ConfidenceTier.HIGH)
    }

    @Test
    fun checklistReturned_forCategory() {
        val snapshot = ItemContextSnapshot(
            itemId = "item-1",
            title = "Camera",
            description = null,
            category = "electronics",
            confidence = 0.7f,
            attributes = listOf(ItemAttributeSnapshot(key = "brand", value = "Canon")),
            priceEstimate = 100.0,
            photosCount = 1
        )

        val response = helper.buildResponse(listOf(snapshot), "Give me a checklist")

        assertThat(response.text).contains("checklist")
        assertThat(response.actions).isNotEmpty()
    }
}
