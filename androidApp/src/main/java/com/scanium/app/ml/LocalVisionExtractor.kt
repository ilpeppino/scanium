package com.scanium.app.ml

import android.graphics.Bitmap
import android.util.Log
import androidx.palette.graphics.Palette
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scanium.app.debug.ImageClassifierDebugger
import com.scanium.shared.core.models.items.VisionAttributes
import com.scanium.shared.core.models.items.VisionColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of local vision extraction.
 * Contains OCR text, colors, and timing information.
 */
data class LocalVisionResult(
    /** Recognized text from OCR (may be null if no text detected) */
    val ocrText: String?,
    /** Dominant colors extracted from the image */
    val colors: List<VisionColor>,
    /** Suggested label derived from OCR (first meaningful line) */
    val suggestedLabel: String?,
    /** Total extraction time in milliseconds */
    val extractionTimeMs: Long,
    /** Whether OCR was successful */
    val ocrSuccess: Boolean,
    /** Whether color extraction was successful */
    val colorSuccess: Boolean,
)

/**
 * Local vision extractor using ML Kit and Palette API.
 *
 * Provides fast, on-device extraction of:
 * - OCR text using ML Kit Text Recognition
 * - Dominant colors using Android Palette API
 *
 * This runs entirely on-device without network, making it suitable for:
 * - Immediate feedback while cloud extraction is in progress
 * - Offline fallback when network is unavailable
 *
 * Usage:
 * ```kotlin
 * val result = localVisionExtractor.extract(bitmap)
 * if (result.ocrText != null) {
 *     // Update item with local OCR results
 * }
 * ```
 */
