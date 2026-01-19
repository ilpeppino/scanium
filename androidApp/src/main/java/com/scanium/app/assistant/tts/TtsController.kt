package com.scanium.app.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

private const val TAG = "TtsController"

/**
 * Lightweight TTS controller for reading AI-generated content aloud.
 *
 * Features:
 * - Lazy initialization with TextToSpeech
 * - Respects app language/locale setting
 * - Safe failure handling (logs warnings, never crashes)
 * - Single-speak mode (QUEUE_FLUSH)
 * - Proper lifecycle management (shutdown on screen exit)
 *
 * Usage:
 * ```
 * val tts = remember { TtsController(context) }
 * DisposableEffect(Unit) {
 *     onDispose { tts.shutdown() }
 * }
 * tts.speakOnce("Generated content here")
 * ```
 */
class TtsController(
    context: Context,
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initializationFailed = false
    private var pendingText: String? = null
    private var currentlySpeaking = false

    init {
        // Initialize TextToSpeech lazily
        try {
            tts =
                TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        Log.d(TAG, "TTS initialized successfully")

                        // Set up utterance listener for state tracking
                        tts?.setOnUtteranceProgressListener(
                            object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {
                                    currentlySpeaking = true
                                }

                                override fun onDone(utteranceId: String?) {
                                    currentlySpeaking = false
                                }

                                override fun onError(utteranceId: String?) {
                                    currentlySpeaking = false
                                    Log.w(TAG, "TTS error for utterance: $utteranceId")
                                }
                            },
                        )

                        // Speak pending text if queued during initialization
                        pendingText?.let { text ->
                            speakNow(text)
                            pendingText = null
                        }
                    } else {
                        initializationFailed = true
                        Log.w(TAG, "TTS initialization failed with status: $status")
                    }
                }
        } catch (e: Exception) {
            initializationFailed = true
            Log.e(TAG, "Failed to create TextToSpeech instance", e)
        }
    }

    /**
     * Set the TTS language to match the app's locale.
     * Falls back to device locale if the specified locale is not available.
     *
     * @param locale The desired locale (e.g., from app language setting)
     */
    fun setLanguage(locale: Locale) {
        if (!isInitialized || initializationFailed) {
            Log.w(TAG, "Cannot set language: TTS not initialized")
            return
        }

        try {
            val result = tts?.setLanguage(locale)
            when (result) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(TAG, "Language not supported: $locale, falling back to default")
                    // Try device default
                    tts?.setLanguage(Locale.getDefault())
                }

                TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
                -> {
                    Log.d(TAG, "TTS language set to: $locale")
                }

                else -> {
                    Log.w(TAG, "Unknown language result: $result for locale $locale")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting TTS language", e)
        }
    }

    /**
     * Speak the given text once, stopping any current speech.
     * Uses QUEUE_FLUSH mode to replace any ongoing speech.
     *
     * If TTS is not initialized yet, queues the text to speak once ready.
     * If initialization failed, logs warning and does nothing.
     *
     * @param text The text to speak (must be non-blank)
     */
    fun speakOnce(text: String) {
        if (text.isBlank()) {
            Log.d(TAG, "Ignoring empty text")
            return
        }

        when {
            initializationFailed -> {
                Log.w(TAG, "Cannot speak: TTS initialization failed")
            }

            !isInitialized -> {
                Log.d(TAG, "TTS not ready yet, queuing text")
                pendingText = text
            }

            else -> {
                speakNow(text)
            }
        }
    }

    private fun speakNow(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ExportAssistantUtterance")
            Log.d(TAG, "Speaking text (${text.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text", e)
        }
    }

    /**
     * Stop any ongoing speech immediately.
     */
    fun stop() {
        try {
            tts?.stop()
            currentlySpeaking = false
            Log.d(TAG, "TTS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    /**
     * Check if TTS is currently speaking.
     *
     * @return true if speech is in progress, false otherwise
     */
    fun isSpeaking(): Boolean = currentlySpeaking

    /**
     * Shut down the TTS engine and release resources.
     * Must be called when the controller is no longer needed.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            currentlySpeaking = false
            Log.d(TAG, "TTS shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during TTS shutdown", e)
        }
    }
}

/**
 * Build speakable text from Export Assistant content.
 * Extracts only the AI-generated values (title, description, bullets),
 * without any UI labels like "Title:", "Description:", etc.
 *
 * @param title The generated title (nullable)
 * @param description The generated description (nullable)
 * @param bullets List of generated bullet points
 * @return Combined speakable text with natural pauses, or empty string if no content
 */
fun buildSpeakableText(
    title: String?,
    description: String?,
    bullets: List<String>,
): String {
    val parts = mutableListOf<String>()

    // Add title if present
    title?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

    // Add description if present
    description?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

    // Add bullets if present, separated by pauses
    if (bullets.isNotEmpty()) {
        val bulletsText = bullets.joinToString("\n") { it.trim() }
        if (bulletsText.isNotBlank()) {
            parts.add(bulletsText)
        }
    }

    // Join with double newlines for natural pauses
    return parts.joinToString("\n\n").trim()
}
