package com.scanium.app.items

import android.net.Uri
import com.scanium.shared.core.models.items.ConfidenceLevel as SharedConfidenceLevel
import com.scanium.shared.core.models.items.ItemListingStatus as SharedItemListingStatus
import com.scanium.shared.core.models.items.ScannedItem as SharedScannedItem

typealias ScannedItem = SharedScannedItem<Uri>
typealias ConfidenceLevel = SharedConfidenceLevel
typealias ItemListingStatus = SharedItemListingStatus
