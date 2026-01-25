package com.scanium.shared.core.models.items

import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun whenReadingConditionMetadata_thenDisplayNameAndDescriptionMatch() {
        val condition = ItemCondition.NEW_SEALED

        assertEquals("New, Sealed", condition.displayName)
        assertEquals("Factory sealed, never opened", condition.description)
    }
}
