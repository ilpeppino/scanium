package com.scanium.app.catalog.ui

import com.scanium.app.catalog.model.CatalogModel

data class CatalogUiState(
    val selectedBrand: String? = null,
    val selectedModel: CatalogModel? = null,
    val modelQuery: String = "",
    val suggestions: List<CatalogModel> = emptyList(),
    val resultCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOfflineMode: Boolean = false,
    val allowFreeText: Boolean = false,
    val brands: List<String> = emptyList(),
    val brandsLoading: Boolean = false,
    val brandsError: String? = null,
)
