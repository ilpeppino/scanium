package com.scanium.app.core.geometry

/**
 * Backwards-compatible alias for the shared NormalizedRect model.
 *
 * The canonical definition now lives in shared:core-models at
 * com.scanium.core.models.geometry.NormalizedRect. This alias keeps existing imports
 * compiling during the KMP migration.
 */
@Deprecated(
    message = "Use com.scanium.app.model.NormalizedRect instead.",
    replaceWith = ReplaceWith("NormalizedRect", "com.scanium.app.model.NormalizedRect"),
)
typealias NormalizedRect = com.scanium.core.models.geometry.NormalizedRect

/**
 * Compatibility helper mirroring the old API.
 */
@Deprecated(
    message = "Use clampToUnit() instead.",
    replaceWith = ReplaceWith("clampToUnit()"),
)
fun NormalizedRect.clamp01(): NormalizedRect = clampToUnit()

/**
 * Compatibility helper mirroring the old API.
 */
@Deprecated(
    message = "Use the width property instead.",
    replaceWith = ReplaceWith("width"),
)
fun NormalizedRect.width(): Float = width

/**
 * Compatibility helper mirroring the old API.
 */
@Deprecated(
    message = "Use the height property instead.",
    replaceWith = ReplaceWith("height"),
)
fun NormalizedRect.height(): Float = height
