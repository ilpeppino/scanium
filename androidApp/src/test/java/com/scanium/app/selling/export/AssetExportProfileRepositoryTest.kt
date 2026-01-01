package com.scanium.app.selling.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.app.listing.ExportProfileId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssetExportProfileRepositoryTest {
    @Test
    fun loadsProfilesFromAssets() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repository = AssetExportProfileRepository(context)

            val profiles = repository.getProfiles()
            val defaultId = repository.getDefaultProfileId()

            assertThat(profiles).isNotEmpty()
            assertThat(profiles.first().id).isNotNull()
            assertThat(defaultId).isEqualTo(ExportProfileId.GENERIC)
        }
}
