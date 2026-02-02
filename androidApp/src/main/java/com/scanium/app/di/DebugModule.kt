package com.scanium.app.di

import android.content.Context
import com.scanium.app.debug.ImageClassifierDebugger
import com.scanium.app.ml.detector.DetectionMapping
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing debug utilities.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugModule {
    @Provides
    @Singleton
    fun provideImageClassifierDebugger(
        @ApplicationContext context: Context,
    ): ImageClassifierDebugger {
        val debugger = ImageClassifierDebugger(context)
        // Also set it on DetectionMapping for static access
        DetectionMapping.debugger = debugger
        return debugger
    }
}
