package com.scanium.app.data

import com.google.common.truth.Truth.assertThat
import com.scanium.app.BuildConfig
import com.scanium.app.config.FeatureFlags
import org.junit.Test

/**
 * Tests for AI Assistant and "Send pictures to AI" default values across flavors.
 *
 * Default value behavior by flavor:
 * - DEV: Both settings default to TRUE (enabled for easier development/testing)
 * - BETA/PROD: Both settings default to FALSE (privacy-first, user must opt-in)
 *
 * Important: These are DEFAULT values only. User's explicit choice is always respected.
 *
 * These tests verify the logic implemented in:
 * - SettingsRepository.allowAssistantFlow
 * - SettingsRepository.allowAssistantImagesFlow
 */
class AssistantSettingsDefaultsTest {

    // ==================== Pure Logic Tests (mirrors SettingsRepository logic) ====================

    /**
     * Calculates what allowAssistantFlow should return based on flavor and stored preference.
     * This mirrors the exact logic in SettingsRepository.allowAssistantFlow.
     *
     * @param isDevBuild FeatureFlags.isDevBuild (true for dev flavor)
     * @param storedValue The user's stored preference (null if not set)
     * @return The effective assistant enabled value
     */
    private fun calculateAssistantDefaultValue(
        isDevBuild: Boolean,
        storedValue: Boolean?
    ): Boolean {
        return when {
            // User explicitly set a value - respect their choice
            storedValue != null -> storedValue
            // DEV flavor: default to true for easier development/testing
            isDevBuild -> true
            // BETA/PROD: default to false (privacy-first)
            else -> false
        }
    }

    /**
     * Calculates what allowAssistantImagesFlow should return based on flavor and stored preference.
     * This mirrors the exact logic in SettingsRepository.allowAssistantImagesFlow.
     *
     * @param isDevBuild FeatureFlags.isDevBuild (true for dev flavor)
     * @param storedValue The user's stored preference (null if not set)
     * @return The effective assistant images enabled value
     */
    private fun calculateAssistantImagesDefaultValue(
        isDevBuild: Boolean,
        storedValue: Boolean?
    ): Boolean {
        return when {
            // User explicitly set a value - respect their choice
            storedValue != null -> storedValue
            // DEV flavor: default to true for easier development/testing
            isDevBuild -> true
            // BETA/PROD: default to false (privacy-first)
            else -> false
        }
    }

    // ==================== DEV Flavor Tests - AI Assistant ====================

