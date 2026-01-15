package com.scanium.app.selling.assistant

import com.scanium.app.BuildConfig

/**
 * Centralized auth gating logic for the AI Assistant.
 *
 * Controls whether Google sign-in is required to use the assistant based on build flavor:
 * - DEV flavor: Sign-in NOT required (bypass enabled via DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT)
 * - BETA flavor: Sign-in REQUIRED
 * - PROD flavor: Sign-in REQUIRED
 *
 * This is a compile-time constant per flavor, ensuring beta/prod cannot accidentally
 * bypass authentication at runtime.
 *
 * SECURITY NOTE: This does not weaken backend security. When bypass is enabled,
 * the dev build uses a local mock assistant provider instead of hitting the
 * authenticated backend endpoint.
 */
object AssistantAuthGating {

    /**
     * Returns true if the AI Assistant requires Google sign-in before use.
     *
     * - Returns false for DEV builds (bypass enabled)
     * - Returns true for BETA/PROD builds (sign-in required)
     *
     * This is evaluated at compile time based on the build flavor.
     */
    fun requiresGoogleSignIn(): Boolean {
        return !BuildConfig.DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT
    }

    /**
     * Returns true if dev bypass mode is active.
     *
     * When true, unsigned users can use the assistant via a mock provider
     * that returns helpful local responses without hitting the backend.
     */
    fun isDevBypassEnabled(): Boolean {
        return BuildConfig.DEV_BYPASS_GOOGLE_SIGNIN_FOR_ASSISTANT
    }
}
