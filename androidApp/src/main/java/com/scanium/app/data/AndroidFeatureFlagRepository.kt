package com.scanium.app.data

import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.model.config.FeatureFlagRepository
import com.scanium.app.model.user.EntitlementPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Android implementation of [FeatureFlagRepository].
 *
 * Consolidates feature flag state from three sources:
 * 1. User preferences (SettingsRepository) - local toggle controlled by user
 * 2. Remote config (ConfigProvider) - server-side feature flags
 * 3. Entitlements (EntitlementManager) - subscription/billing status
 *
 * This is the single source of truth for feature availability,
 * resolving TECH-006: "Feature flags scattered (BuildConfig, Settings, RemoteConfig)".
 */
class AndroidFeatureFlagRepository(
    private val settingsRepository: SettingsRepository,
    private val configProvider: ConfigProvider,
    private val entitlementPolicyFlow: Flow<EntitlementPolicy>
) : FeatureFlagRepository {

    // ==================== Cloud Classification ====================

    override val cloudClassificationUserPreference: Flow<Boolean> =
        settingsRepository.allowCloudClassificationFlow

    override val isCloudClassificationAvailable: Flow<Boolean> = combine(
        configProvider.config.map { it.featureFlags.enableCloud },
        entitlementPolicyFlow.map { it.canUseCloudClassification }
    ) { remoteEnabled, entitled ->
        remoteEnabled && entitled
    }

    override val isCloudClassificationEnabled: Flow<Boolean> = combine(
        cloudClassificationUserPreference,
        isCloudClassificationAvailable
    ) { userEnabled, available ->
        userEnabled && available
    }

    override suspend fun setCloudClassificationEnabled(enabled: Boolean) {
        settingsRepository.setAllowCloudClassification(enabled)
    }

    // ==================== Assistant ====================

    override val assistantUserPreference: Flow<Boolean> =
        settingsRepository.allowAssistantFlow

    override val isAssistantAvailable: Flow<Boolean> = combine(
        configProvider.config.map { it.featureFlags.enableAssistant },
        entitlementPolicyFlow.map { it.canUseAssistant }
    ) { remoteEnabled, entitled ->
        remoteEnabled && entitled
    }

    override val isAssistantEnabled: Flow<Boolean> = combine(
        assistantUserPreference,
        isAssistantAvailable
    ) { userEnabled, available ->
        userEnabled && available
    }

    override suspend fun setAssistantEnabled(enabled: Boolean) {
        settingsRepository.setAllowAssistant(enabled)
    }
}
