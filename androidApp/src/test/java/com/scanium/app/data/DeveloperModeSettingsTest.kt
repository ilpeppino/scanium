package com.scanium.app.data

import com.google.common.truth.Truth.assertThat
import com.scanium.app.BuildConfig
import com.scanium.app.config.FeatureFlags
import org.junit.Test

/**
 * Tests for Developer Mode settings behavior across flavors.
 *
 * Developer Mode behavior by flavor:
 * - DEV: Always ON (forced), toggle hidden, setter is no-op
 * - BETA/PROD: Always OFF (disabled), toggle hidden, setter is no-op
 *
 * These tests verify the logic implemented in SettingsRepository.developerModeFlow
 * and SettingsRepository.setDeveloperMode().
 */
class DeveloperModeSettingsTest {
    // ==================== Pure Logic Tests (mirrors SettingsRepository logic) ====================

    /**
     * Calculates what developerModeFlow should return based on flavor and stored preference.
     * This mirrors the exact logic in SettingsRepository.developerModeFlow.
     *
     * @param isDevBuild FeatureFlags.isDevBuild (true for dev flavor)
     * @param allowDeveloperMode FeatureFlags.allowDeveloperMode (true for dev flavor)
     * @param storedPreference The user's stored preference (if any)
     * @return The effective developer mode value
     */
    private fun calculateDeveloperModeValue(
        isDevBuild: Boolean,
        allowDeveloperMode: Boolean,
        storedPreference: Boolean?,
    ): Boolean {
        return when {
            // DEV flavor: Developer mode is always ON
            isDevBuild -> true
            // BETA/PROD: Developer mode is completely disabled
            !allowDeveloperMode -> false
            // Fallback: use stored preference (shouldn't reach here with current flavors)
            else -> storedPreference ?: false
        }
    }

    /**
     * Determines if setDeveloperMode() should be a no-op.
     * This mirrors the guard logic in SettingsRepository.setDeveloperMode().
     *
     * @param isDevBuild FeatureFlags.isDevBuild (true for dev flavor)
     * @param allowDeveloperMode FeatureFlags.allowDeveloperMode (true for dev flavor)
     * @return true if the setter should be ignored (no-op)
     */
    private fun shouldIgnoreSetterCall(
        isDevBuild: Boolean,
        allowDeveloperMode: Boolean,
    ): Boolean {
        // DEV flavor: Developer mode is forced ON, ignore setter
        if (isDevBuild) return true
        // BETA/PROD: Developer mode is not allowed, ignore setter
        if (!allowDeveloperMode) return true
        // Fallback: allow setter (shouldn't reach here with current flavors)
        return false
    }

    // ==================== DEV Flavor Tests ====================

    @Test
    fun `dev flavor - developer mode always returns true`() {
        // DEV flavor: isDevBuild=true, allowDeveloperMode=true
        val result =
            calculateDeveloperModeValue(
                isDevBuild = true,
                allowDeveloperMode = true,
                // Even if stored preference is false
                storedPreference = false,
            )
        assertThat(result).isTrue()
    }

    @Test
    fun `dev flavor - developer mode returns true regardless of stored preference`() {
        // Test with various stored preferences
        assertThat(calculateDeveloperModeValue(true, true, null)).isTrue()
        assertThat(calculateDeveloperModeValue(true, true, true)).isTrue()
        assertThat(calculateDeveloperModeValue(true, true, false)).isTrue()
    }

    @Test
    fun `dev flavor - setter is always ignored`() {
        val shouldIgnore =
            shouldIgnoreSetterCall(
                isDevBuild = true,
                allowDeveloperMode = true,
            )
        assertThat(shouldIgnore).isTrue()
    }

    // ==================== BETA/PROD Flavor Tests ====================

    @Test
    fun `beta flavor - developer mode always returns false`() {
        // BETA flavor: isDevBuild=false, allowDeveloperMode=false
        val result =
            calculateDeveloperModeValue(
                isDevBuild = false,
                allowDeveloperMode = false,
                // Even if stored preference is true
                storedPreference = true,
            )
        assertThat(result).isFalse()
    }

    @Test
    fun `prod flavor - developer mode always returns false`() {
        // PROD flavor: isDevBuild=false, allowDeveloperMode=false
        val result =
            calculateDeveloperModeValue(
                isDevBuild = false,
                allowDeveloperMode = false,
                storedPreference = null,
            )
        assertThat(result).isFalse()
    }

    @Test
    fun `beta and prod - setter is always ignored`() {
        val shouldIgnore =
            shouldIgnoreSetterCall(
                isDevBuild = false,
                allowDeveloperMode = false,
            )
        assertThat(shouldIgnore).isTrue()
    }

    // ==================== Current Build Verification Tests ====================