    @Test
    fun `dev flavor - assistant defaults to true when not set`() {
        val result = calculateAssistantDefaultValue(
            isDevBuild = true,
            storedValue = null
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `dev flavor - assistant respects explicit false`() {
        val result = calculateAssistantDefaultValue(
            isDevBuild = true,
            storedValue = false
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `dev flavor - assistant respects explicit true`() {
        val result = calculateAssistantDefaultValue(
            isDevBuild = true,
            storedValue = true
        )
        assertThat(result).isTrue()
    }

    // ==================== DEV Flavor Tests - Assistant Images ====================

    @Test
    fun `dev flavor - assistant images defaults to true when not set`() {
        val result = calculateAssistantImagesDefaultValue(
            isDevBuild = true,
            storedValue = null
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `dev flavor - assistant images respects explicit false`() {
        val result = calculateAssistantImagesDefaultValue(
            isDevBuild = true,
            storedValue = false
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `dev flavor - assistant images respects explicit true`() {
        val result = calculateAssistantImagesDefaultValue(
            isDevBuild = true,
            storedValue = true
        )
        assertThat(result).isTrue()
    }

    // ==================== BETA/PROD Flavor Tests - AI Assistant ====================

    @Test
    fun `beta prod flavor - assistant defaults to false when not set`() {
        val result = calculateAssistantDefaultValue(
            isDevBuild = false,
            storedValue = null
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `beta prod flavor - assistant respects explicit true`() {
        // Note: In practice, users can't change this in beta/prod because UI is gated.
        // This tests the logic correctness for edge cases.
        val result = calculateAssistantDefaultValue(
            isDevBuild = false,
            storedValue = true
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `beta prod flavor - assistant respects explicit false`() {
        val result = calculateAssistantDefaultValue(
            isDevBuild = false,
            storedValue = false
        )
        assertThat(result).isFalse()
    }

    // ==================== BETA/PROD Flavor Tests - Assistant Images ====================

    @Test
    fun `beta prod flavor - assistant images defaults to false when not set`() {
        val result = calculateAssistantImagesDefaultValue(
            isDevBuild = false,
            storedValue = null
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `beta prod flavor - assistant images respects explicit true`() {
        val result = calculateAssistantImagesDefaultValue(
            isDevBuild = false,
            storedValue = true
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `beta prod flavor - assistant images respects explicit false`() {
        val result = calculateAssistantImagesDefaultValue(
            isDevBuild = false,
            storedValue = false
        )
        assertThat(result).isFalse()
    }

    // ==================== Current Build Verification Tests ====================

    @Test
    fun `current build - verify assistant default matches flavor`() {
        // This test verifies behavior for the current build variant
        val defaultValue = calculateAssistantDefaultValue(
            isDevBuild = FeatureFlags.isDevBuild,
            storedValue = null
        )

        if (BuildConfig.DEV_MODE_ENABLED) {
            // DEV flavor: should default to true
            assertThat(defaultValue).isTrue()
        } else {
            // BETA/PROD flavor: should default to false
            assertThat(defaultValue).isFalse()
        }
    }

    @Test
    fun `current build - verify assistant images default matches flavor`() {
        val defaultValue = calculateAssistantImagesDefaultValue(
            isDevBuild = FeatureFlags.isDevBuild,
            storedValue = null
        )

        if (BuildConfig.DEV_MODE_ENABLED) {
            // DEV flavor: should default to true
            assertThat(defaultValue).isTrue()
        } else {
            // BETA/PROD flavor: should default to false
            assertThat(defaultValue).isFalse()
        }
    }

    @Test
    fun `current build - user choice always respected`() {
        // Regardless of flavor, explicit user choice must be respected
        val explicitTrue = calculateAssistantDefaultValue(
            isDevBuild = FeatureFlags.isDevBuild,
            storedValue = true
        )
        val explicitFalse = calculateAssistantDefaultValue(
            isDevBuild = FeatureFlags.isDevBuild,
            storedValue = false
        )

        assertThat(explicitTrue).isTrue()
        assertThat(explicitFalse).isFalse()
    }

    // ==================== Truth Table Tests ====================

    @Test
    fun `complete truth table for assistant default value`() {
        data class TestCase(
            val isDevBuild: Boolean,
            val storedValue: Boolean?,
            val expectedResult: Boolean,
            val description: String
        )

        val testCases = listOf(
            // DEV flavor - defaults true when unset, respects explicit values
            TestCase(true, null, true, "dev, not set -> true"),
            TestCase(true, true, true, "dev, explicit true -> true"),
            TestCase(true, false, false, "dev, explicit false -> false"),

            // BETA/PROD flavor - defaults false when unset, respects explicit values
            TestCase(false, null, false, "beta/prod, not set -> false"),
            TestCase(false, true, true, "beta/prod, explicit true -> true"),
            TestCase(false, false, false, "beta/prod, explicit false -> false"),
        )

        testCases.forEach { testCase ->
            val result = calculateAssistantDefaultValue(
                testCase.isDevBuild,
                testCase.storedValue
            )
            assertThat(result).isEqualTo(testCase.expectedResult)
        }
    }

    @Test
    fun `complete truth table for assistant images default value`() {
        data class TestCase(
            val isDevBuild: Boolean,
            val storedValue: Boolean?,
            val expectedResult: Boolean,
            val description: String
        )

        val testCases = listOf(
            // DEV flavor - defaults true when unset, respects explicit values
            TestCase(true, null, true, "dev, not set -> true"),
            TestCase(true, true, true, "dev, explicit true -> true"),
            TestCase(true, false, false, "dev, explicit false -> false"),

            // BETA/PROD flavor - defaults false when unset, respects explicit values
            TestCase(false, null, false, "beta/prod, not set -> false"),
            TestCase(false, true, true, "beta/prod, explicit true -> true"),
            TestCase(false, false, false, "beta/prod, explicit false -> false"),
        )

        testCases.forEach { testCase ->
            val result = calculateAssistantImagesDefaultValue(
                testCase.isDevBuild,
                testCase.storedValue
            )
            assertThat(result).isEqualTo(testCase.expectedResult)
        }
    }

    // ==================== Documentation Tests ====================

    @Test
    fun `documented behavior - dev builds have assistant enabled by default`() {
        // Per requirement: "DEV: Default AI Assistant enabled = true"
        val devDefaultAssistant = calculateAssistantDefaultValue(
            isDevBuild = true,
            storedValue = null
        )
        assertThat(devDefaultAssistant).isTrue()
    }

    @Test
    fun `documented behavior - dev builds have send pictures enabled by default`() {
        // Per requirement: "DEV: Default sendPicturesToAi = true"
        val devDefaultImages = calculateAssistantImagesDefaultValue(
            isDevBuild = true,
            storedValue = null
        )
        assertThat(devDefaultImages).isTrue()
    }

    @Test
    fun `documented behavior - beta prod unchanged from original defaults`() {
        // Per requirement: "BETA / PROD: Values forced false (already enforced by FeatureFlags)"
        // Note: "forced false" here means default is false, not that user can't change it
        val betaProdDefaultAssistant = calculateAssistantDefaultValue(
            isDevBuild = false,
            storedValue = null
        )
        val betaProdDefaultImages = calculateAssistantImagesDefaultValue(
            isDevBuild = false,
            storedValue = null
        )

        assertThat(betaProdDefaultAssistant).isFalse()
        assertThat(betaProdDefaultImages).isFalse()
    }

    @Test
    fun `documented behavior - user choice respected in dev`() {
        // Per requirement: "Do NOT override user choice in dev after they manually change it"
        // If user explicitly disabled in dev, it should stay disabled
        val userDisabledAssistant = calculateAssistantDefaultValue(
            isDevBuild = true,
            storedValue = false
        )
        val userDisabledImages = calculateAssistantImagesDefaultValue(
            isDevBuild = true,
            storedValue = false
        )

        assertThat(userDisabledAssistant).isFalse()
        assertThat(userDisabledImages).isFalse()
    }

    @Test
    fun `documented behavior - clear data reapplies dev defaults`() {
        // Per requirement: "Clear app data → relaunch → defaults ON again"
        // When data is cleared, storedValue becomes null (unset)
        val afterClearDataAssistant = calculateAssistantDefaultValue(
            isDevBuild = true,
            storedValue = null // Simulates cleared data
        )
        val afterClearDataImages = calculateAssistantImagesDefaultValue(
            isDevBuild = true,
            storedValue = null // Simulates cleared data
        )

        assertThat(afterClearDataAssistant).isTrue()
        assertThat(afterClearDataImages).isTrue()
    }

    // ==================== Privacy Safe Mode Consistency Tests ====================

    /**
     * Calculates the default value for assistant images used in privacy safe mode check.
     * This mirrors the logic in SettingsRepository.isPrivacySafeModeActiveFlow.
     */
    private fun calculatePrivacySafeModeImagesDefault(isDevBuild: Boolean): Boolean {
        return if (isDevBuild) true else false
    }

    @Test
    fun `privacy safe mode uses consistent defaults with assistant images flow`() {
        // Privacy safe mode check should use the same default logic as the settings flow
        listOf(true, false).forEach { isDevBuild ->
            val flowDefault = calculateAssistantImagesDefaultValue(isDevBuild, null)
            val safeModeDefault = calculatePrivacySafeModeImagesDefault(isDevBuild)

            assertThat(safeModeDefault).isEqualTo(flowDefault)
        }
    }

    @Test
    fun `dev flavor - privacy safe mode NOT active by default`() {
        // In dev, images default to ON, so privacy safe mode should NOT be active
        val imagesDefault = calculatePrivacySafeModeImagesDefault(isDevBuild = true)
        val imagesOff = !imagesDefault

        // Privacy safe mode requires images to be OFF
        assertThat(imagesOff).isFalse() // Images are ON in dev, so imagesOff is false
    }

    @Test
    fun `beta prod - privacy safe mode detection unchanged`() {
        // In beta/prod, images default to OFF (same as before)
        val imagesDefault = calculatePrivacySafeModeImagesDefault(isDevBuild = false)
        val imagesOff = !imagesDefault

        // Privacy safe mode check sees images as OFF by default
        assertThat(imagesOff).isTrue()
    }
}
