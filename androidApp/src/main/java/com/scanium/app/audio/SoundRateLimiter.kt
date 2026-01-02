package com.scanium.app.audio

internal class SoundRateLimiter(
    private val minIntervalsMs: Map<AppSound, Long>,
    private val clock: () -> Long,
) {
    private val lastPlayed = mutableMapOf<AppSound, Long>()

    fun canPlay(sound: AppSound): Boolean {
        val now = clock()
        val minInterval = minIntervalsMs[sound] ?: 0L
        val last = lastPlayed[sound]
        if (minInterval > 0 && last != null && now - last < minInterval) {
            return false
        }
        lastPlayed[sound] = now
        return true
    }
}
