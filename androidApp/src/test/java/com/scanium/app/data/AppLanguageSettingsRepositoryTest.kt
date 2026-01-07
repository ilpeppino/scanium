package com.scanium.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.common.truth.Truth.assertThat
import com.scanium.app.model.AppLanguage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLanguageSettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository
    private lateinit var dataStoreFile: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = context.preferencesDataStoreFile("test_settings_preferences")
        dataStoreFile.delete()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { dataStoreFile },
            )
        repository = SettingsRepository(context, dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        dataStoreFile.delete()
    }

    @Test
    fun appLanguage_defaults_to_system() =
        runTest {
            assertThat(repository.appLanguageFlow.first()).isEqualTo(AppLanguage.SYSTEM)
        }

    @Test
    fun appLanguage_persists() =
        runTest {
            repository.setAppLanguage(AppLanguage.IT)
            assertThat(repository.appLanguageFlow.first()).isEqualTo(AppLanguage.IT)
        }
}
