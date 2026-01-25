package com.scanium.app.pricing

import com.scanium.shared.core.models.items.ItemCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingUiStateTest {
    @Test
    fun `missingFields returns required fields`() {
        val inputs = PricingInputs(brand = "", productType = "electronics", model = "", condition = null)
        val missing = inputs.missingFields()

        assertTrue(missing.contains(PricingMissingField.BRAND))
        assertTrue(missing.contains(PricingMissingField.MODEL))
        assertTrue(missing.contains(PricingMissingField.CONDITION))
        assertFalse(missing.contains(PricingMissingField.PRODUCT_TYPE))
    }

    @Test
    fun `matches normalizes whitespace and case`() {
        val left =
            PricingInputs(
                brand = "  Philips ",
                productType = "Electronics",
                model = "3200 Series",
                condition = ItemCondition.GOOD,
            )
        val right =
            PricingInputs(
                brand = "philips",
                productType = "electronics",
                model = "3200 series",
                condition = ItemCondition.GOOD,
            )

        assertTrue(left.matches(right))
    }

    @Test
    fun `isStaleComparedTo detects changes`() {
        val snapshot =
            PricingInputs(
                brand = "Philips",
                productType = "coffee_machine",
                model = "3200",
                condition = ItemCondition.GOOD,
            )
        val current = snapshot.copy(model = "3300")

        assertTrue(current.isStaleComparedTo(snapshot))
        assertFalse(snapshot.isStaleComparedTo(snapshot))
    }

    @Test
    fun `isComplete requires all fields`() {
        val complete =
            PricingInputs(
                brand = "Philips",
                productType = "coffee_machine",
                model = "3200",
                condition = ItemCondition.GOOD,
            )
        val incomplete = complete.copy(model = "")

        assertTrue(complete.isComplete())
        assertFalse(incomplete.isComplete())
        assertEquals(setOf(PricingMissingField.MODEL), incomplete.missingFields())
    }
}
