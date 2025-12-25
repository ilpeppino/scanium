package com.scanium.app.core.image

/**
 * Backwards-compatible alias for the shared ImageRef model.
 *
 * The canonical definition now lives in shared:core-models at
 * com.scanium.core.models.image.ImageRef. This alias keeps existing imports
 * compiling during the KMP migration.
 */
@Deprecated(
    message = "Use com.scanium.app.model.ImageRef instead.",
    replaceWith = ReplaceWith("ImageRef", "com.scanium.app.model.ImageRef"),
)
typealias ImageRef = com.scanium.core.models.image.ImageRef
