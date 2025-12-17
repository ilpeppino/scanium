package com.scanium.app.tracking

/**
 * Backwards-compatible aliases for the shared tracking classes.
 *
 * The canonical definitions now live in shared:core-tracking at
 * com.scanium.core.tracking.*. These aliases keep existing imports
 * compiling during the KMP migration.
 */
typealias ObjectTracker = com.scanium.core.tracking.ObjectTracker
typealias DetectionInfo = com.scanium.core.tracking.DetectionInfo
typealias TrackerConfig = com.scanium.core.tracking.TrackerConfig
typealias TrackerStats = com.scanium.core.tracking.TrackerStats
