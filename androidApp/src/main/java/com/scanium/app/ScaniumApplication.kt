package com.scanium.app

import android.app.Application
import com.scanium.app.crash.AndroidCrashPortAdapter
import com.scanium.app.data.SettingsRepository
import com.scanium.app.perf.PerformanceMonitor
import com.scanium.app.telemetry.*
import com.scanium.diagnostics.DefaultDiagnosticsPort
import com.scanium.diagnostics.DiagnosticsPort
import com.scanium.telemetry.facade.Telemetry
import com.scanium.telemetry.ports.CrashPort
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Application class for Scanium.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection (ARCH-001).
 */
@HiltAndroidApp
class ScaniumApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var crashReportingEnabled = false

    // Expose CrashPort for use by Telemetry bridge
    lateinit var crashPort: CrashPort
        private set

    // Expose DiagnosticsPort for use by Developer Settings
    lateinit var diagnosticsPort: DiagnosticsPort
        private set

    // Global Telemetry facade instance
    lateinit var telemetry: Telemetry
        private set

    override fun onCreate() {
        super.onCreate()

        val settingsRepository = SettingsRepository(this)
        val classificationPreferences = com.scanium.app.data.ClassificationPreferences(this)

        // Initialize DiagnosticsPort (shared buffer for crash-time diagnostics)
        diagnosticsPort =
            DefaultDiagnosticsPort(
                contextProvider = {
                    mapOf(
                        "platform" to "android",
                        "app_version" to BuildConfig.VERSION_NAME,
                        "build" to BuildConfig.VERSION_CODE.toString(),
                        "env" to if (BuildConfig.DEBUG) "dev" else "prod",
                        "session_id" to com.scanium.app.logging.CorrelationIds.currentClassificationSessionId(),
                    )
                },
                maxEvents = 200,
// Keep last 200 events
                maxBytes = 128 * 1024,
// 128KB max (will be enforced again at attachment time)
            )

        // Initialize Sentry via CrashPort adapter
        val sentryDsn = BuildConfig.SENTRY_DSN
        if (sentryDsn.isNotBlank()) {
            // Initialize Sentry SDK
            SentryAndroid.init(this) { options ->
                options.dsn = sentryDsn
                options.isEnableAutoSessionTracking = true

                // Set release and environment for proper tracking in Sentry UI
                // Format: com.scanium.app@VERSION_NAME+VERSION_CODE
                options.release = "com.scanium.app@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.environment = if (BuildConfig.DEBUG) "dev" else "prod"

                // Set initial enabled state based on user consent
                val initialShare =
                    runCatching {
                        runBlocking { settingsRepository.shareDiagnosticsFlow.first() }
                    }.getOrDefault(false)

                crashReportingEnabled = initialShare

                // Filter events based on user consent
                options.beforeSend =
                    io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
                        if (crashReportingEnabled) event else null
                    }
            }

            // Create CrashPort adapter with diagnostics attachment
            crashPort = AndroidCrashPortAdapter(diagnosticsPort)

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

        // Clear stale dev config overrides on startup (debug only)
        if (BuildConfig.DEBUG) {
            applicationScope.launch {
                val devConfig = com.scanium.app.config.DevConfigOverride(this@ScaniumApplication)
                devConfig.clearStaleOverrides()
            }
        }

        // Initialize OTLP telemetry ports
        initializeTelemetry()
    }

    private fun initializeTelemetry() {
        // Build TelemetryConfig based on build type
        val telemetryConfig =
            if (BuildConfig.DEBUG) {
                com.scanium.telemetry.TelemetryConfig.development()
            } else {
                com.scanium.telemetry.TelemetryConfig.production()
            }.copy(dataRegion = BuildConfig.TELEMETRY_DATA_REGION)

        // Build OTLP configuration from BuildConfig
        val otlpConfig =
            if (BuildConfig.OTLP_ENABLED && BuildConfig.OTLP_ENDPOINT.isNotBlank()) {
                OtlpConfiguration(
                    enabled = true,
                    endpoint = BuildConfig.OTLP_ENDPOINT,
                    environment = if (BuildConfig.DEBUG) "dev" else "prod",
                    serviceVersion = BuildConfig.VERSION_NAME,
                    traceSamplingRate = telemetryConfig.traceSampleRate,
// Use TelemetryConfig value
                    debugLogging = BuildConfig.DEBUG,
                ).also {
                    android.util.Log.i(
                        "ScaniumApplication",
                        "OTLP telemetry enabled: endpoint=${it.endpoint}, env=${it.environment}, " +
                            "minSeverity=${telemetryConfig.minSeverity}, traceSampling=${telemetryConfig.traceSampleRate}, " +
                            "maxQueue=${telemetryConfig.maxQueueSize}, dropPolicy=${telemetryConfig.dropPolicy}",
                    )
                }
            } else {
                OtlpConfiguration.DISABLED.also {
                    android.util.Log.i("ScaniumApplication", "OTLP telemetry disabled")
                }
            }

        // Create OTLP port implementations with both configs
        val logPort = AndroidLogPortOtlp(telemetryConfig, otlpConfig)
        val metricPort = AndroidMetricPortOtlp(telemetryConfig, otlpConfig)
        val tracePort = AndroidTracePortOtlp(telemetryConfig, otlpConfig)

        // Create Telemetry facade with config and diagnostics
        telemetry =
            Telemetry(
                config = telemetryConfig,
                defaultAttributesProvider = AndroidDefaultAttributesProvider(telemetryConfig.dataRegion),
                logPort = logPort,
                metricPort = metricPort,
                tracePort = tracePort,
                crashPort = crashPort,
// Bridge telemetry to crash reporting
                diagnosticsPort = diagnosticsPort,
// Collect breadcrumbs for crash-time attachment
            )

        android.util.Log.i("ScaniumApplication", "Telemetry facade initialized with diagnostics collection")

        // Initialize PerformanceMonitor for global access to performance telemetry
        PerformanceMonitor.init(telemetry)
        android.util.Log.i("ScaniumApplication", "PerformanceMonitor initialized")
    }
}
