package com.scanium.app.di

import android.app.Application
import android.content.Context
import com.scanium.app.ScaniumApplication
import com.scanium.app.config.SecureApiKeyStore
import com.scanium.app.data.AndroidRemoteConfigProvider
import com.scanium.app.model.config.ConfigProvider
import com.scanium.app.platform.ConnectivityObserver
import com.scanium.app.platform.ConnectivityStatusProvider
import com.scanium.diagnostics.DiagnosticsPort
import com.scanium.telemetry.facade.Telemetry
import com.scanium.telemetry.ports.CrashPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the application-scoped CoroutineScope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Hilt module providing core application dependencies.
 * Part of ARCH-001: DI Framework Migration.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @Provides
    @Singleton
    fun provideSecureApiKeyStore(
        @ApplicationContext context: Context
    ): SecureApiKeyStore {
        return SecureApiKeyStore(context)
    }

    @Provides
    @Singleton
    fun provideConnectivityObserver(
        @ApplicationContext context: Context
    ): ConnectivityObserver {
        return ConnectivityObserver(context)
    }

    @Provides
    @Singleton
    fun provideConnectivityStatusProvider(
        connectivityObserver: ConnectivityObserver
    ): ConnectivityStatusProvider {
        return connectivityObserver
    }

    @Provides
    @Singleton
    fun provideConfigProvider(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): ConfigProvider {
        return AndroidRemoteConfigProvider(context, scope)
    }

    /**
     * Provides Telemetry facade from the Application instance.
     * The Telemetry is initialized in ScaniumApplication.onCreate().
     */
    @Provides
    @Singleton
    fun provideTelemetry(
        application: Application
    ): Telemetry? {
        return (application as? ScaniumApplication)?.telemetry
    }

    /**
     * Provides CrashPort from the Application instance.
     */
    @Provides
    @Singleton
    fun provideCrashPort(
        application: Application
    ): CrashPort {
        return (application as? ScaniumApplication)?.crashPort
            ?: com.scanium.telemetry.ports.NoOpCrashPort
    }

    /**
     * Provides DiagnosticsPort from the Application instance.
     */
    @Provides
    @Singleton
    fun provideDiagnosticsPort(
        application: Application
    ): DiagnosticsPort {
        return (application as? ScaniumApplication)?.diagnosticsPort
            ?: object : DiagnosticsPort {
                override fun attach(event: String, data: Map<String, Any>) {}
                override fun breadcrumbCount(): Int = 0
                override fun getAttachmentData(): ByteArray? = null
            }
    }
}
