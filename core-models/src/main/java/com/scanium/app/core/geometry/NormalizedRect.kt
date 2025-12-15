package com.scanium.app.core.geometry

/**
 * Backwards-compatible alias for the shared NormalizedRect model.
 *
 * The canonical definition lives in com.scanium.app.model.NormalizedRect. This alias keeps
 * existing imports compiling while we migrate callers to the shared package.
 */
typealias NormalizedRect = com.scanium.app.model.NormalizedRect

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
