package com.scanium.app.tracking

/**
 * Backwards-compatible alias for the shared ObjectCandidate model.
 *
 * The canonical definition now lives in shared:core-tracking at
 * com.scanium.core.tracking.ObjectCandidate. This alias keeps existing imports
 * compiling during the KMP migration.
 */
typealias ObjectCandidate = com.scanium.core.tracking.ObjectCandidate

/**
 * Backwards-compatible alias for FloatRect.
 */
typealias FloatRect = com.scanium.core.tracking.FloatRect