@Singleton
class LocalVisionExtractor
    @Inject
    constructor(
        private val debugger: ImageClassifierDebugger? = null,
    ) {
        companion object {
            private const val TAG = "LocalVisionExtractor"
            private const val MAX_OCR_TEXT_LENGTH = 2000 // Limit OCR text for performance
            private const val MIN_OCR_TEXT_LENGTH = 2 // Minimum meaningful text
            private const val MAX_COLORS = 5 // Maximum colors to extract
            private const val COLOR_AREA_THRESHOLD = 0.05f // Minimum 5% area for color
        }

        // ML Kit text recognizer - lazy initialized
        private val textRecognizer: TextRecognizer by lazy {
            Log.d(TAG, "Creating ML Kit TextRecognizer")
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }

        /**
         * Extract vision insights from a bitmap using local ML Kit and Palette.
         *
         * This method runs on-device and does not require network.
         * It extracts:
         * - OCR text using ML Kit Text Recognition
         * - Dominant colors using Palette API
         *
         * @param bitmap The image to analyze
         * @return LocalVisionResult with extracted data
         */
        suspend fun extract(bitmap: Bitmap): LocalVisionResult =
            withContext(Dispatchers.Default) {
                val startTime = System.currentTimeMillis()

                // DEV-ONLY: Log Local Vision input image for debugging
                debugger?.logClassifierInput(
                    bitmap = bitmap,
                    source = "Local Vision extraction (Layer A)",
                    itemId = null,
                    originPath = "Source bitmap ${bitmap.width}x${bitmap.height}",
                )

                var ocrText: String? = null
                var ocrSuccess = false
                var colors: List<VisionColor> = emptyList()
                var colorSuccess = false
                var suggestedLabel: String? = null

                // Extract OCR text
                try {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val visionText = textRecognizer.process(inputImage).await()
                    val rawText = visionText.text

                    if (rawText.length >= MIN_OCR_TEXT_LENGTH) {
                        ocrText =
                            if (rawText.length > MAX_OCR_TEXT_LENGTH) {
                                rawText.take(MAX_OCR_TEXT_LENGTH) + "..."
                            } else {
                                rawText
                            }
                        ocrSuccess = true
                        suggestedLabel = extractSuggestedLabel(rawText)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ML Kit OCR failed", e)
                }

                // Extract colors using Palette API
                try {
                    val resizedBitmap = resizeForPalette(bitmap)
                    val palette = Palette.from(resizedBitmap).generate()
                    colors = extractColorsFromPalette(palette)
                    colorSuccess = colors.isNotEmpty()
                } catch (e: Exception) {
                    Log.e(TAG, "Palette extraction failed", e)
                }

                val totalTime = System.currentTimeMillis() - startTime

                LocalVisionResult(
                    ocrText = ocrText,
                    colors = colors,
                    suggestedLabel = suggestedLabel,
                    extractionTimeMs = totalTime,
                    ocrSuccess = ocrSuccess,
                    colorSuccess = colorSuccess,
                )
            }

        /**
         * Convert LocalVisionResult to VisionAttributes for item update.
         */
        fun toVisionAttributes(result: LocalVisionResult): VisionAttributes =
            VisionAttributes(
                ocrText = result.ocrText,
                colors = result.colors,
                logos = emptyList(), // Local extraction doesn't detect logos
                labels = emptyList(), // Local extraction doesn't provide labels
                brandCandidates = result.suggestedLabel?.let { listOf(it) } ?: emptyList(),
                modelCandidates = emptyList(),
            )

        /**
         * Extract a suggested label from OCR text.
         * Uses a scoring algorithm to pick the best candidate (brand, product name, etc.).
         *
         * Scoring prefers:
         * - Longer text (short fragments like "INS" are penalized)
         * - ALL CAPS or Title Case (brand names are often formatted this way)
         * - Common product name patterns
         * - Text that appears early in the scan (usually more prominent)
         */
        private fun extractSuggestedLabel(text: String): String? {
            // Split into lines and words, collecting candidates
            val candidates = mutableListOf<Pair<String, Float>>()

            // Check individual lines first
            text
                .lines()
                .map { it.trim() }
                .filter { line ->
                    line.length in 3..50 && // Minimum 3 chars now (skip tiny fragments)
                        !line.all { it.isDigit() || it.isWhitespace() } &&
                        line.count { it.isLetter() } >= line.length * 0.5
                }.forEachIndexed { index, line ->
                    val score = scoreLabelCandidate(line, index)
                    if (score > 0) {
                        candidates.add(line to score)
                    }
                }

            // Also check individual words (brand names can be single words)
            text
                .split(Regex("[\\s\\n]+"))
                .map { it.trim() }
                .filter { word ->
                    word.length in 4..30 && // Words need at least 4 chars to be meaningful
                        word.count { it.isLetter() } >= word.length * 0.8 // Mostly letters
                }.forEachIndexed { index, word ->
                    val score = scoreLabelCandidate(word, index) * 0.9f // Slight penalty for words vs lines
                    if (score > 0) {
                        candidates.add(word to score)
                    }
                }

            // Return the highest scoring candidate
            return candidates
                .maxByOrNull { it.second }
                ?.first
        }

        /**
         * Score a label candidate. Higher = better.
         */
        private fun scoreLabelCandidate(
            text: String,
            positionIndex: Int,
        ): Float {
            var score = 0f

            // Length scoring: prefer medium-length text (5-20 chars is ideal for product names)
            score +=
                when (text.length) {
                    in 3..4 -> 0.5f

                    // Very short, might be fragment
                    in 5..10 -> 2.0f

                    // Ideal range for brand names like "Kleenex"
                    in 11..20 -> 1.5f

                    // Good for phrases like "THE ORIGINAL"
                    in 21..35 -> 1.0f

                    // Getting long but still ok
                    else -> 0.3f // Too short or too long
                }

            // Case pattern scoring
            val isAllCaps = text.all { !it.isLetter() || it.isUpperCase() }
            val isTitleCase =
                text.first().isUpperCase() &&
                    text.drop(1).any { it.isLowerCase() }
            val hasCapitalWord =
                text.split(" ").any { word ->
                    word.length >= 3 && word.all { !it.isLetter() || it.isUpperCase() }
                }

            when {
                isAllCaps && text.length >= 4 -> score += 1.5f

                // ALL CAPS like "KLEENEX" or "THE ORIGINAL"
                isTitleCase -> score += 1.0f

                // Title Case like "Kleenex"
                hasCapitalWord -> score += 0.8f // Contains caps word
            }

            // Position penalty: earlier text is usually more prominent
            score -= positionIndex * 0.1f

            // Bonus for trademark indicators
            if (text.contains("®") || text.contains("™")) {
                score += 1.0f
            }

            // Penalty for known noise patterns (common filler words)
            val lowerText = text.lowercase()
            if (lowerText in listOf("the", "and", "for", "with", "new", "free", "net", "wt")) {
                score -= 2.0f
            }

            // Penalty for text that looks like quantities or measurements
            if (text.matches(Regex(".*\\d+\\s*(oz|ml|g|kg|lb|ct|pk|count).*", RegexOption.IGNORE_CASE))) {
                score -= 1.0f
            }

            // Heavy penalty for recycling/environmental labels (these are NOT product names)
            val recyclingPatterns =
                listOf(
                    "recycl",
                    "cyclabl",
                    "recycle",
                    "recyclable",
                    "compost",
                    "biodegr",
                    "sustain",
                    "please",
                    "dispose",
                    "trash",
                    "bin",
                    "made from",
                    "contains",
                    "ingredients",
                )
            if (recyclingPatterns.any { lowerText.contains(it) }) {
                score -= 3.0f
            }

            // Bonus for text containing common product name patterns
            val productPatterns =
                listOf(
                    "original",
                    "classic",
                    "premium",
                    "deluxe",
                    "ultra",
                    "pro",
                    "plus",
                    "max",
                    "extra",
                    "super",
                )
            if (productPatterns.any { lowerText.contains(it) }) {
                score += 1.5f
            }

            return score
        }

        /**
         * Resize bitmap for faster palette extraction.
         */
        private fun resizeForPalette(bitmap: Bitmap): Bitmap {
            val maxDimension = 100 // Small size is fine for color extraction
            val scale = minOf(1.0f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
            if (scale >= 1.0f) return bitmap

            val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        /**
         * Extract named colors from a Palette.
         * Returns unique color names only (no duplicates like "cyan, cyan, cyan").
         */
        private fun extractColorsFromPalette(palette: Palette): List<VisionColor> {
            val colors = mutableListOf<VisionColor>()
            val seenColorNames = mutableSetOf<String>()

            // Get swatches in order of prominence
            val swatches =
                listOfNotNull(
                    palette.dominantSwatch,
                    palette.vibrantSwatch,
                    palette.mutedSwatch,
                    palette.lightVibrantSwatch,
                    palette.darkVibrantSwatch,
                    palette.lightMutedSwatch,
                    palette.darkMutedSwatch,
                ).distinctBy { it.rgb }

            for (swatch in swatches) {
                val colorName = getColorName(swatch.rgb)

                // Skip if we already have this color name
                if (colorName in seenColorNames) continue
                seenColorNames.add(colorName)

                val hex = String.format("#%06X", 0xFFFFFF and swatch.rgb)
                val score = swatch.population.toFloat() / (palette.swatches.sumOf { it.population } + 1)

                colors.add(
                    VisionColor(
                        name = colorName,
                        hex = hex,
                        score = score,
                    ),
                )

                // Stop after MAX_COLORS unique colors
                if (colors.size >= MAX_COLORS) break
            }

            return colors
        }

        /**
         * Get a human-readable color name from an RGB value.
         */
        private fun getColorName(rgb: Int): String {
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF

            // Convert to HSV for better color categorization
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val saturation = hsv[1]
            val value = hsv[2]

            // Determine color based on HSV
            return when {
                value < 0.2 -> {
                    "black"
                }

                value > 0.9 && saturation < 0.1 -> {
                    "white"
                }

                saturation < 0.15 -> {
                    when {
                        value < 0.5 -> "gray"
                        else -> "light gray"
                    }
                }

                else -> {
                    when {
                        hue < 15 || hue >= 345 -> "red"
                        hue < 45 -> "orange"
                        hue < 70 -> "yellow"
                        hue < 150 -> "green"
                        hue < 190 -> "cyan"
                        hue < 260 -> "blue"
                        hue < 290 -> "purple"
                        else -> "pink"
                    }
                }
            }
        }

        /**
         * Close resources.
         */
        fun close() {
            try {
                textRecognizer.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing text recognizer", e)
            }
        }
    }