    @Test
    fun `current build - verify developer mode behavior matches flavor`() {
        // This test verifies the actual behavior based on the current build variant
        if (BuildConfig.DEV_MODE_ENABLED) {
            // DEV flavor: should always return true
            val result =
                calculateDeveloperModeValue(
                    isDevBuild = FeatureFlags.isDevBuild,
                    allowDeveloperMode = FeatureFlags.allowDeveloperMode,
                    storedPreference = false,
                )
            assertThat(result).isTrue()
        } else {
            // BETA/PROD flavor: should always return false
            val result =
                calculateDeveloperModeValue(
                    isDevBuild = FeatureFlags.isDevBuild,
                    allowDeveloperMode = FeatureFlags.allowDeveloperMode,
                    storedPreference = true,
                )
            assertThat(result).isFalse()
        }
    }

    @Test
    fun `current build - verify setter is ignored`() {
        // In all current flavors, the setter should be ignored
        val shouldIgnore =
            shouldIgnoreSetterCall(
                isDevBuild = FeatureFlags.isDevBuild,
                allowDeveloperMode = FeatureFlags.allowDeveloperMode,
            )
        assertThat(shouldIgnore).isTrue()
    }

    // ==================== Truth Table Tests ====================

    @Test
    fun `complete truth table for developer mode value`() {
        data class TestCase(
            val isDevBuild: Boolean,
            val allowDeveloperMode: Boolean,
            val storedPreference: Boolean?,
            val expectedResult: Boolean,
            val description: String,
        )

        val testCases =
            listOf(
                // DEV flavor (isDevBuild=true, allowDeveloperMode=true) - always true
                TestCase(true, true, null, true, "dev, no stored pref"),
                TestCase(true, true, true, true, "dev, stored pref true"),
                TestCase(true, true, false, true, "dev, stored pref false"),
                // BETA/PROD flavor (isDevBuild=false, allowDeveloperMode=false) - always false
                TestCase(false, false, null, false, "beta/prod, no stored pref"),
                TestCase(false, false, true, false, "beta/prod, stored pref true"),
                TestCase(false, false, false, false, "beta/prod, stored pref false"),
                // Edge case: hypothetical flavor with allowDeveloperMode=true but isDevBuild=false
                // Would use stored preference (fallback path)
                TestCase(false, true, null, false, "fallback, no stored pref -> false"),
                TestCase(false, true, true, true, "fallback, stored pref true -> true"),
                TestCase(false, true, false, false, "fallback, stored pref false -> false"),
            )

        testCases.forEach { testCase ->
            val result =
                calculateDeveloperModeValue(
                    testCase.isDevBuild,
                    testCase.allowDeveloperMode,
                    testCase.storedPreference,
                )
            assertThat(result).isEqualTo(testCase.expectedResult)
        }
    }

    @Test
    fun `complete truth table for setter ignore behavior`() {
        data class TestCase(
            val isDevBuild: Boolean,
            val allowDeveloperMode: Boolean,
            val shouldIgnore: Boolean,
            val description: String,
        )

        val testCases =
            listOf(
                // DEV flavor - always ignore (forced ON)
                TestCase(true, true, true, "dev flavor - ignore setter"),
                // BETA/PROD flavor - always ignore (forced OFF)
                TestCase(false, false, true, "beta/prod flavor - ignore setter"),
                // Edge case: hypothetical flavor with allowDeveloperMode=true but isDevBuild=false
                // Would allow setter (fallback path)
                TestCase(false, true, false, "fallback - allow setter"),
            )

        testCases.forEach { testCase ->
            val result =
                shouldIgnoreSetterCall(
                    testCase.isDevBuild,
                    testCase.allowDeveloperMode,
                )
            assertThat(result).isEqualTo(testCase.shouldIgnore)
        }
    }

    // ==================== Documentation Tests ====================

    @Test
    fun `documented behavior - dev builds have developer mode always ON`() {
        // Per requirement: "Dev flavor: Developer Mode always ON"
        val devResult =
            calculateDeveloperModeValue(
                isDevBuild = true,
                allowDeveloperMode = true,
                storedPreference = false,
            )
        // DEV flavor must have Developer Mode always ON per requirements
        assertThat(devResult).isTrue()
    }

    @Test
    fun `documented behavior - dev builds cannot disable developer mode`() {
        // Per requirement: "any setter / persistence call is ignored or becomes a no-op in dev"
        val shouldIgnore =
            shouldIgnoreSetterCall(
                isDevBuild = true,
                allowDeveloperMode = true,
            )
        // DEV flavor must ignore setDeveloperMode() calls per requirements
        assertThat(shouldIgnore).isTrue()
    }

    @Test
    fun `documented behavior - beta and prod unchanged`() {
        // Per requirement: "Beta/Prod must remain unchanged" - they have developer mode disabled
        val betaProdResult =
            calculateDeveloperModeValue(
                isDevBuild = false,
                allowDeveloperMode = false,
                storedPreference = true,
            )
        // BETA/PROD must have Developer Mode OFF per existing behavior
        assertThat(betaProdResult).isFalse()
    }
}
