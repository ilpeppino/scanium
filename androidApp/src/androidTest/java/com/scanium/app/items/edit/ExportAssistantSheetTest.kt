package com.scanium.app.items.edit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scanium.shared.core.models.assistant.ConfidenceTier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for ExportAssistantSheet content cards.
 *
 * Tests verify:
 * - Long generated text is fully scrollable within content cards
 * - Content doesn't get cut off with ellipsis
 * - Text is accessible in the composition tree (not truncated)
 */
@RunWith(AndroidJUnit4::class)
class ExportAssistantContentCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenLongTextProvided_thenAllContentIsAccessible() {
        // Arrange - Create a very long text that exceeds typical visible area
        val longText = """
            This is a very long text content that should be scrollable within the card.
            Line 1: This item is in excellent condition with minimal wear and tear.
            Line 2: It features premium materials and exceptional craftsmanship throughout.
            Line 3: Perfect for collectors and enthusiasts who appreciate quality items.
            Line 4: Has been carefully stored in a smoke-free, pet-free environment.
            Line 5: All original parts, accessories, and documentation are included.
            Line 6: Rare color variant that's increasingly hard to find in this condition.
            Line 7: Comes from a reputable manufacturer known for quality and durability.
            Line 8: Ideal gift for special occasions, celebrations, or personal enjoyment.
            Line 9: Functional design that combines aesthetics with practical features.
            Line 10: Ships carefully packaged with tracking and insurance for your peace of mind.
            Line 11: This is the last line that proves text is not truncated with ellipsis.
        """.trimIndent()

        // Act - Render the ExportContentCard with long text
        composeTestRule.setContent {
            ExportContentCard(
                label = "Description",
                content = longText,
                onCopy = {},
                maxLines = 10
            )
        }

        // Assert - Beginning of text should be visible
        composeTestRule.onNodeWithText("This is a very long text", substring = true)
            .assertIsDisplayed()

        // Assert - Middle content should exist in the tree (may or may not be visible depending on scroll)
        composeTestRule.onNodeWithText("Line 5:", substring = true)
            .assertExists()

        // Assert - End content should exist (proving no ellipsis truncation)
        // This is the key test - before the fix, this would fail because maxLines
        // would cause TextOverflow.Ellipsis to cut off the text
        composeTestRule.onNodeWithText("Line 11:", substring = true)
            .assertExists()

        composeTestRule.onNodeWithText("proves text is not truncated", substring = true)
            .assertExists()
    }

    @Test
    fun whenShortTextProvided_thenAllContentIsVisible() {
        // Arrange
        val shortText = "A brief description that fits easily within the visible area."

        // Act
        composeTestRule.setContent {
            ExportContentCard(
                label = "Title",
                content = shortText,
                onCopy = {},
                maxLines = 3
            )
        }

        // Assert - Short text should be fully displayed
        composeTestRule.onNodeWithText(shortText)
            .assertIsDisplayed()
    }

    @Test
    fun whenMultiParagraphText_thenAllParagraphsAreAccessible() {
        // Arrange - Multi-paragraph text like a description
        val multiParagraphText = """
            First paragraph describes the item's main features and benefits to the buyer.
            This paragraph should be fully accessible in the UI.

            Second paragraph provides additional details about condition and specifications.
            It includes important information that buyers need to know before purchasing.

            Third paragraph mentions shipping, returns, and other practical details.
            All of this content must be accessible, not cut off by ellipsis.
        """.trimIndent()

        // Act
        composeTestRule.setContent {
            ExportContentCard(
                label = "Description",
                content = multiParagraphText,
                onCopy = {},
                maxLines = 10
            )
        }

        // Assert - All three paragraphs should be accessible
        composeTestRule.onNodeWithText("First paragraph", substring = true)
            .assertExists()

        composeTestRule.onNodeWithText("Second paragraph", substring = true)
            .assertExists()

        composeTestRule.onNodeWithText("Third paragraph", substring = true)
            .assertExists()

        // The key assertion - last sentence should exist (not truncated)
        composeTestRule.onNodeWithText("not cut off by ellipsis", substring = true)
            .assertExists()
    }

    @Test
    fun whenBulletsCardWithManyBullets_thenAllBulletsAreDisplayed() {
        // Arrange
        val bullets = listOf(
            "Premium quality materials and construction",
            "Excellent condition with minimal signs of use",
            "Rare collectible item with increasing value",
            "Original packaging and all accessories included",
            "Authenticated and verified by experts",
            "Limited edition variant",
            "Comes from smoke-free environment"
        )

        // Act
        composeTestRule.setContent {
            ExportBulletsCard(
                bullets = bullets,
                onCopy = {}
            )
        }

        // Assert - All bullets should be displayed (not truncated)
        bullets.forEach { bullet ->
            composeTestRule.onNodeWithText(bullet, substring = true)
                .assertExists()
        }

        // Specifically check first and last bullets to ensure no truncation
        composeTestRule.onNodeWithText("Premium quality", substring = true)
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("smoke-free environment", substring = true)
            .assertExists()
    }
}
