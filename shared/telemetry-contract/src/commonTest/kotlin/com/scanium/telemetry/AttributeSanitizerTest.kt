package com.scanium.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttributeSanitizerTest {

    @Test
    fun `sanitize removes PII attributes`() {
        val attributes = mapOf(
            "scan_duration_ms" to "1234",
            "email" to "user@example.com",
            "phone" to "+1234567890",
            "password" to "secret123"
        )

        val sanitized = AttributeSanitizer.sanitize(attributes)

        assertEquals("1234", sanitized["scan_duration_ms"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["email"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["phone"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["password"])
    }

    @Test
    fun `sanitize is case-insensitive for PII keys`() {
        val attributes = mapOf(
            "EMAIL" to "user@example.com",
            "Phone_Number" to "+1234567890",
            "api_KEY" to "abc123"
        )

        val sanitized = AttributeSanitizer.sanitize(attributes)

        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["EMAIL"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["Phone_Number"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["api_KEY"])
    }

    @Test
    fun `sanitize detects PII in composite keys`() {
        val attributes = mapOf(
            "user_email_address" to "user@example.com",
            "contact_phone" to "+1234567890",
            "oauth_token" to "token123"
        )

        val sanitized = AttributeSanitizer.sanitize(attributes)

        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["user_email_address"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["contact_phone"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["oauth_token"])
    }

    @Test
    fun `sanitize truncates long values`() {
        val longValue = "x".repeat(2000)
        val attributes = mapOf("long_field" to longValue)

        val sanitized = AttributeSanitizer.sanitize(attributes)

        val sanitizedValue = sanitized["long_field"]!!
        assertEquals(AttributeSanitizer.MAX_VALUE_LENGTH, sanitizedValue.length)
        assertTrue(sanitizedValue.endsWith("..."))
    }

    @Test
    fun `sanitize preserves safe attributes`() {
        val attributes = mapOf(
            "platform" to "android",
            "app_version" to "1.0.0",
            "scan_count" to "5",
            "duration_ms" to "1234"
        )

        val sanitized = AttributeSanitizer.sanitize(attributes)

        assertEquals(attributes, sanitized)
    }

    @Test
    fun `validateRequiredAttributes detects missing fields`() {
        val incomplete = mapOf(
            "platform" to "android",
            "app_version" to "1.0.0"
            // Missing: build, env, session_id
        )

        val missing = AttributeSanitizer.validateRequiredAttributes(incomplete)

        assertEquals(3, missing.size)
        assertTrue(missing.contains(TelemetryEvent.ATTR_BUILD))
        assertTrue(missing.contains(TelemetryEvent.ATTR_ENV))
        assertTrue(missing.contains(TelemetryEvent.ATTR_SESSION_ID))
    }

    @Test
    fun `validateRequiredAttributes returns empty for complete attributes`() {
        val complete = mapOf(
            "platform" to "android",
            "app_version" to "1.0.0",
            "build" to "42",
            "env" to "prod",
            "session_id" to "abc-123"
        )

        val missing = AttributeSanitizer.validateRequiredAttributes(complete)

        assertTrue(missing.isEmpty())
    }

    @Test
    fun `sanitizeEvent creates sanitized copy`() {
        val event = TelemetryEvent(
            name = "test.event",
            severity = TelemetrySeverity.INFO,
            timestamp = kotlinx.datetime.Clock.System.now(),
            attributes = mapOf(
                "safe_field" to "value",
                "email" to "user@example.com"
            )
        )

        val sanitized = AttributeSanitizer.sanitizeEvent(event)

        assertEquals(event.name, sanitized.name)
        assertEquals(event.severity, sanitized.severity)
        assertEquals(event.timestamp, sanitized.timestamp)
        assertEquals("value", sanitized.attributes["safe_field"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized.attributes["email"])
    }

    @Test
    fun `sanitize handles empty attributes`() {
        val sanitized = AttributeSanitizer.sanitize(emptyMap())
        assertTrue(sanitized.isEmpty())
    }

    @Test
    fun `sanitize redacts location data`() {
        val attributes = mapOf(
            "latitude" to "37.7749",
            "longitude" to "-122.4194",
            "gps_coordinates" to "37.7749,-122.4194"
        )

        val sanitized = AttributeSanitizer.sanitize(attributes)

        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["latitude"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["longitude"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["gps_coordinates"])
    }

    @Test
    fun `sanitize redacts authentication tokens`() {
        val attributes = mapOf(
            "access_token" to "eyJhbGc...",
            "bearer_token" to "Bearer abc123",
            "session_token" to "session123"
        )

        val sanitized = AttributeSanitizer.sanitize(attributes)

        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["access_token"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["bearer_token"])
        assertEquals(AttributeSanitizer.REDACTED_VALUE, sanitized["session_token"])
    }
}
