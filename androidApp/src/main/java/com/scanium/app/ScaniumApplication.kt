package com.scanium.app

import android.app.Application
import com.scanium.app.crash.AndroidCrashPortAdapter
import com.scanium.app.data.SettingsRepository
import com.scanium.telemetry.ports.CrashPort
import io.sentry.android.core.SentryAndroid
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

    // Expose CrashPort for use by Telemetry bridge
    lateinit var crashPort: CrashPort
        private set

    override fun onCreate() {
        super.onCreate()

        val settingsRepository = SettingsRepository(this)
        val classificationPreferences = com.scanium.app.data.ClassificationPreferences(this)

        // Initialize Sentry via CrashPort adapter
        val sentryDsn = BuildConfig.SENTRY_DSN
        if (sentryDsn.isNotBlank()) {
            // Initialize Sentry SDK
            SentryAndroid.init(this) { options ->
                options.dsn = sentryDsn
                options.isEnableAutoSessionTracking = true

                // Set initial enabled state based on user consent
                val initialShare = runCatching {
                    runBlocking { settingsRepository.shareDiagnosticsFlow.first() }
                }.getOrDefault(false)

                crashReportingEnabled = initialShare

                // Filter events based on user consent
                options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
                    if (crashReportingEnabled) event else null
                }
            }

            // Create CrashPort adapter
            crashPort = AndroidCrashPortAdapter()

            // Set required tags
            crashPort.setTag("platform", "android")
            crashPort.setTag("app_version", BuildConfig.VERSION_NAME)
            crashPort.setTag("build", BuildConfig.VERSION_CODE.toString())
            crashPort.setTag("env", if (BuildConfig.DEBUG) "dev" else "prod")

            // Set initial session and classification tags
            crashPort.setTag("build_type", if (BuildConfig.DEBUG) "debug" else "release")
            crashPort.setTag("session_id", com.scanium.app.logging.CorrelationIds.currentClassificationSessionId())

            // Placeholder for future domain pack version
            crashPort.setTag("domain_pack_version", "unknown")

            // Observe changes to user preferences and update tags dynamically
            applicationScope.launch {
                settingsRepository.shareDiagnosticsFlow.collect { enabled ->
                    crashReportingEnabled = enabled
                }
            }

            applicationScope.launch {
                classificationPreferences.mode.collect { mode ->
                    // Use "scan_mode" tag for classification mode
                    crashPort.setTag("scan_mode", mode.name)

                    // Keep cloud_mode for backward compatibility
                    crashPort.setTag("cloud_mode", mode.name)
                }
            }

            applicationScope.launch {
                settingsRepository.allowCloudClassificationFlow.collect { allowed ->
                    crashPort.setTag("cloud_allowed", allowed.toString())
                }
            }
        } else {
            // Use NoOp adapter when Sentry is not configured
            crashPort = com.scanium.telemetry.ports.NoOpCrashPort
        }
    }
}
