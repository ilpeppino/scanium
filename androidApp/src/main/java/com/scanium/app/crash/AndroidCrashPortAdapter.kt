package com.scanium.app.crash

import com.scanium.telemetry.ports.CrashPort
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Android implementation of [CrashPort] using Sentry SDK.
 *
 * This adapter bridges the vendor-neutral CrashPort interface to the Sentry Android SDK.
 * It translates CrashPort operations (tags, breadcrumbs, exceptions) into Sentry-specific calls.
 *
 * ## Initialization
 * ```kotlin
 * // In Application.onCreate():
 * SentryAndroid.init(context) { options ->
 *     options.dsn = BuildConfig.SENTRY_DSN
 *     options.isEnableAutoSessionTracking = true
 *     options.beforeSend = ...  // User consent filtering
 * }
 *
 * val crashPort: CrashPort = AndroidCrashPortAdapter()
 *
 * // Set required tags
 * crashPort.setTag("platform", "android")
 * crashPort.setTag("app_version", BuildConfig.VERSION_NAME)
 * crashPort.setTag("build", BuildConfig.VERSION_CODE.toString())
 * crashPort.setTag("env", if (BuildConfig.DEBUG) "dev" else "prod")
 * ```
 *
 * ## Thread Safety
 * Sentry SDK is thread-safe, so this adapter is also thread-safe.
 *
 * ## User Consent
 * User consent for crash reporting should be handled in the Sentry `beforeSend` callback
 * during initialization, not in this adapter.
 */
class AndroidCrashPortAdapter : CrashPort {

    /**
     * Sets a Sentry tag.
     *
     * Tags are searchable key-value pairs attached to every event sent to Sentry.
     * They're useful for filtering and grouping issues in the Sentry UI.
     *
     * @param key The tag key (e.g., "platform", "app_version", "session_id")
     * @param value The tag value (e.g., "android", "1.0.0", "abc-123")
     */
    override fun setTag(key: String, value: String) {
        Sentry.setTag(key, value)
    }

    /**
     * Adds a Sentry breadcrumb.
     *
     * Breadcrumbs are automatically timestamped and stored in a ring buffer.
     * They appear in the Sentry UI when viewing crash reports, showing the
     * sequence of events leading up to an error.
     *
     * Sentry default breadcrumb limit: 100 (configurable in SentryOptions.maxBreadcrumbs)
     *
     * @param message Breadcrumb message (e.g., "User started scan", "ML classification completed")
     * @param attributes Additional context as key-value pairs
     */
    override fun addBreadcrumb(message: String, attributes: Map<String, String>) {
        val breadcrumb = Breadcrumb().apply {
            this.message = message
            this.level = SentryLevel.INFO
            this.category = "app"  // Category helps group breadcrumbs in Sentry UI

            // Add all attributes as breadcrumb data
            attributes.forEach { (key, value) ->
                this.setData(key, value)
            }
        }

        Sentry.addBreadcrumb(breadcrumb)
    }

    /**
     * Captures an exception and sends it to Sentry.
     *
     * This is used for handled exceptions that you want to track but don't crash the app.
     * Sentry will group similar exceptions and track their frequency.
     *
     * The exception will include:
     * - Stack trace
     * - All tags set via setTag()
     * - All recent breadcrumbs
     * - Custom attributes passed to this method
     *
     * @param throwable The exception to report
     * @param attributes Additional context as key-value pairs (added as Sentry "extras")
     */
    override fun captureException(throwable: Throwable, attributes: Map<String, String>) {
        Sentry.captureException(throwable) { scope ->
            // Add attributes as "extras" (additional context data in Sentry)
            attributes.forEach { (key, value) ->
                scope.setExtra(key, value)
            }
        }
    }
}
