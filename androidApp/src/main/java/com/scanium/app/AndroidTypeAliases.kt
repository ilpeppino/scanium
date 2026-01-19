package com.scanium.app

import android.net.Uri

// Type aliases for Android-specific versions of KMP types
// These provide the concrete types needed for Android platform code

typealias ScannedItem = com.scanium.shared.core.models.items.ScannedItem<Uri>
typealias ItemCategory = com.scanium.shared.core.models.ml.ItemCategory
typealias NormalizedRect = com.scanium.shared.core.models.model.NormalizedRect
typealias ObjectTracker = com.scanium.core.tracking.ObjectTracker

// Aggregation types are Android-specific and live in core-tracking module
// They are NOT part of the shared KMP modules yet, so they use the android-specific package
typealias AggregatedItem = com.scanium.app.aggregation.AggregatedItem
typealias ItemAggregator = com.scanium.app.aggregation.ItemAggregator
typealias AggregationConfig = com.scanium.app.aggregation.AggregationConfig
typealias AggregationPresets = com.scanium.app.aggregation.AggregationPresets
typealias AggregationStats = com.scanium.app.aggregation.AggregationStats
