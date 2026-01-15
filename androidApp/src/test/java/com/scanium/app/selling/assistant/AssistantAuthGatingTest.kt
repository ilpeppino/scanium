package com.scanium.app.selling.assistant

import com.google.common.truth.Truth.assertThat
import com.scanium.app.BuildConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for AssistantAuthGating.
 *
 * These tests verify that:
 * 1. The gating functions return consistent values based on BuildConfig
 * 2. requiresGoogleSignIn() and isDevBypassEnabled() are inverses of each other
 *
 * The actual flag value depends on which build variant is being tested:
 * - devDebug: DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT = true
 * - betaDebug/prodDebug: DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT = false
 */
@RunWith(RobolectricTestRunner::class)
class AssistantAuthGatingTest {

    @Test
    fun `requiresGoogleSignIn and isDevBypassEnabled are inverses`() {
        // These should always be inverses of each other
        assertThat(AssistantAuthGating.requiresGoogleSignIn())
            .isEqualTo(!AssistantAuthGating.isDevBypassEnabled())
    }

    @Test
    fun `isDevBypassEnabled matches BuildConfig flag`() {
        // Direct check against BuildConfig
        assertThat(AssistantAuthGating.isDevBypassEnabled())
            .isEqualTo(BuildConfig.DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT)
    }

    @Test
    fun `requiresGoogleSignIn returns opposite of BuildConfig flag`() {
        // requiresGoogleSignIn should be the inverse of the bypass flag
        assertThat(AssistantAuthGating.requiresGoogleSignIn())
            .isEqualTo(!BuildConfig.DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT)
    }

    /**
     * This test documents the expected behavior per flavor.
     * It will pass for all flavors but the assertion reflects the current build.
     *
     * When running `./gradlew :androidApp:testDevDebugUnitTest`:
     *   - DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT = true
     *   - requiresGoogleSignIn() = false (bypass enabled)
     *
     * When running `./gradlew :androidApp:testBetaDebugUnitTest`:
     *   - DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT = false
     *   - requiresGoogleSignIn() = true (sign-in required)
     *
     * When running `./gradlew :androidApp:testProdDebugUnitTest`:
     *   - DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT = false
     *   - requiresGoogleSignIn() = true (sign-in required)
     */
    @Test
    fun `documents current flavor behavior`() {
        // This test documents what the current build variant should return
        val bypassEnabled = BuildConfig.DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT

        if (bypassEnabled) {
            // DEV flavor: bypass is enabled
            assertThat(AssistantAuthGating.requiresGoogleSignIn()).isFalse()
            assertThat(AssistantAuthGating.isDevBypassEnabled()).isTrue()
        } else {
            // BETA/PROD flavor: sign-in required
            assertThat(AssistantAuthGating.requiresGoogleSignIn()).isTrue()
            assertThat(AssistantAuthGating.isDevBypassEnabled()).isFalse()
        }
    }
}
