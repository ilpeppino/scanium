package com.scanium.app.telemetry

import com.scanium.telemetry.facade.Telemetry

/**
 * Mobile telemetry events for Scanium app (Option 2: OTLP → Alloy → Loki).
 *
 * This object provides convenience methods for emitting mobile app telemetry events
 * using the OTLP Telemetry facade. Events are sent directly to Grafana Alloy on the
 * NAS, which then forwards them to Loki with appropriate labels.
 *
 * ***REMOVED******REMOVED*** Event Naming Convention
 * Events follow the pattern: `<domain>.<action>`
 * - app.*: Application lifecycle events
 * - scan.*: Scanning workflow events
 * - share.*: Export/sharing events
 * - ai.*: AI assistant events
 * - error.*: Error and exception events
 *
 * ***REMOVED******REMOVED*** Dashboard Integration
 * Events are designed to populate the "Scanium - Mobile App Health" dashboard
 * with metrics like:
 * - App launches and session counts
 * - Scan success/failure rates
 * - Feature usage (AI, sharing)
 * - Error rates and types
 *
 * ***REMOVED******REMOVED*** Privacy & PII
 * All events are automatically sanitized by the Telemetry facade to ensure:
 * - No user-generated content (names, prompts, responses)
 * - No device identifiers (IMEI, Android ID)
 * - No location data (GPS, IP)
 * - Session IDs are random UUIDs per app launch
 *
 * ***REMOVED******REMOVED*** Usage Example
 * ```kotlin
 * // In your Activity or ViewModel
 * val app = context.applicationContext as ScaniumApplication
 * MobileEvents.scanStarted(app.telemetry, scanSource = "camera")
 * ```
 */
object MobileEvents {
    /**
     * App started (cold start).
     * Emitted once per app launch.
     */
    fun appStarted(
        telemetry: Telemetry,
        launchType: String = "cold_start",
    ) {
        telemetry.info(
            name = "app.started",
            userAttributes =
                mapOf(
                    "launch_type" to launchType,
                ),
        )
    }

    /**
     * Scan started by user.
     * Emitted when camera opens or scan begins.
     */
    fun scanStarted(
        telemetry: Telemetry,
        scanSource: String = "camera",
    ) {
        telemetry.info(
            name = "scan.started",
            userAttributes =
                mapOf(
                    "scan_source" to scanSource,
                ),
        )
    }

    /**
     * Item created/detected during scan.
     * Emitted for each item added to the current scan.
     */
    fun scanCreatedItem(
        telemetry: Telemetry,
        itemType: String = "unknown",
        hasBarcode: Boolean = false,
        hasNutrition: Boolean = false,
    ) {
        telemetry.info(
            name = "scan.created_item",
            userAttributes =
                mapOf(
                    "item_type" to itemType,
                    "has_barcode" to hasBarcode.toString(),
                    "has_nutrition" to hasNutrition.toString(),
                ),
        )
    }

    /**
     * Scan confirmed/saved by user.
     * Emitted when user completes a scan and saves items.
     */
    fun scanConfirmed(
        telemetry: Telemetry,
        itemCount: Int,
        durationMs: Long? = null,
    ) {
        val attrs =
            mutableMapOf<String, String>(
                "items_detected" to itemCount.toString(),
            )
        if (durationMs != null) {
            attrs["scan_duration_ms"] = durationMs.toString()
        }
        telemetry.info(
            name = "scan.confirmed",
            userAttributes = attrs,
        )
    }

    /**
     * Share/export sheet opened.
     * Emitted when user opens the share options.
     */
    fun shareOpened(
        telemetry: Telemetry,
        context: String = "scan_result",
    ) {
        telemetry.info(
            name = "share.opened",
            userAttributes =
                mapOf(
                    "context" to context,
                ),
        )
    }

    /**
     * Export ZIP created and shared.
     * Emitted when user exports items as ZIP.
     */
    fun shareExportZip(
        telemetry: Telemetry,
        itemCount: Int,
        includeImages: Boolean = true,
    ) {
        telemetry.info(
            name = "share.export_zip",
            userAttributes =
                mapOf(
                    "item_count" to itemCount.toString(),
                    "include_images" to includeImages.toString(),
                ),
        )
    }

    /**
     * AI Generate button clicked.
     * Emitted when user triggers AI assistant.
     */
    fun aiGenerateClicked(
        telemetry: Telemetry,
        context: String = "scan_result",
        aiEnabled: Boolean = true,
        sendPictures: Boolean = false,
    ) {
        telemetry.info(
            name = "ai.generate_clicked",
            userAttributes =
                mapOf(
                    "context" to context,
                    "ai_enabled" to aiEnabled.toString(),
                    "send_pictures_to_ai" to sendPictures.toString(),
                ),
        )
    }

    /**
     * Error or exception occurred.
     * Emitted for both caught and uncaught exceptions.
     */
    fun errorException(
        telemetry: Telemetry,
        errorCode: String,
        errorCategory: String = "unknown",
        isRecoverable: Boolean = true,
        throwable: Throwable? = null,
    ) {
        val attrs =
            mutableMapOf(
                "error_code" to errorCode,
                "error_category" to errorCategory,
                "is_recoverable" to isRecoverable.toString(),
            )
        if (throwable != null) {
            attrs["exception_type"] = throwable::class.simpleName ?: "Unknown"
            // DO NOT include throwable.message (may contain PII)
        }

        telemetry.error(
            name = "error.exception",
            userAttributes = attrs,
        )
    }

    /**
     * Scan cancelled by user.
     * Emitted when user backs out of scan without saving.
     */
    fun scanCancelled(
        telemetry: Telemetry,
        reason: String = "user_cancelled",
    ) {
        telemetry.info(
            name = "scan.cancelled",
            userAttributes =
                mapOf(
                    "reason" to reason,
                ),
        )
    }

    /**
     * Classification completed (on-device or cloud).
     * Emitted when an item is classified successfully.
     */
    fun classificationCompleted(
        telemetry: Telemetry,
        mode: String = "on_device",
        durationMs: Long,
        success: Boolean = true,
    ) {
        telemetry.info(
            name = "ml.classification_completed",
            userAttributes =
                mapOf(
                    "classification_mode" to mode,
                    "duration_ms" to durationMs.toString(),
                    "success" to success.toString(),
                ),
        )
    }
}
