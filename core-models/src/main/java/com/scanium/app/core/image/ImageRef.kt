package com.scanium.app.core.image

/**
 * Backwards-compatible alias for the shared ImageRef model.
 *
 * The canonical definition lives in com.scanium.app.model.ImageRef. This alias keeps
 * existing imports compiling while we migrate callers to the shared package.
 */
typealias ImageRef = com.scanium.app.model.ImageRef
