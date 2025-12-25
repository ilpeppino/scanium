package com.scanium.app.model.config

import kotlinx.coroutines.flow.Flow

/**
 * Centralized repository for feature flags, consolidating configuration from:
 * - User preferences (local toggle)
 * - Remote config (server-side feature flags)
 * - Entitlements (user subscription status)
 *
 * This provides a single source of truth for feature availability,
 * eliminating configuration sprawl across BuildConfig, Settings, and Preferences.
 */
interface FeatureFlagRepository {

    /**
     * Whether cloud classification is effectively enabled.
     * This is the single source of truth combining:
     * - User preference (allowCloudClassification)
     * - Remote feature flag (enableCloud)
     * - User entitlement (canUseCloudClassification)
     *
     * Returns true only if ALL conditions are met.
     */
    val isCloudClassificationEnabled: Flow<Boolean>

    /**
     * User's preference for cloud classification.
     * This is the user-controllable setting, independent of
     * remote config and entitlements.
     */
    val cloudClassificationUserPreference: Flow<Boolean>

    /**
     * Whether cloud classification is available (remote + entitlement).
     * True if the feature is enabled remotely AND user is entitled.
     * This is useful for UI to show if the feature CAN be enabled,
     * even if the user has currently disabled it.
     */
    val isCloudClassificationAvailable: Flow<Boolean>

    /**
     * Whether AI assistant is effectively enabled.
     */
    val isAssistantEnabled: Flow<Boolean>

    /**
     * User's preference for AI assistant.
     */
    val assistantUserPreference: Flow<Boolean>

    /**
     * Whether AI assistant is available (remote + entitlement).
     */
    val isAssistantAvailable: Flow<Boolean>

    /**
     * Enable or disable cloud classification preference.
     * This only affects the user preference, not remote config or entitlements.
     */
    suspend fun setCloudClassificationEnabled(enabled: Boolean)

    /**
     * Enable or disable AI assistant preference.
     */
    suspend fun setAssistantEnabled(enabled: Boolean)
}
