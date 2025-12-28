package com.scanium.app.di

import com.scanium.app.items.persistence.NoopScannedItemStore
import com.scanium.app.items.persistence.ScannedItemStore
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.ml.classification.NoopClassificationThumbnailProvider
import com.scanium.app.ml.classification.NoopClassifier
import com.scanium.app.model.billing.BillingProvider
import com.scanium.app.model.billing.EntitlementState
import com.scanium.app.model.billing.ProductDetails
import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.model.config.RemoteConfig
import com.scanium.app.platform.ConnectivityStatus
import com.scanium.app.platform.ConnectivityStatusProvider
import com.scanium.diagnostics.DiagnosticsPort
import com.scanium.telemetry.facade.Telemetry
import com.scanium.telemetry.ports.CrashPort
import com.scanium.telemetry.ports.NoOpCrashPort
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Named
import javax.inject.Singleton

/**
 * Test module that replaces production modules with fakes.
 * Part of ARCH-001: Hilt DI migration.
 *
 * Usage in tests:
 * ```
 * @HiltAndroidTest
 * class MyViewModelTest {
 *     @get:Rule
 *     val hiltRule = HiltAndroidRule(this)
 *
 *     @Inject
 *     lateinit var viewModel: MyViewModel
 *
 *     @Before
 *     fun setup() {
 *         hiltRule.inject()
 *     }
 * }
 * ```
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class, RepositoryModule::class, ClassificationModule::class, BillingModule::class, DatabaseModule::class]
)
object FakeAppModule {

    @Provides
    @Singleton
    fun provideTelemetry(): Telemetry? = null

    @Provides
    @Singleton
    fun provideCrashPort(): CrashPort = NoOpCrashPort

    @Provides
    @Singleton
    fun provideDiagnosticsPort(): DiagnosticsPort = object : DiagnosticsPort {
        override fun attach(event: String, data: Map<String, Any>) {}
        override fun breadcrumbCount(): Int = 0
        override fun getAttachmentData(): ByteArray? = null
    }

    @Provides
    @Singleton
    fun provideScannedItemStore(): ScannedItemStore = NoopScannedItemStore

    @Provides
    @Singleton
    fun provideClassificationThumbnailProvider(): ClassificationThumbnailProvider = NoopClassificationThumbnailProvider

    @Provides
    @Singleton
    @Named("onDevice")
    fun provideOnDeviceClassifier(): ItemClassifier = NoopClassifier

    @Provides
    @Singleton
    @Named("cloud")
    fun provideCloudClassifier(): ItemClassifier = NoopClassifier

    @Provides
    @Singleton
    fun provideClassificationModeFlow(): StateFlow<ClassificationMode> =
        MutableStateFlow(ClassificationMode.ON_DEVICE)

    @Provides
    @Singleton
    @Named("cloudClassificationEnabled")
    fun provideCloudClassificationEnabledFlow(): StateFlow<Boolean> =
        MutableStateFlow(false)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Provides
    @Singleton
    fun provideBillingProvider(): BillingProvider = object : BillingProvider {
        override val entitlementState: Flow<EntitlementState> = flowOf(EntitlementState.DEFAULT)
        override suspend fun refreshEntitlements() {}
        override suspend fun getProductDetails(productIds: List<String>): List<ProductDetails> = emptyList()
        override suspend fun purchase(productId: String, activityContext: Any?): Result<Unit> = Result.success(Unit)
        override suspend fun restorePurchases(): Result<Unit> = Result.success(Unit)
    }

    @Provides
    @Singleton
    fun provideConfigProvider(): ConfigProvider = object : ConfigProvider {
        override val config: Flow<RemoteConfig> = flowOf(RemoteConfig())
        override suspend fun refresh(force: Boolean) {}
    }

    @Provides
    @Singleton
    fun provideConnectivityStatusProvider(): ConnectivityStatusProvider = object : ConnectivityStatusProvider {
        override val statusFlow: Flow<ConnectivityStatus> = flowOf(ConnectivityStatus.ONLINE)
    }
}
