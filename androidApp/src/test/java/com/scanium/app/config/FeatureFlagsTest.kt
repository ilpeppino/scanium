package com.scanium.app.config

import com.google.common.truth.Truth.assertThat
import com.scanium.app.BuildConfig
import org.junit.Test

/**
 * Tests for FeatureFlags flavor-specific behavior.
 *
 * These tests verify that FeatureFlags correctly maps BuildConfig values.
 * The actual values tested depend on the build variant (dev/beta/prod).
 */
class FeatureFlagsTest {
    @Test
    fun `allowDeveloperMode matches FEATURE_DEV_MODE BuildConfig`() {
        assertThat(FeatureFlags.allowDeveloperMode).isEqualTo(BuildConfig.FEATURE_DEV_MODE)
    }

    @Test
    fun `allowScreenshots matches FEATURE_SCREENSHOTS BuildConfig`() {
        assertThat(FeatureFlags.allowScreenshots).isEqualTo(BuildConfig.FEATURE_SCREENSHOTS)
    }

    @Test
    fun `allowAiAssistant matches FEATURE_AI_ASSISTANT BuildConfig`() {
        assertThat(FeatureFlags.allowAiAssistant).isEqualTo(BuildConfig.FEATURE_AI_ASSISTANT)
    }

    @Test
    fun `maxImageResolution matches MAX_IMAGE_RESOLUTION BuildConfig`() {
        assertThat(FeatureFlags.maxImageResolution).isEqualTo(BuildConfig.MAX_IMAGE_RESOLUTION)
    }

    @Test
    fun `showItemDiagnostics matches FEATURE_ITEM_DIAGNOSTICS BuildConfig`() {
        assertThat(FeatureFlags.showItemDiagnostics).isEqualTo(BuildConfig.FEATURE_ITEM_DIAGNOSTICS)
    }

    @Test
    fun `isDevBuild matches DEV_MODE_ENABLED BuildConfig`() {
        assertThat(FeatureFlags.isDevBuild).isEqualTo(BuildConfig.DEV_MODE_ENABLED)
    }

    @Test
    fun `allowHighResolution is true when maxImageResolution is HIGH`() {
        val expected = FeatureFlags.maxImageResolution == "HIGH"
        assertThat(FeatureFlags.allowHighResolution).isEqualTo(expected)
    }

    @Test
    fun `dev build has all features enabled`() {
        // This test verifies the expected dev build configuration
        // It will only pass when run against devDebug variant
        if (BuildConfig.DEV_MODE_ENABLED) {
            assertThat(FeatureFlags.allowDeveloperMode).isTrue()
            assertThat(FeatureFlags.allowScreenshots).isTrue()
            assertThat(FeatureFlags.allowAiAssistant).isTrue()
            assertThat(FeatureFlags.maxImageResolution).isEqualTo("HIGH")
            assertThat(FeatureFlags.showItemDiagnostics).isTrue()
            assertThat(FeatureFlags.allowHighResolution).isTrue()
        }
    }

    @Test
    fun `beta and prod builds have AI assistant enabled but restricted dev features`() {
        // This test verifies the expected beta/prod build configuration
        // It will only pass when run against betaDebug or prodDebug variants
        if (!BuildConfig.DEV_MODE_ENABLED) {
            assertThat(FeatureFlags.allowDeveloperMode).isFalse()
            assertThat(FeatureFlags.allowScreenshots).isTrue()
            // AI Assistant is now enabled in beta/prod (no subscription required)
            assertThat(FeatureFlags.allowAiAssistant).isTrue()
            assertThat(FeatureFlags.maxImageResolution).isEqualTo("HIGH")
            assertThat(FeatureFlags.showItemDiagnostics).isFalse()
            assertThat(FeatureFlags.allowHighResolution).isTrue()
        }
    }
}
