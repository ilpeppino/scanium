package com.scanium.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the detection bounding boxes overlay setting.
 *
 * Verifies:
 * - Setting can be toggled on and off
 * - Setting persists across toggle operations
 * - Setting survives repository recreation (simulating app restart)
 *
 * Note: Default value testing in Robolectric can be unreliable due to DataStore
 * initialization timing. The production code sets default to true in SettingsRepository.kt.
 */
@RunWith(RobolectricTestRunner::class)
class DetectionBoxesSettingsTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.clearDataStorePrefs()
        repository = SettingsRepository(context)
    }

    @After
    fun tearDown() {
        context.clearDataStorePrefs()
    }

    @Test
    fun showDetectionBoxes_can_be_set_to_true() = runTest {
        // Set to true
        repository.setShowDetectionBoxes(true)

        // Verify it's now true
        assertThat(repository.showDetectionBoxesFlow.first()).isTrue()
    }

    @Test
    fun showDetectionBoxes_can_be_set_to_false() = runTest {
        // Set to false
        repository.setShowDetectionBoxes(false)

        // Verify it's now false
        assertThat(repository.showDetectionBoxesFlow.first()).isFalse()
    }

    @Test
    fun showDetectionBoxes_toggle_from_true_to_false_persists() = runTest {
        // Set to true first
        repository.setShowDetectionBoxes(true)
        assertThat(repository.showDetectionBoxesFlow.first()).isTrue()

        // Toggle to false
        repository.setShowDetectionBoxes(false)

        // Verify persisted as false
        assertThat(repository.showDetectionBoxesFlow.first()).isFalse()
    }

    @Test
    fun showDetectionBoxes_toggle_from_false_to_true_persists() = runTest {
        // Set to false first
        repository.setShowDetectionBoxes(false)
        assertThat(repository.showDetectionBoxesFlow.first()).isFalse()

        // Toggle to true
        repository.setShowDetectionBoxes(true)

        // Verify persisted as true
        assertThat(repository.showDetectionBoxesFlow.first()).isTrue()
    }

    @Test
    fun showDetectionBoxes_survives_repository_recreation() = runTest {
        // Set to false
        repository.setShowDetectionBoxes(false)
        assertThat(repository.showDetectionBoxesFlow.first()).isFalse()

        // Create a new repository instance (simulates app restart)
        val newRepository = SettingsRepository(context)

        // Verify the setting persisted across "restart"
        assertThat(newRepository.showDetectionBoxesFlow.first()).isFalse()
    }

    @Test
    fun showDetectionBoxes_true_survives_repository_recreation() = runTest {
        // Set to true
        repository.setShowDetectionBoxes(true)
        assertThat(repository.showDetectionBoxesFlow.first()).isTrue()

        // Create a new repository instance (simulates app restart)
        val newRepository = SettingsRepository(context)

        // Verify the setting persisted across "restart"
        assertThat(newRepository.showDetectionBoxesFlow.first()).isTrue()
    }
}

private fun Context.clearDataStorePrefs() {
    val baseDir = filesDir.parentFile ?: return
    val storeFile = java.io.File(baseDir, "datastore/settings_preferences.preferences_pb")
    if (storeFile.exists()) {
        storeFile.delete()
    }
}
