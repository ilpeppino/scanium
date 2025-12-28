package com.scanium.app.di

import android.content.Context
import com.scanium.app.BuildConfig
import com.scanium.app.billing.AndroidBillingProvider
import com.scanium.app.billing.BillingRepository
import com.scanium.app.billing.FakeBillingProvider
import com.scanium.app.model.billing.BillingProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * Hilt module providing billing dependencies.
 * Part of ARCH-001: DI Framework Migration.
 *
 * Provides FakeBillingProvider for DEBUG builds and AndroidBillingProvider for RELEASE.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideBillingProvider(
        @ApplicationContext context: Context,
        billingRepository: BillingRepository,
        @ApplicationScope scope: CoroutineScope
    ): BillingProvider {
        return if (BuildConfig.DEBUG) {
            FakeBillingProvider(billingRepository)
        } else {
            AndroidBillingProvider(context, billingRepository, scope)
        }
    }
}
