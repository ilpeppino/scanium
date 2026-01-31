package com.scanium.app.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.catalog.CatalogRepository
import com.scanium.app.catalog.model.CatalogModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel
    @Inject
    constructor(
        private val repository: CatalogRepository,
        private val subtype: String = DEFAULT_SUBTYPE,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CatalogUiState())
        val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

        private val selectedBrandFlow = MutableStateFlow<String?>(null)
        private val modelQueryFlow = MutableStateFlow("")

        init {
            loadBrands()
            observeModelSearch()
        }

        fun onBrandSelected(brand: String?) {
            selectedBrandFlow.value = brand
            modelQueryFlow.value = ""
            _uiState.update {
                it.copy(
                    selectedBrand = brand,
                    selectedModel = null,
                    modelQuery = "",
                    suggestions = emptyList(),
                    resultCount = 0,
                    isLoading = false,
                    error = null,
                    isOfflineMode = false,
                    allowFreeText = false,
                )
            }
        }

        fun onModelQueryChanged(query: String) {
            modelQueryFlow.value = query
            _uiState.update {
                it.copy(
                    modelQuery = query,
                    selectedModel = null,
                    error = null,
                )
            }
        }

        fun onModelSuggestionSelected(model: CatalogModel) {
            modelQueryFlow.value = model.modelLabel
            _uiState.update {
                it.copy(
                    selectedModel = model,
                    modelQuery = model.modelLabel,
                    suggestions = emptyList(),
                    resultCount = 0,
                    isLoading = false,
                    error = null,
                    isOfflineMode = false,
                    allowFreeText = false,
                )
            }
        }

        fun onClearModel() {
            modelQueryFlow.value = ""
            _uiState.update {
                it.copy(
                    selectedModel = null,
                    modelQuery = "",
                    suggestions = emptyList(),
                    resultCount = 0,
                    isLoading = false,
                    error = null,
                    isOfflineMode = false,
                    allowFreeText = false,
                )
            }
        }

        private fun loadBrands() {
            _uiState.update { it.copy(brandsLoading = true, brandsError = null) }
            viewModelScope.launch {
                runCatching { repository.brands(subtype) }
                    .onSuccess { brands ->
                        _uiState.update { it.copy(brands = brands, brandsLoading = false) }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                brandsLoading = false,
                                brandsError = error.message ?: "Unable to load brands",
                            )
                        }
                    }
            }
        }

        private fun observeModelSearch() {
            viewModelScope.launch {
                combine(
                    selectedBrandFlow,
                    modelQueryFlow.debounce(MODEL_QUERY_DEBOUNCE_MS),
                ) { brand, query ->
                    brand to query.trim()
                }.collectLatest { (brand, query) ->
                    handleModelSearch(brand, query)
                }
            }
        }

        private suspend fun handleModelSearch(
            brand: String?,
            query: String,
        ) {
            val selectedModelLabel = _uiState.value.selectedModel?.modelLabel
            if (!selectedModelLabel.isNullOrBlank() && selectedModelLabel == query) {
                _uiState.update { it.copy(isLoading = false, suggestions = emptyList(), resultCount = 0) }
                return
            }

            if (brand.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        suggestions = emptyList(),
                        resultCount = 0,
                        isLoading = false,
                        error = null,
                        isOfflineMode = false,
                        allowFreeText = false,
                    )
                }
                return
            }

            if (query.length < MIN_QUERY_LENGTH) {
                _uiState.update {
                    it.copy(
                        suggestions = emptyList(),
                        resultCount = 0,
                        isLoading = false,
                        error = null,
                        isOfflineMode = false,
                        allowFreeText = false,
                    )
                }
                return
            }

            _uiState.update { it.copy(isLoading = true, error = null, isOfflineMode = false) }

            runCatching { repository.models(subtype, brand) }
                .onSuccess { models ->
                    val matches = filterModels(models, query)
                    _uiState.update {
                        it.copy(
                            suggestions = matches.take(MAX_SUGGESTIONS),
                            resultCount = matches.size,
                            isLoading = false,
                            error = null,
                            isOfflineMode = false,
                            allowFreeText = models.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            suggestions = emptyList(),
                            resultCount = 0,
                            isLoading = false,
                            error = error.message ?: "Unable to load models",
                            isOfflineMode = isOfflineError(error),
                            allowFreeText = true,
                        )
                    }
                }
        }

        private fun filterModels(
            models: List<CatalogModel>,
            query: String,
        ): List<CatalogModel> {
            if (models.isEmpty()) return emptyList()
            val normalizedQuery = query.lowercase()

            val startsWithMatches = mutableListOf<CatalogModel>()
            val wordStartsWithMatches = mutableListOf<CatalogModel>()
            val containsMatches = mutableListOf<CatalogModel>()
            val aliasMatches = mutableListOf<CatalogModel>()

            for (model in models) {
                val label = model.modelLabel
                val normalizedLabel = label.lowercase()

                when {
                    normalizedLabel.startsWith(normalizedQuery) -> startsWithMatches.add(model)
                    labelWords(label).any { it.startsWith(normalizedQuery) } -> wordStartsWithMatches.add(model)
                    normalizedLabel.contains(normalizedQuery) -> containsMatches.add(model)
                    model.aliases?.any { it.contains(normalizedQuery, ignoreCase = true) } == true ->
                        aliasMatches.add(model)
                }
            }

            return startsWithMatches + wordStartsWithMatches + containsMatches + aliasMatches
        }

        private fun labelWords(label: String): List<String> =
            label
                .lowercase()
                .split(WORD_SPLIT_REGEX)
                .filter { it.isNotBlank() }

        private fun isOfflineError(error: Throwable): Boolean =
            when (error) {
                is UnknownHostException,
                is SocketTimeoutException,
                is ConnectException -> true

                is IOException -> error.message?.startsWith("Catalog ") != true
                else -> false
            }

        companion object {
            private const val DEFAULT_SUBTYPE = "electronics_phone"
            private const val MODEL_QUERY_DEBOUNCE_MS = 300L
            private const val MIN_QUERY_LENGTH = 2
            private const val MAX_SUGGESTIONS = 10
            private val WORD_SPLIT_REGEX = Regex("[^A-Za-z0-9]+")
        }
    }
