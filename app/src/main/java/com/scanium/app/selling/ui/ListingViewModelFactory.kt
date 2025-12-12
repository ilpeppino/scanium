package com.scanium.app.selling.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.data.EbayMarketplaceService

class ListingViewModelFactory(
    private val selectedItems: List<ScannedItem>,
    private val marketplaceService: EbayMarketplaceService,
    private val itemsViewModel: ItemsViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ListingViewModel(selectedItems, marketplaceService, itemsViewModel) as T
    }
}
