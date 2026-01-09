package com.scanium.app.data

import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.model.config.FeatureFlags
import com.scanium.app.model.config.RemoteConfig
import com.scanium.app.model.user.EntitlementPolicy
import com.scanium.app.model.user.FreeEntitlements
import com.scanium.app.model.user.ProEntitlements
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidFeatureFlagRepositoryTest {
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val configProvider = mockk<ConfigProvider>(relaxed = true)
    private val connectivityStatusProvider = mockk<ConnectivityStatusProvider>(relaxed = true)
    private val apiKeyStore = mockk<SecureApiKeyStore>(relaxed = true)

    private val allowCloudFlow = MutableStateFlow(true)
    private val allowAssistantFlow = MutableStateFlow(false)
    private val developerModeFlow = MutableStateFlow(false)
    private val remoteConfigFlow = MutableStateFlow(RemoteConfig())
    private val entitlementFlow = MutableStateFlow<EntitlementPolicy>(FreeEntitlements)
    private val connectivityFlow = MutableStateFlow(ConnectivityStatus.ONLINE)

    private fun createRepository(): AndroidFeatureFlagRepository {
        every { settingsRepository.allowCloudClassificationFlow } returns allowCloudFlow
        every { settingsRepository.allowAssistantFlow } returns allowAssistantFlow
        every { settingsRepository.developerModeFlow } returns developerModeFlow
        every { configProvider.config } returns remoteConfigFlow
        every { connectivityStatusProvider.statusFlow } returns connectivityFlow
        every { apiKeyStore.getApiKey() } returns "test-api-key"

        return AndroidFeatureFlagRepository(
            settingsRepository = settingsRepository,
            configProvider = configProvider,
            entitlementPolicyFlow = entitlementFlow,
            connectivityStatusProvider = connectivityStatusProvider,
            apiKeyStore = apiKeyStore,
        )
    }

    // ==================== Cloud Classification Tests ====================

    @Test
    fun `cloud classification enabled when all conditions met`() =
        runTest {
            val repository = createRepository()

            // All conditions: user pref ON, remote ON (default), entitlement OK (FreeEntitlements.canUseCloudClassification = true)
            allowCloudFlow.value = true
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableCloud = true))
            entitlementFlow.value = FreeEntitlements

            assertTrue(repository.isCloudClassificationEnabled.first())
        }

    @Test
    fun `cloud classification disabled when user preference off`() =
        runTest {
            val repository = createRepository()

            allowCloudFlow.value = false
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableCloud = true))
            entitlementFlow.value = ProEntitlements

            assertFalse(repository.isCloudClassificationEnabled.first())
        }

    @Test
    fun `cloud classification disabled when remote flag off`() =
        runTest {
            val repository = createRepository()

            allowCloudFlow.value = true
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableCloud = false))
            entitlementFlow.value = ProEntitlements

            assertFalse(repository.isCloudClassificationEnabled.first())
        }

    @Test
    fun `cloud classification disabled when not entitled`() =
        runTest {
            val repository = createRepository()

            // Create a mock entitlement that doesn't allow cloud
            val noCloudEntitlement =
                object : EntitlementPolicy {
                    override val canUseCloudClassification = false
                    override val canUseAssistant = false
                    override val canBatchExport = false
                    override val maxDailyCloudClassifications = 0
                    override val isPro = false
                }

            allowCloudFlow.value = true
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableCloud = true))
            entitlementFlow.value = noCloudEntitlement

            assertFalse(repository.isCloudClassificationEnabled.first())
        }

    @Test
    fun `cloud classification available reflects remote and entitlement only`() =
        runTest {
            val repository = createRepository()

            // User pref OFF but available should still be true if remote + entitlement OK
            allowCloudFlow.value = false
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableCloud = true))
            entitlementFlow.value = ProEntitlements

            assertTrue(repository.isCloudClassificationAvailable.first())
            assertFalse(repository.isCloudClassificationEnabled.first())
        }

    @Test
    fun `setCloudClassificationEnabled delegates to settings repository`() =
        runTest {
            val repository = createRepository()

            repository.setCloudClassificationEnabled(false)

            coVerify { settingsRepository.setAllowCloudClassification(false) }
        }

    // ==================== Assistant Tests ====================

    @Test
    fun `assistant enabled when all conditions met`() =
        runTest {
            val repository = createRepository()

            allowAssistantFlow.value = true
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableAssistant = true))
            entitlementFlow.value = ProEntitlements // Pro has canUseAssistant = true

            assertTrue(repository.isAssistantEnabled.first())
        }

    @Test
    fun `assistant enabled for free users when BuildConfig allows (subscription bypass)`() =
        runTest {
            val repository = createRepository()

            allowAssistantFlow.value = true
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableAssistant = true))
            entitlementFlow.value = FreeEntitlements // Free has canUseAssistant = false

            // With BuildConfig.FEATURE_AI_ASSISTANT = true for all flavors, subscription checks are bypassed
            assertTrue(repository.isAssistantEnabled.first())
        }

    @Test
    fun `assistant available when BuildConfig allows (bypasses subscription check)`() =
        runTest {
            val repository = createRepository()

            // User pref OFF but available should be true due to BuildConfig.FEATURE_AI_ASSISTANT = true
            allowAssistantFlow.value = false
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableAssistant = false))
            entitlementFlow.value = FreeEntitlements // No subscription

            // With BuildConfig.FEATURE_AI_ASSISTANT = true, assistant is available regardless of subscription
            assertTrue(repository.isAssistantAvailable.first())
            // But not enabled because user preference is OFF
            assertFalse(repository.isAssistantEnabled.first())
        }

    @Test
    fun `setAssistantEnabled delegates to settings repository when disabling`() =
        runTest {
            val repository = createRepository()

            // Disabling should always work
            val result = repository.setAssistantEnabled(false)

            assertTrue(result)
            coVerify { settingsRepository.setAllowAssistant(false) }
        }

    @Test
    fun `setAssistantEnabled succeeds when all prerequisites met`() =
        runTest {
            val repository = createRepository()

            // Set up all prerequisites to be met
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableAssistant = true))
            entitlementFlow.value = ProEntitlements
            connectivityFlow.value = ConnectivityStatus.ONLINE

            val result = repository.setAssistantEnabled(true)

            assertTrue(result)
            coVerify { settingsRepository.setAllowAssistant(true) }
        }

    @Test
    fun `setAssistantEnabled succeeds for free users when BuildConfig allows (subscription bypass)`() =
        runTest {
            val repository = createRepository()

            // Previously would fail: free user
            // Now succeeds: BuildConfig.FEATURE_AI_ASSISTANT = true bypasses subscription check
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableAssistant = false))
            entitlementFlow.value = FreeEntitlements
            connectivityFlow.value = ConnectivityStatus.ONLINE

            val result = repository.setAssistantEnabled(true)

            // Succeeds because BuildConfig allows AI assistant for all flavors
            assertTrue(result)
            coVerify { settingsRepository.setAllowAssistant(true) }
        }

    @Test
    fun `setAssistantEnabled fails when infrastructure prerequisites not met`() =
        runTest {
            val repository = createRepository()

            // Prerequisites not met: no connectivity
            remoteConfigFlow.value = RemoteConfig(featureFlags = FeatureFlags(enableAssistant = true))
            entitlementFlow.value = ProEntitlements
            connectivityFlow.value = ConnectivityStatus.OFFLINE

            val result = repository.setAssistantEnabled(true)

            // Fails due to infrastructure (connectivity), not subscription
            assertFalse(result)
            coVerify(exactly = 0) { settingsRepository.setAllowAssistant(true) }
        }

    // ==================== User Preference Flow Tests ====================

    @Test
    fun `user preference flows reflect settings repository state`() =
        runTest {
            val repository = createRepository()

            allowCloudFlow.value = true
            allowAssistantFlow.value = false

            assertTrue(repository.cloudClassificationUserPreference.first())
            assertFalse(repository.assistantUserPreference.first())

            allowCloudFlow.value = false
            allowAssistantFlow.value = true

            assertFalse(repository.cloudClassificationUserPreference.first())
            assertTrue(repository.assistantUserPreference.first())
        }
}
