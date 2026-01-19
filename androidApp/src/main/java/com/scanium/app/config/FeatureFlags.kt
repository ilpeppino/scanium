package com.scanium.app.config

import com.scanium.app.BuildConfig

/**
 * Centralized feature flags driven by BuildConfig values.
 *
 * This is the SINGLE SOURCE OF TRUTH for flavor-specific feature gating.
 * All flavor behavior should be driven through these flags, not scattered
 * BuildConfig checks throughout the codebase.
 *
 * Flavor behavior:
 * - DEV: Full functionality with developer mode and diagnostics enabled
 * - BETA/PROD: Restricted feature set for external users
 *
 * @see docs/FLAVOR_GATING.md for complete documentation
 */
object FeatureFlags {
    /**
     * Whether developer mode can be enabled by the user.
     * - DEV: true - Developer can toggle developer mode
     * - BETA/PROD: false - Developer mode is completely hidden
     */
    val allowDeveloperMode: Boolean
        get() = BuildConfig.FEATURE_DEV_MODE

    /**
     * Whether screenshots are allowed.
     * - DEV: true - Screenshots can be toggled on/off
     * - BETA/PROD: false - FLAG_SECURE always applied, no toggle exposed
     */
    val allowScreenshots: Boolean
        get() = BuildConfig.FEATURE_SCREENSHOTS

    /**
     * Whether the AI Assistant feature is available.
     * - DEV: true - Assistant is fully accessible
     * - BETA/PROD: false - Assistant is completely hidden and inaccessible
     */
    val allowAiAssistant: Boolean
        get() = BuildConfig.FEATURE_AI_ASSISTANT

    /**
     * Maximum allowed image resolution tier.
     * - DEV: HIGH - All resolution options available
     * - BETA/PROD: NORMAL - High resolution capped/hidden
     *
     * Returns the tier as a string matching ImageResolutionTier enum names.
     */
    val maxImageResolution: String
        get() = BuildConfig.MAX_IMAGE_RESOLUTION

    /**
     * Whether to show diagnostic labels in item list UI.
     * This controls visibility of:
     * - Aggregation accuracy labels (Low/Medium/High)
     * - Cloud/on-device classification indicators
     *
     * - DEV: true - Show all diagnostic labels for debugging
     * - BETA/PROD: false - Hide technical labels from end users
     */
    val showItemDiagnostics: Boolean
        get() = BuildConfig.FEATURE_ITEM_DIAGNOSTICS

    /**
     * Whether this is a development build (dev flavor).
     * Convenience property combining DEV_MODE_ENABLED with feature flags.
     */
    val isDevBuild: Boolean
        get() = BuildConfig.DEV_MODE_ENABLED

    /**
     * Whether HIGH resolution is allowed based on maxImageResolution.
     */
    val allowHighResolution: Boolean
        get() = maxImageResolution == "HIGH"
}
