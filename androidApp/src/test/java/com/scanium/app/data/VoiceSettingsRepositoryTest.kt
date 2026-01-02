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

@RunWith(RobolectricTestRunner::class)
class VoiceSettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.clearVoicePrefs()
        repository = SettingsRepository(context)
    }

    @After
    fun tearDown() {
        context.clearVoicePrefs()
    }

    @Test
    fun voice_toggles_default_to_off() =
        runTest {
            assertThat(repository.voiceModeEnabledFlow.first()).isFalse()
            assertThat(repository.speakAnswersEnabledFlow.first()).isFalse()
            assertThat(repository.autoSendTranscriptFlow.first()).isFalse()
        }

    @Test
    fun voice_toggle_preferences_persist() =
        runTest {
            repository.setVoiceModeEnabled(true)
            repository.setSpeakAnswersEnabled(true)
            repository.setAutoSendTranscript(true)

            assertThat(repository.voiceModeEnabledFlow.first()).isTrue()
            assertThat(repository.speakAnswersEnabledFlow.first()).isTrue()
            assertThat(repository.autoSendTranscriptFlow.first()).isTrue()
        }
}

private fun Context.clearVoicePrefs() {
    val baseDir = filesDir.parentFile ?: return
    val storeFile = java.io.File(baseDir, "datastore/settings_preferences.preferences_pb")
    if (storeFile.exists()) {
        storeFile.delete()
    }
}
