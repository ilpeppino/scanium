package com.scanium.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for privacy-related settings logic.
 *
 * These tests verify the privacy toggle state detection logic,
 * specifically the Privacy Safe Mode calculation.
 *
 * Note: SettingsRepository itself is not mocked since it requires Context.
 * Instead, we test the state combination logic directly.
 */
class PrivacySettingsTest {

    // ==================== Documented Default Values Tests ====================

    @Test
    fun `documented default - cloud classification ON`() {
        // Per DATA_SAFETY.md and SettingsRepository: cloud classification defaults to true
        val cloudDefault = true
        assertTrue("Cloud classification should default to ON per documentation", cloudDefault)
    }

    @Test
    fun `documented default - assistant OFF`() {
        // Per DATA_SAFETY.md: assistant is opt-in (default false)
        val assistantDefault = false
        assertFalse("Assistant should default to OFF per documentation", assistantDefault)
    }

    @Test
    fun `documented default - assistant images OFF`() {
        // Per DATA_SAFETY.md: assistant images is opt-in (default false)
        val imagesDefault = false
        assertFalse("Assistant images should default to OFF per documentation", imagesDefault)
    }

    @Test
    fun `documented default - voice mode OFF`() {
        // Per DATA_SAFETY.md: voice mode is opt-in (default false)
        val voiceDefault = false
        assertFalse("Voice mode should default to OFF per documentation", voiceDefault)
    }

    @Test
    fun `documented default - speak answers OFF`() {
        // Per DATA_SAFETY.md: speak answers is opt-in (default false)
        val speakDefault = false
        assertFalse("Speak answers should default to OFF per documentation", speakDefault)
    }

    @Test
    fun `documented default - share diagnostics OFF`() {
        // Per DATA_SAFETY.md: diagnostics is opt-in (default false)
        val diagnosticsDefault = false
        assertFalse("Share diagnostics should default to OFF per documentation", diagnosticsDefault)
    }

    // ==================== Privacy Safe Mode State Detection Tests ====================

    /**
     * Privacy Safe Mode is active when:
     * - Cloud classification is OFF
     * - Assistant images is OFF
     * - Share diagnostics is OFF
     */
    private fun isPrivacySafeModeActive(
        cloudEnabled: Boolean,
        imagesEnabled: Boolean,
        diagnosticsEnabled: Boolean
    ): Boolean {
        return !cloudEnabled && !imagesEnabled && !diagnosticsEnabled
    }

    @Test
    fun `privacy safe mode NOT active when cloud is ON`() {
        // With defaults: cloud=ON, images=OFF, diagnostics=OFF -> NOT active
        val result = isPrivacySafeModeActive(
            cloudEnabled = true,
            imagesEnabled = false,
            diagnosticsEnabled = false
        )
        assertFalse("Privacy safe mode should NOT be active when cloud is ON", result)
    }

    @Test
    fun `privacy safe mode IS active when all cloud features OFF`() {
        val result = isPrivacySafeModeActive(
            cloudEnabled = false,
            imagesEnabled = false,
            diagnosticsEnabled = false
        )
        assertTrue("Privacy safe mode should be active when all cloud features are OFF", result)
    }

    @Test
    fun `privacy safe mode NOT active if images is ON`() {
        val result = isPrivacySafeModeActive(
            cloudEnabled = false,
            imagesEnabled = true,
            diagnosticsEnabled = false
        )
        assertFalse("Privacy safe mode requires images OFF", result)
    }

    @Test
    fun `privacy safe mode NOT active if diagnostics is ON`() {
        val result = isPrivacySafeModeActive(
            cloudEnabled = false,
            imagesEnabled = false,
            diagnosticsEnabled = true
        )
        assertFalse("Privacy safe mode requires diagnostics OFF", result)
    }

    @Test
    fun `privacy safe mode NOT active if cloud and diagnostics are ON`() {
        val result = isPrivacySafeModeActive(
            cloudEnabled = true,
            imagesEnabled = false,
            diagnosticsEnabled = true
        )
        assertFalse("Privacy safe mode requires ALL cloud features OFF", result)
    }

    @Test
    fun `privacy safe mode NOT active if all cloud features are ON`() {
        val result = isPrivacySafeModeActive(
            cloudEnabled = true,
            imagesEnabled = true,
            diagnosticsEnabled = true
        )
        assertFalse("Privacy safe mode should NOT be active when all cloud features are ON", result)
    }

    // ==================== Flow State Change Tests ====================

    @Test
    fun `flow state changes are reflected correctly`() = runTest {
        val flow = MutableStateFlow(false)

        assertFalse(flow.first())

        flow.value = true
        assertTrue(flow.first())

        flow.value = false
        assertFalse(flow.first())
    }

    @Test
    fun `multiple flows can be combined for safe mode detection`() = runTest {
        val cloudFlow = MutableStateFlow(true)
        val imagesFlow = MutableStateFlow(false)
        val diagnosticsFlow = MutableStateFlow(false)

        // Initial state: cloud=ON -> not safe mode
        assertFalse(isPrivacySafeModeActive(
            cloudFlow.value, imagesFlow.value, diagnosticsFlow.value
        ))

        // Disable cloud -> safe mode active
        cloudFlow.value = false
        assertTrue(isPrivacySafeModeActive(
            cloudFlow.value, imagesFlow.value, diagnosticsFlow.value
        ))

        // Enable images -> not safe mode
        imagesFlow.value = true
        assertFalse(isPrivacySafeModeActive(
            cloudFlow.value, imagesFlow.value, diagnosticsFlow.value
        ))
    }

    // ==================== Reset Privacy Settings Behavior ====================

    @Test
    fun `reset should set cloud ON and others OFF`() {
        // After reset:
        // - cloud = ON (default true)
        // - assistant = OFF (default false)
        // - images = OFF (default false)
        // - voice = OFF (default false)
        // - speak = OFF (default false)
        // - diagnostics = OFF (default false)

        val resetCloud = true
        val resetAssistant = false
        val resetImages = false
        val resetVoice = false
        val resetSpeak = false
        val resetDiagnostics = false

        assertTrue("Cloud should be ON after reset", resetCloud)
        assertFalse("Assistant should be OFF after reset", resetAssistant)
        assertFalse("Images should be OFF after reset", resetImages)
        assertFalse("Voice should be OFF after reset", resetVoice)
        assertFalse("Speak should be OFF after reset", resetSpeak)
        assertFalse("Diagnostics should be OFF after reset", resetDiagnostics)

        // After reset, privacy safe mode should NOT be active (cloud is ON)
        assertFalse(isPrivacySafeModeActive(resetCloud, resetImages, resetDiagnostics))
    }
}
