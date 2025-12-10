package com.scanium.app.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.scanium.app.data.ItemsRepository

/**
 * Factory for creating ItemsViewModel with constructor dependencies.
 *
 * This is required because ItemsViewModel now takes a repository parameter,
 * and the default ViewModelProvider cannot instantiate it without this factory.
 *
 * @param repository The repository to inject into the ViewModel
 */
class ItemsViewModelFactory(
    private val repository: ItemsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ItemsViewModel::class.java)) {
            return ItemsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
