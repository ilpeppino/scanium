package com.scanium.app.model

/**
 * Backwards-compatible alias for the shared NormalizedRect model.
 *
 * The canonical definition now lives in shared:core-models at
 * com.scanium.core.models.geometry.NormalizedRect. This alias keeps existing imports
 * compiling during the KMP migration.
 */
typealias NormalizedRect = com.scanium.core.models.geometry.NormalizedRect
