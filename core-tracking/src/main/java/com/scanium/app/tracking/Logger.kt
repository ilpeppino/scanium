package com.scanium.app.tracking

/**
 * Backwards-compatible alias for the shared Logger interface.
 *
 * The canonical definition now lives in shared:core-tracking at
 * com.scanium.core.tracking.Logger. This alias keeps existing imports
 * compiling during the KMP migration.
 */
typealias Logger = com.scanium.core.tracking.Logger
