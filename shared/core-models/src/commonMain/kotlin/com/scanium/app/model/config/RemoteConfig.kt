package com.scanium.app.model.config

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class RemoteConfig(
    val version: String = "1.0.0",
    val fetchedAt: Long = 0,
    val ttlSeconds: Long = 3600,
    val featureFlags: FeatureFlags = FeatureFlags(),
    val limits: Limits = Limits(),
    val experiments: Map<String, Experiment> = emptyMap(),
)

@Serializable
data class FeatureFlags(
    val enableCloud: Boolean = true,
    val enableAssistant: Boolean = false,
    val enableProfiles: Boolean = false,
    val enablePostingAssist: Boolean = false,
    /** Enable Vision Insights UI in assistant (displays detected colors, brands, etc.) */
    val enableVisionInsights: Boolean = true,
)

@Serializable
data class Limits(
    val cloudDailyCap: Int = 50,
    val assistDailyCap: Int = 10,
    val maxPhotosShare: Int = 10,
    val scanCloudCooldownMs: Long = 1000,
)

@Serializable
data class Experiment(
    val id: String,
    val variant: String,
    val parameters: Map<String, String> = emptyMap(),
)

interface ConfigProvider {
    val config: Flow<RemoteConfig>

    suspend fun refresh(force: Boolean = false)

    fun getFlag(
        name: String,
        default: Boolean,
    ): Boolean

    fun getLimit(
        name: String,
        default: Int,
    ): Int

    fun getLimit(
        name: String,
        default: Long,
    ): Long

    fun getExperimentVariant(id: String): String?
}
