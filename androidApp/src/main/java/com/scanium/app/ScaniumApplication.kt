package com.scanium.app

import android.app.Application
import com.scanium.app.data.SettingsRepository
import io.sentry.android.core.SentryAndroid
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ScaniumApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    @Volatile
    private var crashReportingEnabled = false

    override fun onCreate() {
        super.onCreate()

        val settingsRepository = SettingsRepository(this)
        val classificationPreferences = com.scanium.app.data.ClassificationPreferences(this)
        
        // Initialize Sentry
        val sentryDsn = BuildConfig.SENTRY_DSN
        if (sentryDsn.isNotBlank()) {
            SentryAndroid.init(this) { options ->
                options.dsn = sentryDsn
                options.isEnableAutoSessionTracking = true
                
                // Set initial enabled state
                val initialShare = runCatching {
                    runBlocking { settingsRepository.shareDiagnosticsFlow.first() }
                }.getOrDefault(false)
                
                crashReportingEnabled = initialShare
                
                options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
                    if (crashReportingEnabled) event else null
                }
            }
            
            // Set initial tags
            Sentry.setTag("build_type", if (BuildConfig.DEBUG) "debug" else "release")
            Sentry.setTag("cls_session_id", com.scanium.app.logging.CorrelationIds.currentClassificationSessionId())

            // Observe changes to preferences
            applicationScope.launch {
                settingsRepository.shareDiagnosticsFlow.collect { enabled ->
                    crashReportingEnabled = enabled
                }
            }
            
            applicationScope.launch {
                classificationPreferences.mode.collect { mode ->
                    Sentry.setTag("cloud_mode", mode.name)
                }
            }
            
            applicationScope.launch {
                settingsRepository.allowCloudClassificationFlow.collect { allowed ->
                    Sentry.setTag("cloud_allowed", allowed.toString())
                }
            }
        }
    }
}
