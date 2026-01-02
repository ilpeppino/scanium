package com.scanium.app.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class AndroidSoundManager internal constructor(
    soundsEnabledFlow: Flow<Boolean>,
    private val deviceSoundPolicy: DeviceSoundPolicy,
    private val tonePlayer: TonePlayer,
    private val rateLimiter: SoundRateLimiter,
    private val scope: CoroutineScope,
) : SoundManager {
    constructor(
        context: Context,
        soundsEnabledFlow: Flow<Boolean>,
    ) : this(
        soundsEnabledFlow = soundsEnabledFlow,
        deviceSoundPolicy = AndroidDeviceSoundPolicy(context),
        tonePlayer = ToneGeneratorPlayer(),
        rateLimiter = SoundRateLimiter(DEFAULT_RATE_LIMITS, SystemClock::elapsedRealtime),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )

    private val soundsEnabled = SoundEnabledState()

    init {
        scope.launch {
            soundsEnabledFlow.collect { enabled ->
                soundsEnabled.value = enabled
            }
        }
    }

    override fun preload() = Unit

    override fun play(sound: AppSound) {
        if (!soundsEnabled.value) return
        if (!deviceSoundPolicy.isSoundAllowed()) return
        if (!rateLimiter.canPlay(sound)) return
        val tone = SOUND_TONES[sound] ?: return
        val durationMs = SOUND_DURATIONS[sound] ?: DEFAULT_DURATION_MS
        tonePlayer.play(tone, durationMs)
    }

    override fun release() {
        tonePlayer.release()
        scope.cancel()
    }

    private class SoundEnabledState {
        @Volatile
        var value: Boolean = true
    }

    companion object {
        private const val DEFAULT_DURATION_MS = 80
        private val SOUND_TONES =
            mapOf(
                AppSound.CAPTURE to ToneGenerator.TONE_PROP_BEEP,
                AppSound.ITEM_ADDED to ToneGenerator.TONE_PROP_BEEP2,
                AppSound.SELECT to ToneGenerator.TONE_PROP_BEEP2,
                AppSound.DELETE to ToneGenerator.TONE_PROP_NACK,
                AppSound.ERROR to ToneGenerator.TONE_SUP_ERROR,
                AppSound.SEND to ToneGenerator.TONE_PROP_ACK,
                AppSound.RECEIVED to ToneGenerator.TONE_PROP_BEEP2,
                AppSound.EXPORT to ToneGenerator.TONE_PROP_ACK,
            )

        private val SOUND_DURATIONS =
            mapOf(
                AppSound.CAPTURE to 120,
                AppSound.ITEM_ADDED to 60,
                AppSound.SELECT to 50,
                AppSound.DELETE to 90,
                AppSound.ERROR to 140,
                AppSound.SEND to 70,
                AppSound.RECEIVED to 90,
                AppSound.EXPORT to 80,
            )

        private val DEFAULT_RATE_LIMITS =
            mapOf(
                AppSound.ITEM_ADDED to 800L,
                AppSound.SELECT to 150L,
                AppSound.RECEIVED to 500L,
            )
    }
}

internal interface DeviceSoundPolicy {
    fun isSoundAllowed(): Boolean
}

internal class AndroidDeviceSoundPolicy(context: Context) : DeviceSoundPolicy {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun isSoundAllowed(): Boolean {
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val filter = notificationManager.currentInterruptionFilter
            if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) return false
        }
        return true
    }
}

internal interface TonePlayer {
    fun play(
        tone: Int,
        durationMs: Int,
    )

    fun release()
}

private class ToneGeneratorPlayer : TonePlayer {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, VOLUME_PERCENT)

    override fun play(
        tone: Int,
        durationMs: Int,
    ) {
        toneGenerator.startTone(tone, durationMs)
    }

    override fun release() {
        toneGenerator.release()
    }

    companion object {
        private const val VOLUME_PERCENT = 40
    }
}
