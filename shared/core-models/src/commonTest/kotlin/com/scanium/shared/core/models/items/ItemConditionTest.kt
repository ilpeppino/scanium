package com.scanium.shared.core.models.items

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for ItemCondition enum.
 *
 * Note: Display names and descriptions are localized via ItemLocalizer
 * in the Android app layer, not in this shared module.
 */
class ItemConditionTest {
    @Test
    fun whenEnumeratingConditions_thenAllSevenValuesPresent() {
        val names = ItemCondition.entries.map { it.name }

        assertEquals(
            listOf(
                "NEW_SEALED",
                "NEW_WITH_TAGS",
                "NEW_WITHOUT_TAGS",
                "LIKE_NEW",
                "GOOD",
                "FAIR",
                "POOR",
            ),
            names,
        )
    }
}
