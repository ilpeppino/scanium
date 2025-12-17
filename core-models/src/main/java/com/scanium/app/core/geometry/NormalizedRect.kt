package com.scanium.app.core.geometry

/**
 * Backwards-compatible alias for the shared NormalizedRect model.
 *
 * The canonical definition now lives in shared:core-models at
 * com.scanium.core.models.geometry.NormalizedRect. This alias keeps existing imports
 * compiling during the KMP migration.
 */
typealias NormalizedRect = com.scanium.core.models.geometry.NormalizedRect

/**
 * Compatibility helper mirroring the old API.
 */
fun NormalizedRect.clamp01(): NormalizedRect = clampToUnit()

/**
 * Compatibility helper mirroring the old API.
 */
fun NormalizedRect.width(): Float = width

/**
 * Compatibility helper mirroring the old API.
 */
fun NormalizedRect.height(): Float = height
