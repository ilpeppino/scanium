package com.scanium.app.camera

import android.media.AudioAttributes
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages camera sound effects using ToneGenerator.
 *
 * Provides pleasant audio feedback for:
 * - Camera shutter (tap capture)
 * - Scan start (crescendo melody)
 * - Scan stop (decrescendo melody)
 */
class CameraSoundManager {

    companion object {
        private const val TAG = "CameraSoundManager"

        // Musical notes (frequencies in Hz)
        private const val NOTE_C5 = 523
        private const val NOTE_E5 = 659
        private const val NOTE_G5 = 784
        private const val NOTE_C6 = 1047

        private const val NOTE_A5 = 880
        private const val NOTE_F5 = 698
        private const val NOTE_D5 = 587

        // Tone durations
        private const val CLICK_DURATION_MS = 50
        private const val NOTE_DURATION_MS = 80
        private const val NOTE_GAP_MS = 30L
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var toneGenerator: ToneGenerator? = null
    private var isPlaying = false

    init {
        try {
            // Create ToneGenerator with DTMF stream type for pleasant tones
            toneGenerator = ToneGenerator(
                android.media.AudioManager.STREAM_MUSIC,
                ToneGenerator.MAX_VOLUME / 3 // 33% volume for pleasant, non-intrusive sounds
            )
            Log.d(TAG, "ToneGenerator initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Plays a short click sound for camera capture.
     */
    fun playShutterClick() {
        scope.launch {
            try {
                if (isPlaying) return@launch
                isPlaying = true

                // Play a short, pleasant click (high C note)
                playTone(NOTE_C6, CLICK_DURATION_MS)

                isPlaying = false
            } catch (e: Exception) {
                Log.e(TAG, "Error playing shutter click", e)
                isPlaying = false
            }
        }
    }

    /**
     * Plays a pleasant ascending melody when scanning starts.
     * Creates a sense of initiation and activity.
     */
    fun playScanStartMelody() {
        scope.launch {
            try {
                if (isPlaying) return@launch
                isPlaying = true

                // Ascending arpeggio: C5 -> E5 -> G5 -> C6
                playTone(NOTE_C5, NOTE_DURATION_MS)
                delay(NOTE_GAP_MS)
                playTone(NOTE_E5, NOTE_DURATION_MS)
                delay(NOTE_GAP_MS)
                playTone(NOTE_G5, NOTE_DURATION_MS)
                delay(NOTE_GAP_MS)
                playTone(NOTE_C6, NOTE_DURATION_MS + 20) // Slightly longer final note

                isPlaying = false
            } catch (e: Exception) {
                Log.e(TAG, "Error playing scan start melody", e)
                isPlaying = false
            }
        }
    }

    /**
     * Plays a pleasant descending melody when scanning stops.
     * Creates a sense of completion and relaxation.
     */
    fun playScanStopMelody() {
        scope.launch {
            try {
                if (isPlaying) return@launch
                isPlaying = true

                // Descending pattern: A5 -> F5 -> D5 -> C5
                playTone(NOTE_A5, NOTE_DURATION_MS)
                delay(NOTE_GAP_MS)
                playTone(NOTE_F5, NOTE_DURATION_MS)
                delay(NOTE_GAP_MS)
                playTone(NOTE_D5, NOTE_DURATION_MS)
                delay(NOTE_GAP_MS)
                playTone(NOTE_C5, NOTE_DURATION_MS + 40) // Longer final note for resolution

                isPlaying = false
            } catch (e: Exception) {
                Log.e(TAG, "Error playing scan stop melody", e)
                isPlaying = false
            }
        }
    }

    /**
     * Plays a tone at the specified frequency for the given duration.
     * Suspends until the tone completes.
     */
    private suspend fun playTone(frequencyHz: Int, durationMs: Int) {
        try {
            toneGenerator?.let { generator ->
                // Generate a simple sine wave tone
                // We'll use DTMF tones as a base and adjust
                // Since ToneGenerator doesn't directly support custom frequencies,
                // we'll use the closest DTMF tone and rely on the pleasant pattern

                // Map to nearest DTMF tone for pleasant sound
                val dtmfTone = when {
                    frequencyHz <= 600 -> ToneGenerator.TONE_DTMF_1  // Low
                    frequencyHz <= 700 -> ToneGenerator.TONE_DTMF_4  // Med-low
                    frequencyHz <= 850 -> ToneGenerator.TONE_DTMF_7  // Medium
                    frequencyHz <= 1000 -> ToneGenerator.TONE_DTMF_9 // Med-high
                    else -> ToneGenerator.TONE_DTMF_D                 // High
                }

                generator.startTone(dtmfTone, durationMs)
                delay(durationMs.toLong()) // Non-blocking wait for tone to complete
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error playing tone at ${frequencyHz}Hz", e)
        }
    }

    /**
     * Cleanup resources.
     */
    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            Log.d(TAG, "ToneGenerator released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ToneGenerator", e)
        }
    }
}
