package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.model.AssistantActionType
import com.scanium.app.model.AssistantMessage
import com.scanium.app.model.AssistantPrefs
import com.scanium.app.model.AssistantRegion
import com.scanium.app.model.AssistantRole
import com.scanium.app.model.AssistantTone
import com.scanium.app.model.AssistantUnits
import com.scanium.app.model.AssistantVerbosity
import com.scanium.app.model.AttributeSource
import com.scanium.app.model.ItemAttributeSnapshot
import com.scanium.app.model.ItemContextSnapshot
import com.scanium.shared.core.models.listing.ExportDescriptionRules
import com.scanium.shared.core.models.listing.ExportProfileId
import com.scanium.shared.core.models.listing.ExportTitleRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Test

class AssistantContractTest {

    private val json = Json

    @Test
    fun `request payload matches contract fixture`() {
        val actual = json.parseToJsonElement(
            AssistantContractCodec.encodeRequest(
                items = listOf(createItemSnapshot()),
                history = listOf(
                    AssistantMessage(
                        role = AssistantRole.USER,
                        content = "What is this?",
                        timestamp = 1704067200000L,
                        itemContextIds = listOf("item-123"),
                    ),
                ),
                userMessage = "Generate a listing",
                exportProfile = createExportProfile(),
                assistantPrefs = AssistantPrefs(
                    language = "en",
                    tone = AssistantTone.MARKETPLACE,
                    region = AssistantRegion.NL,
                    units = AssistantUnits.METRIC,
                    verbosity = AssistantVerbosity.CONCISE,
                ),
                includePricing = true,
                pricingCountryCode = "NL",
            ),
        )

        val expected = loadJsonFixture("assistant_contract/assistant_request.json")

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `response fixture parses into assistant model`() {
        val responseJson = loadJsonFixtureText("assistant_contract/assistant_response.json")
        val response = AssistantContractCodec.decodeResponse(responseJson)

        assertThat(response.reply).isEqualTo("Suggested title: Vintage Brass Lamp")
        assertThat(response.actions).hasSize(1)
        assertThat(response.actions.first().type).isEqualTo(AssistantActionType.COPY_TEXT)
        assertThat(response.suggestedAttributes.first().key).isEqualTo("brand")
        assertThat(response.suggestedDraftUpdates.first().field).isEqualTo("title")
        assertThat(response.marketPrice?.status).isEqualTo("OK")
        assertThat(response.marketPrice?.countryCode).isEqualTo("NL")
        assertThat(response.marketPrice?.results?.first()?.price?.amount).isEqualTo(125.0)
        assertThat(response.marketPrice?.range?.high).isEqualTo(150.0)
    }

    private fun createItemSnapshot(): ItemContextSnapshot {
        return ItemContextSnapshot(
            itemId = "item-123",
            title = "Vintage Lamp",
            description = "Brass lamp with shade",
            category = "Lighting",
            confidence = 0.92f,
            attributes = listOf(
                ItemAttributeSnapshot(
                    key = "brand",
                    value = "Acme",
                    confidence = 0.77f,
                    source = AttributeSource.USER,
                ),
                ItemAttributeSnapshot(
                    key = "color",
                    value = "Brass",
                    confidence = 0.66f,
                    source = AttributeSource.DETECTED,
                ),
            ),
            priceEstimate = 120.5,
            photosCount = 2,
            exportProfileId = ExportProfileId.GENERIC,
        )
    }

    private fun createExportProfile(): ExportProfileDefinition {
        return ExportProfileDefinition(
            id = ExportProfileId.GENERIC,
            displayName = "Generic",
            titleRules = ExportTitleRules(),
            descriptionRules = ExportDescriptionRules(),
        )
    }

    private fun loadJsonFixture(path: String): JsonElement {
        return json.parseToJsonElement(loadJsonFixtureText(path))
    }

    private fun loadJsonFixtureText(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Missing test fixture: $path")
        return stream.use {
            it.bufferedReader().readText()
        }
    }
}
