package com.scanium.telemetry

/**
 * Naming convention constants for telemetry events.
 *
 * All event names must follow the pattern: `<prefix>.<action>`
 * - Use lowercase with underscores for multi-word actions
 * - Keep names concise and descriptive
 * - Use past tense for completed actions (e.g., "completed", "failed")
 * - Use present tense for ongoing states (e.g., "started", "in_progress")
 *
 * ***REMOVED******REMOVED*** Examples
 * - `scan.started` - Scan operation started
 * - `scan.completed` - Scan operation completed successfully
 * - `ml.classification_failed` - ML classification failed
 * - `storage.export_started` - Export to storage started
 * - `error.network_timeout` - Network timeout error occurred
 */
object TelemetryEventNaming {
    /**
     * Prefix for scan-related events.
     * Examples: scan.started, scan.completed, scan.paused, scan.cancelled
     */
    const val PREFIX_SCAN = "scan"

    /**
     * Prefix for ML/AI model-related events.
     * Examples: ml.inference_started, ml.classification_failed, ml.model_loaded
     */
    const val PREFIX_ML = "ml"

    /**
     * Prefix for storage/persistence events.
     * Examples: storage.saved, storage.deleted, storage.sync_failed
     */
    const val PREFIX_STORAGE = "storage"

    /**
     * Prefix for export-related events (CSV, JSON, etc.).
     * Examples: export.csv_started, export.json_completed, export.failed
     */
    const val PREFIX_EXPORT = "export"

    /**
     * Prefix for UI interaction events.
     * Examples: ui.button_clicked, ui.screen_viewed, ui.gesture_detected
     */
    const val PREFIX_UI = "ui"

    /**
     * Prefix for error events.
     * Examples: error.network_timeout, error.parse_failed, error.permission_denied
     */
    const val PREFIX_ERROR = "error"

    /**
     * Common event action suffixes.
     */
    object Actions {
        const val STARTED = "started"
        const val COMPLETED = "completed"
        const val FAILED = "failed"
        const val CANCELLED = "cancelled"
        const val PAUSED = "paused"
        const val RESUMED = "resumed"
        const val TIMEOUT = "timeout"
        const val RETRY = "retry"
    }

    /**
     * Validates that an event name follows the naming convention.
     * @return true if the event name is valid, false otherwise
     */
    fun isValidEventName(name: String): Boolean {
        if (name.isBlank()) return false

        val parts = name.split(".")
        if (parts.size < 2) return false

        val prefix = parts[0]
        val action = parts.drop(1).joinToString(".")

        return prefix.isNotEmpty() &&
               action.isNotEmpty() &&
               name.all { it.isLowerCase() || it.isDigit() || it == '.' || it == '_' }
    }

    /**
     * Extracts the prefix from an event name.
     * @return the prefix (e.g., "scan" from "scan.started"), or null if invalid
     */
    fun extractPrefix(name: String): String? {
        val parts = name.split(".")
        return if (parts.size >= 2) parts[0] else null
    }
}
