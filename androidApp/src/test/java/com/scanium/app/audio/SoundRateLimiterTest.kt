package com.scanium.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SoundRateLimiterTest {
    @Test
    fun whenWithinMinInterval_thenBlocksPlayback() {
        var now = 0L
        val limiter =
            SoundRateLimiter(
                minIntervalsMs = mapOf(AppSound.ITEM_ADDED to 800L),
                clock = { now },
            )

        assertThat(limiter.canPlay(AppSound.ITEM_ADDED)).isTrue()

        now += 400L
        assertThat(limiter.canPlay(AppSound.ITEM_ADDED)).isFalse()

        now += 500L
        assertThat(limiter.canPlay(AppSound.ITEM_ADDED)).isTrue()
    }
}
