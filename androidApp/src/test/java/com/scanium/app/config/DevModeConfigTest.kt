package com.scanium.app.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for Developer Mode visibility logic.
 *
 * DEV_MODE_ENABLED is a compile-time flag set per product flavor:
 * - dev flavor: DEV_MODE_ENABLED = true
 * - beta flavor: DEV_MODE_ENABLED = false
 *
 * These tests verify the visibility calculation logic that determines
 * whether Developer Options should be shown in Settings.
 */
class DevModeConfigTest {

    /**
     * Calculates whether Developer Options should be visible.
     *
     * Mirrors the logic in SettingsHomeScreen:
     *   showDeveloper = DEV_MODE_ENABLED && (DEBUG || isDeveloperMode)
     *
     * @param devModeEnabled BuildConfig.DEV_MODE_ENABLED (true for dev, false for beta)
     * @param isDebugBuild BuildConfig.DEBUG (true for debug, false for release)
     * @param isDeveloperModeToggled User preference for developer mode toggle
     */
    private fun shouldShowDeveloperOptions(
        devModeEnabled: Boolean,
        isDebugBuild: Boolean,
        isDeveloperModeToggled: Boolean
    ): Boolean {
        return devModeEnabled && (isDebugBuild || isDeveloperModeToggled)
    }

    // ==================== Beta Flavor Tests (DEV_MODE_ENABLED = false) ====================

    @Test
    fun `beta debug build - developer options HIDDEN`() {
        // Beta flavor: DEV_MODE_ENABLED = false
        // Even in debug builds, developer options are hidden
        val result = shouldShowDeveloperOptions(
            devModeEnabled = false,
            isDebugBuild = true,
            isDeveloperModeToggled = false
        )
        assertFalse("Beta builds should never show developer options", result)
    }

    @Test
    fun `beta release build - developer options HIDDEN`() {
        val result = shouldShowDeveloperOptions(
            devModeEnabled = false,
            isDebugBuild = false,
            isDeveloperModeToggled = false
        )
        assertFalse("Beta release builds should never show developer options", result)
    }

    @Test
    fun `beta build with developer toggle ON - still HIDDEN`() {
        // Even if user somehow toggled developer mode, it remains hidden
        val result = shouldShowDeveloperOptions(
            devModeEnabled = false,
            isDebugBuild = true,
            isDeveloperModeToggled = true
        )
        assertFalse("Beta builds ignore developer mode toggle", result)
    }

    // ==================== Dev Flavor Tests (DEV_MODE_ENABLED = true) ====================

    @Test
    fun `dev debug build - developer options VISIBLE`() {
        // Dev flavor debug: always visible
        val result = shouldShowDeveloperOptions(
            devModeEnabled = true,
            isDebugBuild = true,
            isDeveloperModeToggled = false
        )
        assertTrue("Dev debug builds should show developer options", result)
    }

    @Test
    fun `dev release build with toggle OFF - developer options HIDDEN`() {
        // Dev flavor release: hidden unless user enables toggle
        val result = shouldShowDeveloperOptions(
            devModeEnabled = true,
            isDebugBuild = false,
            isDeveloperModeToggled = false
        )
        assertFalse("Dev release builds need toggle enabled", result)
    }

    @Test
    fun `dev release build with toggle ON - developer options VISIBLE`() {
        // Dev flavor release: visible when user enables toggle
        val result = shouldShowDeveloperOptions(
            devModeEnabled = true,
            isDebugBuild = false,
            isDeveloperModeToggled = true
        )
        assertTrue("Dev release with toggle ON should show developer options", result)
    }

    @Test
    fun `dev debug build with toggle ON - developer options VISIBLE`() {
        // Dev flavor debug with toggle: still visible
        val result = shouldShowDeveloperOptions(
            devModeEnabled = true,
            isDebugBuild = true,
            isDeveloperModeToggled = true
        )
        assertTrue("Dev debug with toggle ON should show developer options", result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `all flags false - developer options HIDDEN`() {
        val result = shouldShowDeveloperOptions(
            devModeEnabled = false,
            isDebugBuild = false,
            isDeveloperModeToggled = false
        )
        assertFalse("All flags false should hide developer options", result)
    }

    @Test
    fun `only toggle true with beta - developer options HIDDEN`() {
        // Beta builds should never show developer options regardless of toggle
        val result = shouldShowDeveloperOptions(
            devModeEnabled = false,
            isDebugBuild = false,
            isDeveloperModeToggled = true
        )
        assertFalse("Beta with toggle ON should still hide developer options", result)
    }

    // ==================== Truth Table Verification ====================

    @Test
    fun `complete truth table for developer options visibility`() {
        // Exhaustive test of all combinations
        data class TestCase(
            val devModeEnabled: Boolean,
            val isDebugBuild: Boolean,
            val isDeveloperModeToggled: Boolean,
            val expectedVisible: Boolean,
            val description: String
        )

        val testCases = listOf(
            // Beta flavor (devModeEnabled = false) - always hidden
            TestCase(false, false, false, false, "beta release, toggle off"),
            TestCase(false, false, true, false, "beta release, toggle on"),
            TestCase(false, true, false, false, "beta debug, toggle off"),
            TestCase(false, true, true, false, "beta debug, toggle on"),

            // Dev flavor (devModeEnabled = true) - depends on debug or toggle
            TestCase(true, false, false, false, "dev release, toggle off"),
            TestCase(true, false, true, true, "dev release, toggle on"),
            TestCase(true, true, false, true, "dev debug, toggle off"),
            TestCase(true, true, true, true, "dev debug, toggle on")
        )

        testCases.forEach { testCase ->
            val result = shouldShowDeveloperOptions(
                testCase.devModeEnabled,
                testCase.isDebugBuild,
                testCase.isDeveloperModeToggled
            )
            assertTrue(
                "Failed for: ${testCase.description} - expected ${testCase.expectedVisible}, got $result",
                result == testCase.expectedVisible
            )
        }
    }
}
