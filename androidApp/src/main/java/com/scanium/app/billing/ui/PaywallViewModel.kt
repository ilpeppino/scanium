package com.scanium.app.billing.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.billing.BillingSkus
import com.scanium.app.model.billing.BillingProvider
import com.scanium.app.model.billing.ProductDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the paywall/subscription screen.
 * Part of ARCH-001: Migrated to Hilt dependency injection.
 */
@HiltViewModel
class PaywallViewModel
    @Inject
    constructor(
        private val billingProvider: BillingProvider,
    ) : ViewModel() {
        private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
        val products: StateFlow<List<ProductDetails>> = _products

        private val _isLoading = MutableStateFlow(true)
        val isLoading: StateFlow<Boolean> = _isLoading

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        init {
            loadProducts()
        }

        private fun loadProducts() {
            viewModelScope.launch {
                _isLoading.value = true
                val skus = BillingSkus.SUBSCRIPTIONS + BillingSkus.INAPP
                val details = billingProvider.getProductDetails(skus)
                _products.value = details
                _isLoading.value = false
            }
        }

        fun purchase(
            activity: Activity,
            productId: String,
        ) {
            viewModelScope.launch {
                val result = billingProvider.purchase(productId, activity)
                result.onFailure { e ->
                    _error.value = "Purchase failed: ${e.message}"
                }
            }
        }

        fun restorePurchases() {
            viewModelScope.launch {
                _isLoading.value = true
                billingProvider.restorePurchases()
                _isLoading.value = false
            }
        }

        fun clearError() {
            _error.value = null
        }
    }
