package com.scanium.app.audio

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSoundManagerTest {
    @Test
    fun whenDisabled_thenDoesNotPlaySounds() {
        val enabledFlow = MutableStateFlow(false)
        val fakePlayer = FakeTonePlayer()
        val scope = TestScope(UnconfinedTestDispatcher())
        val manager = AndroidSoundManager(
            context = ApplicationProvider.getApplicationContext(),
            soundsEnabledFlow = enabledFlow,
            deviceSoundPolicy = FakeDeviceSoundPolicy(),
            tonePlayer = fakePlayer,
            rateLimiter = SoundRateLimiter(emptyMap(), clock = { 0L }),
            scope = scope
        )

        scope.runCurrent()
        manager.play(AppSound.CAPTURE)

        assertThat(fakePlayer.playCount).isEqualTo(0)
        manager.release()
    }

    private class FakeTonePlayer : TonePlayer {
        var playCount = 0

        override fun play(tone: Int, durationMs: Int) {
            playCount++
        }

        override fun release() = Unit
    }

    private class FakeDeviceSoundPolicy : DeviceSoundPolicy {
        override fun isSoundAllowed(): Boolean = true
    }
}
