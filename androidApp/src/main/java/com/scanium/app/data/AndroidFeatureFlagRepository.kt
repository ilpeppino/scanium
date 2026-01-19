package com.scanium.app.data

import android.util.Log
import com.scanium.app.BuildConfig
import com.scanium.app.config.FeatureFlags
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.model.config.AssistantPrerequisite
import com.scanium.app.model.config.AssistantPrerequisiteState
import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.model.config.ConnectionTestErrorType
import com.scanium.app.model.config.ConnectionTestResult
import com.scanium.app.model.config.FeatureFlagRepository
import com.scanium.app.model.config.PrerequisiteCategory
import com.scanium.app.model.user.EntitlementPolicy
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

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
    private val entitlementPolicyFlow: Flow<EntitlementPolicy>,
    private val connectivityStatusProvider: ConnectivityStatusProvider,
    private val apiKeyStore: SecureApiKeyStore,
) : FeatureFlagRepository {
    private val healthCheckClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

    // ==================== Cloud Classification ====================

    override val cloudClassificationUserPreference: Flow<Boolean> =
        settingsRepository.allowCloudClassificationFlow

    override val isCloudClassificationAvailable: Flow<Boolean> =
        combine(
            configProvider.config.map { it.featureFlags.enableCloud },
            entitlementPolicyFlow.map { it.canUseCloudClassification },
        ) { remoteEnabled, entitled ->
            remoteEnabled && entitled
        }

    override val isCloudClassificationEnabled: Flow<Boolean> =
        combine(
            cloudClassificationUserPreference,
            isCloudClassificationAvailable,
        ) { userEnabled, available ->
            userEnabled && available
        }

    override suspend fun setCloudClassificationEnabled(enabled: Boolean) {
        settingsRepository.setAllowCloudClassification(enabled)
    }

    // ==================== Assistant ====================

    override val assistantUserPreference: Flow<Boolean> =
        settingsRepository.allowAssistantFlow

    override val isAssistantAvailable: Flow<Boolean> =
        combine(
            configProvider.config.map { it.featureFlags.enableAssistant },
            entitlementPolicyFlow.map { it.canUseAssistant },
            settingsRepository.developerModeFlow,
        ) { remoteEnabled, entitled, developerMode ->
            // If BuildConfig allows AI assistant for this flavor (dev/beta/prod), bypass subscription checks
            if (FeatureFlags.allowAiAssistant) {
                return@combine true
            }
            // Fallback: In DEBUG builds with developer mode, bypass subscription and remote config checks
            val isDeveloperOverride = BuildConfig.DEBUG && developerMode
            isDeveloperOverride || (remoteEnabled && entitled)
        }

    override val isAssistantEnabled: Flow<Boolean> =
        combine(
            assistantUserPreference,
            isAssistantAvailable,
        ) { userEnabled, available ->
            userEnabled && available
        }

    override val assistantPrerequisiteState: Flow<AssistantPrerequisiteState> =
        combine(
            entitlementPolicyFlow,
            configProvider.config,
            connectivityStatusProvider.statusFlow,
            settingsRepository.developerModeFlow,
        ) { entitlement, config, connectivity, developerMode ->
            val baseUrl = BuildConfig.SCANIUM_API_BASE_URL
            val apiKey = apiKeyStore.getApiKey()

            // If BuildConfig allows AI assistant for this flavor (dev/beta/prod), bypass subscription and remote checks
            val isFlavorAllowed = FeatureFlags.allowAiAssistant
            // In DEBUG builds with developer mode enabled, bypass subscription and remote flag checks
            val isDeveloperOverride = BuildConfig.DEBUG && developerMode
            val bypassSubAndRemote = isFlavorAllowed || isDeveloperOverride

            val prerequisites =
                listOf(
                    AssistantPrerequisite(
                        id = "subscription",
                        displayName = "Pro or Developer subscription",
                        description =
                            when {
                                isFlavorAllowed -> "Enabled for this app version"
                                isDeveloperOverride -> "Bypassed (Developer Mode)"
                                else -> "Assistant requires a Pro or Developer subscription"
                            },
                        satisfied = bypassSubAndRemote || entitlement.canUseAssistant,
                        category = PrerequisiteCategory.SUBSCRIPTION,
                    ),
                    AssistantPrerequisite(
                        id = "remote_flag",
                        displayName = "Feature enabled by server",
                        description =
                            when {
                                isFlavorAllowed -> "Enabled for this app version"
                                isDeveloperOverride -> "Bypassed (Developer Mode)"
                                else -> "Assistant feature must be enabled on the server"
                            },
                        satisfied = bypassSubAndRemote || config.featureFlags.enableAssistant,
                        category = PrerequisiteCategory.REMOTE_CONFIG,
                    ),
                    AssistantPrerequisite(
                        id = "backend_url",
                        displayName = "Backend URL configured",
                        description = "A valid backend URL must be configured",
                        satisfied = baseUrl.isNotBlank(),
                        category = PrerequisiteCategory.LOCAL_CONFIG,
                    ),
                    AssistantPrerequisite(
                        id = "api_key",
                        displayName = "API key configured",
                        description = "A valid API key must be configured",
                        satisfied = !apiKey.isNullOrBlank(),
                        category = PrerequisiteCategory.LOCAL_CONFIG,
                    ),
                    AssistantPrerequisite(
                        id = "connectivity",
                        displayName = "Network connection",
                        description = "Device must be connected to the internet",
                        satisfied = connectivity == ConnectivityStatus.ONLINE,
                        category = PrerequisiteCategory.CONNECTIVITY,
                    ),
                )

            AssistantPrerequisiteState(prerequisites = prerequisites)
        }

    override suspend fun setAssistantEnabled(enabled: Boolean): Boolean {
        if (enabled) {
            // Check if all prerequisites are met before enabling
            val state = assistantPrerequisiteState.first()
            if (!state.allSatisfied) {
                return false
            }
        }
        settingsRepository.setAllowAssistant(enabled)
        return true
    }

    override suspend fun testAssistantConnection(): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.SCANIUM_API_BASE_URL
            val apiKey = apiKeyStore.getApiKey()
            val endpoint = "/health"
            val method = "GET"

            // DIAG: Log connection test parameters
            Log.d("ScaniumNet", "FeatureFlags: testAssistantConnection baseUrl=$baseUrl")
            if (apiKey != null) {
                Log.d("ScaniumAuth", "FeatureFlags: apiKey present len=${apiKey.length} prefix=${apiKey.take(6)}...")
            } else {
                Log.w("ScaniumAuth", "FeatureFlags: apiKey is NULL")
            }

            if (baseUrl.isBlank()) {
                return@withContext ConnectionTestResult.Failure(
                    errorType = ConnectionTestErrorType.NOT_CONFIGURED,
                    message = "Backend URL is not configured",
                    endpoint = endpoint,
                    method = method,
                )
            }

            if (apiKey.isNullOrBlank()) {
                return@withContext ConnectionTestResult.Failure(
                    errorType = ConnectionTestErrorType.NOT_CONFIGURED,
                    message = "API key is not configured",
                    endpoint = endpoint,
                    method = method,
                )
            }

            val healthEndpoint = "${baseUrl.trimEnd('/')}$endpoint"
            Log.d("ScaniumNet", "FeatureFlags: healthEndpoint=$healthEndpoint")

            try {
                Log.d("ScaniumAuth", "FeatureFlags: Adding X-API-Key header to health check")
                val request =
                    Request
                        .Builder()
                        .url(healthEndpoint)
                        .get()
                        .header("X-API-Key", apiKey)
                        .header("X-Client", "Scanium-Android")
                        .header("X-App-Version", BuildConfig.VERSION_NAME)
                        .build()

                healthCheckClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            ConnectionTestResult.Success(
                                httpStatus = response.code,
                                endpoint = endpoint,
                            )
                        }

                        response.code == 401 || response.code == 403 -> {
                            ConnectionTestResult.Failure(
                                errorType = ConnectionTestErrorType.UNAUTHORIZED,
                                message = "Invalid API key or unauthorized access",
                                httpStatus = response.code,
                                endpoint = endpoint,
                                method = method,
                            )
                        }

                        response.code == 404 -> {
                            ConnectionTestResult.Failure(
                                errorType = ConnectionTestErrorType.NOT_FOUND,
                                message = "Endpoint not found (wrong base URL or route)",
                                httpStatus = response.code,
                                endpoint = endpoint,
                                method = method,
                            )
                        }

                        response.code in 500..599 -> {
                            ConnectionTestResult.Failure(
                                errorType = ConnectionTestErrorType.SERVER_ERROR,
                                message = "Server error (${response.code})",
                                httpStatus = response.code,
                                endpoint = endpoint,
                                method = method,
                            )
                        }

                        else -> {
                            ConnectionTestResult.Failure(
                                errorType = ConnectionTestErrorType.SERVER_ERROR,
                                message = "Unexpected response: ${response.code}",
                                httpStatus = response.code,
                                endpoint = endpoint,
                                method = method,
                            )
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                ConnectionTestResult.Failure(
                    errorType = ConnectionTestErrorType.TIMEOUT,
                    message = "Connection timed out",
                    endpoint = endpoint,
                    method = method,
                )
            } catch (e: java.net.UnknownHostException) {
                ConnectionTestResult.Failure(
                    errorType = ConnectionTestErrorType.NETWORK_UNREACHABLE,
                    message = "Cannot resolve server address",
                    endpoint = endpoint,
                    method = method,
                )
            } catch (e: java.net.ConnectException) {
                ConnectionTestResult.Failure(
                    errorType = ConnectionTestErrorType.NETWORK_UNREACHABLE,
                    message = "Cannot connect to server",
                    endpoint = endpoint,
                    method = method,
                )
            } catch (e: javax.net.ssl.SSLException) {
                ConnectionTestResult.Failure(
                    errorType = ConnectionTestErrorType.NETWORK_UNREACHABLE,
                    message = "SSL/TLS error: ${e.message?.take(30) ?: "certificate issue"}",
                    endpoint = endpoint,
                    method = method,
                )
            } catch (e: Exception) {
                ConnectionTestResult.Failure(
                    errorType = ConnectionTestErrorType.NETWORK_UNREACHABLE,
                    message = e.message?.take(50) ?: "Unknown error",
                    endpoint = endpoint,
                    method = method,
                )
            }
        }
}
