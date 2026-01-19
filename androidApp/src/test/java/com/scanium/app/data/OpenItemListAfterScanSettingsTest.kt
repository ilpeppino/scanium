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
 * Unit tests for the auto-open item list after scan setting.
 *
 * Verifies:
 * - Default value is false (disabled by default)
 * - Setting can be toggled on and off
 * - Setting persists across toggle operations
 * - Setting survives repository recreation (simulating app restart)
 */
@RunWith(RobolectricTestRunner::class)
class OpenItemListAfterScanSettingsTest {
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
    fun openItemListAfterScan_defaults_to_false() =
        runTest {
            // Verify default is false (disabled by default)
            assertThat(repository.openItemListAfterScanFlow.first()).isFalse()
        }

    @Test
    fun openItemListAfterScan_can_be_set_to_true() =
        runTest {
            // Set to true
            repository.setOpenItemListAfterScan(true)

            // Verify it's now true
            assertThat(repository.openItemListAfterScanFlow.first()).isTrue()
        }

    @Test
    fun openItemListAfterScan_can_be_set_to_false() =
        runTest {
            // Set to false explicitly
            repository.setOpenItemListAfterScan(false)

            // Verify it's now false
            assertThat(repository.openItemListAfterScanFlow.first()).isFalse()
        }

    @Test
    fun openItemListAfterScan_toggle_from_true_to_false_persists() =
        runTest {
            // Set to true first
            repository.setOpenItemListAfterScan(true)
            assertThat(repository.openItemListAfterScanFlow.first()).isTrue()

            // Toggle to false
            repository.setOpenItemListAfterScan(false)

            // Verify persisted as false
            assertThat(repository.openItemListAfterScanFlow.first()).isFalse()
        }

    @Test
    fun openItemListAfterScan_toggle_from_false_to_true_persists() =
        runTest {
            // Set to false first (explicit set, even though default is false)
            repository.setOpenItemListAfterScan(false)
            assertThat(repository.openItemListAfterScanFlow.first()).isFalse()

            // Toggle to true
            repository.setOpenItemListAfterScan(true)

            // Verify persisted as true
            assertThat(repository.openItemListAfterScanFlow.first()).isTrue()
        }

    @Test
    fun openItemListAfterScan_false_survives_repository_recreation() =
        runTest {
            // Set to false
            repository.setOpenItemListAfterScan(false)
            assertThat(repository.openItemListAfterScanFlow.first()).isFalse()

            // Create a new repository instance (simulates app restart)
            val newRepository = SettingsRepository(context)

            // Verify the setting persisted across "restart"
            assertThat(newRepository.openItemListAfterScanFlow.first()).isFalse()
        }

    @Test
    fun openItemListAfterScan_true_survives_repository_recreation() =
        runTest {
            // Set to true
            repository.setOpenItemListAfterScan(true)
            assertThat(repository.openItemListAfterScanFlow.first()).isTrue()

            // Create a new repository instance (simulates app restart)
            val newRepository = SettingsRepository(context)

            // Verify the setting persisted across "restart"
            assertThat(newRepository.openItemListAfterScanFlow.first()).isTrue()
        }
}

private fun Context.clearDataStorePrefs() {
    val baseDir = filesDir.parentFile ?: return
    val storeFile = java.io.File(baseDir, "datastore/settings_preferences.preferences_pb")
    if (storeFile.exists()) {
        storeFile.delete()
    }
}
