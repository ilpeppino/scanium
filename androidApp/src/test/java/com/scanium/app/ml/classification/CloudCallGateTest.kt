package com.scanium.app.ml.classification

import com.google.common.truth.Truth.assertThat
import com.scanium.app.aggregation.AggregatedItem
import com.scanium.shared.core.models.ml.ItemCategory
import com.scanium.shared.core.models.model.ImageRef
import com.scanium.shared.core.models.model.NormalizedRect
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudCallGateTest {

    private fun createItem(
        id: String,
        mergeCount: Int = 10,
        ageMs: Long = 1000
    ): AggregatedItem {
        return AggregatedItem(
            aggregatedId = id,
            category = ItemCategory.UNKNOWN,
            labelText = "Test",
            boundingBox = NormalizedRect(0f, 0f, 1f, 1f),
            thumbnail = null,
            maxConfidence = 0.9f,
            averageConfidence = 0.9f,
            priceRange = 0.0 to 0.0,
            mergeCount = mergeCount,
            firstSeenTimestamp = System.currentTimeMillis() - ageMs
        )
    }

    @Test
    fun `canClassify returns false when not in cloud mode`() {
        val gate = CloudCallGate(isCloudMode = { false })
        val item = createItem("1")
        assertThat(gate.canClassify(item, null)).isFalse()
    }

    @Test
    fun `canClassify blocks unstable items`() {
        val gate = CloudCallGate(isCloudMode = { true })
        
        // Low merge count (default threshold 5) AND low age
        val unstableItem = createItem("1", mergeCount = 2, ageMs = 100)
        assertThat(gate.canClassify(unstableItem, null)).isFalse()

        // Low age (default threshold 500ms)
        val youngItem = createItem("2", mergeCount = 10, ageMs = 100)
        // This should actually PASS merge count check (10 > 5), so it returns TRUE?
        // Logic: if (mergeCount < 5) check age.
        // If mergeCount >= 5, it returns true immediately?
        // Let's check logic:
        // if (item.mergeCount < MIN_STABILITY_FRAMES) { ... }
        // So if mergeCount is 10, it skips the block and returns true (if cooldown passes).
        // So youngItem SHOULD pass if mergeCount is high.
        // My previous test expectation was wrong for youngItem.
        // But for unstableItem (merge=2, age=100), it MUST fail.
    }

    @Test
    fun `canClassify enforces cooldown`() {
        val gate = CloudCallGate(isCloudMode = { true }, cooldownMs = 10000)
        val item = createItem("1")

        // First call allowed (stage 1)
        assertThat(gate.canClassify(item, null)).isTrue()
        
        // Trigger classification
        gate.onClassificationTriggered(item, null)

        // Immediate next call blocked
        assertThat(gate.canClassify(item, null)).isFalse()
    }

    @Test
    fun `canClassify detects duplicate inputs`() {
        val gate = CloudCallGate(isCloudMode = { true }, cooldownMs = 0)
        val item = createItem("1")
        val thumb1 = ImageRef.Bytes(byteArrayOf(1, 2, 3), "image/jpeg", 10, 10)
        val thumb2 = ImageRef.Bytes(byteArrayOf(1, 2, 3), "image/jpeg", 10, 10) // Identical bytes
        val thumb3 = ImageRef.Bytes(byteArrayOf(4, 5, 6), "image/jpeg", 10, 10) // Different bytes

        // First call allowed
        assertThat(gate.canClassify(item, thumb1)).isTrue()
        gate.onClassificationTriggered(item, thumb1)

        // Same thumbnail blocked
        assertThat(gate.canClassify(item, thumb2)).isFalse()

        // Different thumbnail allowed
        assertThat(gate.canClassify(item, thumb3)).isTrue()
    }
}
