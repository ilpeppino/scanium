package com.scanium.telemetry

/**
 * Sanitizes telemetry event attributes to prevent PII leakage.
 *
 * This sanitizer applies the following rules:
 * 1. Removes attributes with keys matching the PII denylist
 * 2. Truncates string values exceeding the maximum length
 * 3. Redacts values for sensitive keys (replaces with "[REDACTED]")
 *
 * ## PII Denylist
 * The following attribute keys are automatically redacted:
 * - Personal identifiers: email, phone, address, full_name, username, user_id
 * - Authentication: token, auth, password, api_key, secret, credentials, cookies, session_token
 * - Location: gps, latitude, lat, longitude, lon, location, coordinates
 * - Biometric: fingerprint, face_id, biometric
 * - Payment: credit_card, card_number, cvv, billing
 * - Device identifiers: imei, device_id, mac_address, serial_number
 *
 * ## Usage
 * ```kotlin
 * val rawAttributes = mapOf(
 *     "scan_duration_ms" to "1234",
 *     "email" to "user@example.com",  // Will be redacted
 *     "very_long_value" to "x".repeat(2000)  // Will be truncated
 * )
 * val sanitized = AttributeSanitizer.sanitize(rawAttributes)
 * // Result: {"scan_duration_ms": "1234", "email": "[REDACTED]", "very_long_value": "x...x" (1024 chars)}
 * ```
 */
object AttributeSanitizer {
    /**
     * Maximum length for attribute string values.
     * Values exceeding this length will be truncated.
     */
    const val MAX_VALUE_LENGTH = 1024

    /**
     * Redaction marker for sensitive values.
     */
    const val REDACTED_VALUE = "[REDACTED]"

    /**
     * PII denylist: attribute keys that should be redacted.
     * Keys are matched case-insensitively and as substrings.
     */
    private val PII_DENYLIST = setOf(
        // Personal identifiers
        "email",
        "phone",
        "address",
        "full_name",
        "fullname",
        "username",
        "user_id",
        "userid",
        "name",

        // Authentication & authorization
        "token",
        "auth",
        "password",
        "passwd",
        "pwd",
        "api_key",
        "apikey",
        "secret",
        "credentials",
        "cookie",
        "session_token",
        "access_token",
        "refresh_token",
        "bearer",

        // Location data
        "gps",
        "latitude",
        "lat",
        "longitude",
        "lon",
        "location",
        "coordinates",
        "coord",
        "geolocation",

        // Biometric
        "fingerprint",
        "face_id",
        "faceid",
        "biometric",

        // Payment
        "credit_card",
        "creditcard",
        "card_number",
        "cardnumber",
        "cvv",
        "billing",
        "payment",

        // Device identifiers
        "imei",
        "device_id",
        "deviceid",
        "mac_address",
        "macaddress",
        "serial_number",
        "serialnumber",
        "uuid",

        // Other sensitive
        "ssn",
        "social_security",
        "passport",
        "license",
        "ip_address",
        "ipaddress",
        "ip"
    )

    /**
     * Sanitizes a map of attributes by removing PII and enforcing value length limits.
     *
     * @param attributes Raw attributes from the event
     * @return Sanitized attributes safe for export
     */
    fun sanitize(attributes: Map<String, String>): Map<String, String> {
        return attributes.mapNotNull { (key, value) ->
            when {
                // Redact PII keys
                isPiiKey(key) -> key to REDACTED_VALUE

                // Truncate long values
                value.length > MAX_VALUE_LENGTH -> key to truncateValue(value)

                // Keep as-is
                else -> key to value
            }
        }.toMap()
    }

    /**
     * Checks if a key matches the PII denylist.
     * Matching is case-insensitive and checks both:
     * 1. Full key against compound terms (e.g., "api_key")
     * 2. Individual words within the key against all terms
     *
     * Words are separated by underscores, hyphens, or dots.
     *
     * Examples:
     * - "user_email" matches (contains word "email")
     * - "platform" does NOT match (contains "lat" but not as a separate word)
     * - "gps_lat" matches (contains word "lat")
     * - "api_KEY" matches (normalized to "api_key")
     */
    private fun isPiiKey(key: String): Boolean {
        val lowerKey = key.lowercase()

        // First, check if the full key matches any compound term
        if (PII_DENYLIST.contains(lowerKey)) {
            return true
        }

        // Then, split key into words and check each word
        val words = lowerKey.split('_', '-', '.')
        return words.any { word ->
            PII_DENYLIST.contains(word)
        }
    }

    /**
     * Truncates a value to MAX_VALUE_LENGTH, appending "..." to indicate truncation.
     */
    private fun truncateValue(value: String): String {
        if (value.length <= MAX_VALUE_LENGTH) return value
        return value.take(MAX_VALUE_LENGTH - 3) + "..."
    }

    /**
     * Validates that a sanitized attributes map contains all required fields.
     *
     * @param attributes Sanitized attributes
     * @return List of missing required attribute keys (empty if all present)
     */
    fun validateRequiredAttributes(attributes: Map<String, String>): List<String> {
        val required = listOf(
            TelemetryEvent.ATTR_PLATFORM,
            TelemetryEvent.ATTR_APP_VERSION,
            TelemetryEvent.ATTR_BUILD,
            TelemetryEvent.ATTR_ENV,
            TelemetryEvent.ATTR_SESSION_ID
        )

        return required.filter { it !in attributes }
    }

    /**
     * Creates a sanitized copy of a TelemetryEvent.
     *
     * @param event Original event
     * @return Event with sanitized attributes
     */
    fun sanitizeEvent(event: TelemetryEvent): TelemetryEvent {
        return event.copy(attributes = sanitize(event.attributes))
    }
}
