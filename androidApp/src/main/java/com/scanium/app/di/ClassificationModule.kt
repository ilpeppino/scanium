package com.scanium.app.di

import android.content.Context
import com.scanium.app.classification.persistence.ClassificationCorrectionDao
import com.scanium.app.data.ClassificationPreferences
import com.scanium.app.items.persistence.ScannedItemDatabase
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationThumbnailProvider
import com.scanium.app.ml.classification.CloudClassifier
import com.scanium.app.ml.classification.ItemClassifier
import com.scanium.app.ml.classification.OnDeviceClassifier
import com.scanium.app.ml.classification.StableItemCropper
import com.scanium.app.model.config.FeatureFlagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module providing classification dependencies.
 * Part of ARCH-001: DI Framework Migration.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClassificationModule {
    @Provides
    @Singleton
    @Named("onDevice")
    fun provideOnDeviceClassifier(): ItemClassifier = OnDeviceClassifier()

    @Provides
    @Singleton
    @Named("cloud")
    fun provideCloudClassifier(
        @ApplicationContext context: Context,
        correctionDao: ClassificationCorrectionDao,
    ): ItemClassifier = CloudClassifier(context = context, correctionDao = correctionDao)

    @Provides
    @Singleton
    fun provideStableItemCropper(
        @ApplicationContext context: Context,
    ): StableItemCropper = StableItemCropper(context)

    @Provides
    @Singleton
    fun provideClassificationThumbnailProvider(stableItemCropper: StableItemCropper): ClassificationThumbnailProvider = stableItemCropper

    @Provides
    @Singleton
    fun provideClassificationModeFlow(
        classificationPreferences: ClassificationPreferences,
        @ApplicationScope scope: CoroutineScope,
    ): StateFlow<ClassificationMode> =
        classificationPreferences.mode
            .stateIn(scope, SharingStarted.Eagerly, ClassificationMode.CLOUD)

    @Provides
    @Singleton
    @Named("cloudClassificationEnabled")
    fun provideCloudClassificationEnabledFlow(
        featureFlagRepository: FeatureFlagRepository,
        @ApplicationScope scope: CoroutineScope,
    ): StateFlow<Boolean> =
        featureFlagRepository.isCloudClassificationEnabled
            .stateIn(scope, SharingStarted.Eagerly, true)
}
