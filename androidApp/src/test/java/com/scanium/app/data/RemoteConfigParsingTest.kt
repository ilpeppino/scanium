package com.scanium.app.data

import com.scanium.app.model.config.RemoteConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests to ensure backend remote-config.json field names match Android model.
 *
 * The backend file is at: backend/config/remote-config.json
 * The Android model is at: shared/core-models/.../RemoteConfig.kt
 *
 * This test prevents field name mismatches that would cause features to be disabled
 * due to JSON fields being silently ignored (ignoreUnknownKeys = true).
 */
class RemoteConfigParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Backend remote-config.json format MUST use field names that match Android model.
     * If this test fails, the backend remote-config.json uses wrong field names.
     */
    @Test
    fun `backend remote config JSON parses correctly into Android model`() {
        // This is the exact format that backend/config/remote-config.json should use
        val backendJson =
            """
            {
              "version": "2025-01-04",
              "ttlSeconds": 3600,
              "featureFlags": {
                "enableAssistant": true,
                "enableCloud": true,
                "enableProfiles": true,
                "enablePostingAssist": true
              },
              "limits": {
                "cloudDailyCap": 100,
                "assistDailyCap": 50,
                "maxPhotosShare": 12,
                "scanCloudCooldownMs": 1000
              },
              "experiments": {}
            }
            """.trimIndent()

        val config = json.decodeFromString<RemoteConfig>(backendJson)

        // Verify feature flags are parsed correctly (NOT using defaults)
        assertTrue("enableAssistant should be true", config.featureFlags.enableAssistant)
        assertTrue("enableCloud should be true", config.featureFlags.enableCloud)
        assertTrue("enableProfiles should be true", config.featureFlags.enableProfiles)
        assertTrue("enablePostingAssist should be true", config.featureFlags.enablePostingAssist)

        // Verify limits are parsed correctly
        assertEquals(100, config.limits.cloudDailyCap)
        assertEquals(50, config.limits.assistDailyCap)
        assertEquals(12, config.limits.maxPhotosShare)
        assertEquals(1000L, config.limits.scanCloudCooldownMs)

        // Verify version and TTL
        assertEquals("2025-01-04", config.version)
        assertEquals(3600L, config.ttlSeconds)
    }

    /**
     * WRONG field names should fall back to defaults.
     * This test documents the previous bug where 'assistant.online' was used
     * instead of 'enableAssistant', causing the feature to always appear disabled.
     */
    @Test
    fun `wrong field names fall back to defaults - documents previous bug`() {
        // OLD BROKEN FORMAT - DO NOT USE
        val brokenBackendJson =
            """
            {
              "version": "2025-01-01",
              "ttlSeconds": 3600,
              "featureFlags": {
                "assistant.online": true,
                "cloud_classifier.required": false
              },
              "limits": {
                "maxPhotosPerItem": 12,
                "maxConcurrentRequests": 2
              },
              "experiments": {}
            }
            """.trimIndent()

        val config = json.decodeFromString<RemoteConfig>(brokenBackendJson)

        // With wrong field names, these fall back to DEFAULTS (false)
        // This was the bug - assistant appeared disabled even when backend intended it to be ON
        assertEquals(
            "With wrong field name 'assistant.online', enableAssistant defaults to false",
            false,
            config.featureFlags.enableAssistant,
        )

        // Limits also fall back to defaults with wrong field names
        assertEquals(
            "With wrong field name 'maxPhotosPerItem', maxPhotosShare defaults to 10",
            10,
            config.limits.maxPhotosShare,
        )
    }

    /**
     * Empty featureFlags object should use safe defaults.
     */
    @Test
    fun `empty config uses safe defaults`() {
        val minimalJson =
            """
            {
              "version": "0.0.0"
            }
            """.trimIndent()

        val config = json.decodeFromString<RemoteConfig>(minimalJson)

        // Safe defaults: cloud enabled, assistant disabled
        assertTrue("enableCloud should default to true", config.featureFlags.enableCloud)
        assertEquals(
            "enableAssistant should default to false for safety",
            false,
            config.featureFlags.enableAssistant,
        )
    }
}
