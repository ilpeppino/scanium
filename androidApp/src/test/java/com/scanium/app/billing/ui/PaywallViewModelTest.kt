package com.scanium.app.billing.ui

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import com.scanium.app.billing.BillingSkus
import com.scanium.app.model.billing.BillingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class PaywallViewModelTest {
    @Mock
    private lateinit var mockBillingProvider: BillingProvider

    @Mock
    private lateinit var mockActivity: Activity

    private lateinit var viewModel: PaywallViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `purchase calls billing provider with product ID`() =
        runTest {
            whenever(mockBillingProvider.purchase(any(), any())).thenReturn(kotlin.Result.success(Unit))

            viewModel = PaywallViewModel(mockBillingProvider)
            viewModel.purchase(mockActivity, BillingSkus.PRO_SUBSCRIPTION_MONTHLY)

            verify(mockBillingProvider).purchase(BillingSkus.PRO_SUBSCRIPTION_MONTHLY, mockActivity)
        }

    @Test
    fun `purchase with failure sets error state`() =
        runTest {
            val exception = Exception("Purchase failed")
            whenever(mockBillingProvider.purchase(any(), any())).thenReturn(kotlin.Result.failure(exception))

            viewModel = PaywallViewModel(mockBillingProvider)
            viewModel.purchase(mockActivity, BillingSkus.PRO_SUBSCRIPTION_MONTHLY)

            val error = viewModel.error.first()
            assertThat(error).isEqualTo("Purchase failed: Purchase failed")
        }

    @Test
    fun `clearError clears error state`() =
        runTest {
            val exception = Exception("Purchase failed")
            whenever(mockBillingProvider.purchase(any(), any())).thenReturn(kotlin.Result.failure(exception))

            viewModel = PaywallViewModel(mockBillingProvider)
            viewModel.purchase(mockActivity, BillingSkus.PRO_SUBSCRIPTION_MONTHLY)
            assertThat(viewModel.error.first()).isNotNull()

            viewModel.clearError()
            assertThat(viewModel.error.first()).isNull()
        }

    @Test
    fun `restorePurchases calls billing provider`() =
        runTest {
            whenever(mockBillingProvider.restorePurchases()).thenReturn(kotlin.Result.success(Unit))

            viewModel = PaywallViewModel(mockBillingProvider)
            viewModel.restorePurchases()

            verify(mockBillingProvider).restorePurchases()
        }

    @Test
    fun `restorePurchases sets loading to true initially`() =
        runTest {
            viewModel = PaywallViewModel(mockBillingProvider)

            val isLoadingBefore = viewModel.isLoading.first()
            assertThat(isLoadingBefore).isFalse()

            whenever(mockBillingProvider.restorePurchases()).thenReturn(kotlin.Result.success(Unit))
            viewModel.restorePurchases()

            val isLoadingAfter = viewModel.isLoading.first()
            assertThat(isLoadingAfter).isFalse()
        }

    @Test
    fun `restorePurchases sets loading to false after completion`() =
        runTest {
            whenever(mockBillingProvider.restorePurchases()).thenReturn(kotlin.Result.success(Unit))

            viewModel = PaywallViewModel(mockBillingProvider)
            viewModel.restorePurchases()

            val isLoading = viewModel.isLoading.first()
            assertThat(isLoading).isFalse()
        }
}
