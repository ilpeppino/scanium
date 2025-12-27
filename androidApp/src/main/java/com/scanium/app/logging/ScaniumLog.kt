package com.scanium.app.logging

import android.util.Log
import com.scanium.app.BuildConfig

object ScaniumLog {
    private const val PREFIX = "Scanium"

    @Volatile
    var verboseEnabled: Boolean = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (!verboseEnabled) return
        Log.d("$PREFIX/$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$PREFIX/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$PREFIX/$tag", message, throwable)
        } else {
            Log.w("$PREFIX/$tag", message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$PREFIX/$tag", message, throwable)
        } else {
            Log.e("$PREFIX/$tag", message)
        }
    }

    /**
     * Sanitizes an HTTP response body for safe logging.
     * Truncates to a safe length and redacts common PII patterns.
     *
     * @param responseBody The raw response body (may be null)
     * @param maxLength Maximum length of the sanitized output (default: 200)
     * @return Sanitized string safe for logging
     */
    fun sanitizeResponseBody(responseBody: String?, maxLength: Int = 200): String {
        if (responseBody.isNullOrBlank()) return "[empty]"

        // Patterns for common PII that might appear in error responses
        val piiPatterns = listOf(
            // Email addresses
            Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""") to "[EMAIL]",
            // JWT tokens (header.payload.signature format)
            Regex("""eyJ[a-zA-Z0-9_-]*\.eyJ[a-zA-Z0-9_-]*\.[a-zA-Z0-9_-]*""") to "[TOKEN]",
            // Bearer tokens
            Regex("""[Bb]earer\s+[a-zA-Z0-9._-]+""") to "Bearer [TOKEN]",
            // API keys (common patterns)
            Regex("""["\']?(?:api[_-]?key|apikey|x-api-key)["\']?\s*[:=]\s*["\']?[a-zA-Z0-9_-]{16,}["\']?""", RegexOption.IGNORE_CASE) to "[API_KEY]",
            // Authorization headers
            Regex("""["\']?(?:authorization|auth)["\']?\s*[:=]\s*["\']?[a-zA-Z0-9._\-/+=]+["\']?""", RegexOption.IGNORE_CASE) to "[AUTH]",
            // Session tokens
            Regex("""["\']?(?:session[_-]?(?:id|token)|sessionid|sessiontoken)["\']?\s*[:=]\s*["\']?[a-zA-Z0-9_-]+["\']?""", RegexOption.IGNORE_CASE) to "[SESSION]",
            // Credit card numbers (basic pattern)
            Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b""") to "[CARD]",
            // Phone numbers (various formats)
            Regex("""\+?\d{1,3}[\s.-]?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}""") to "[PHONE]"
        )

        var sanitized = responseBody
        for ((pattern, replacement) in piiPatterns) {
            sanitized = sanitized.replace(pattern, replacement)
        }

        // Truncate to max length
        return if (sanitized.length > maxLength) {
            sanitized.take(maxLength) + "..."
        } else {
            sanitized
        }
    }
}
