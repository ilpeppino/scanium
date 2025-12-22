package com.scanium.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.listing.ExportProfileId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportProfilePreferencesTest {

    @Test
    fun storesAndLoadsLastProfileId() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = ExportProfilePreferences(context)
        val expected = ExportProfileId("VINTED_STYLE")

        preferences.setLastProfileId(expected)
        val loaded = preferences.getLastProfileId(ExportProfileId.GENERIC)

        assertThat(loaded).isEqualTo(expected)
    }
}
