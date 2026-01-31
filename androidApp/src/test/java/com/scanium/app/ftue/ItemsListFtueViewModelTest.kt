package com.scanium.app.ftue

import com.google.common.truth.Truth.assertThat
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
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ItemsListFtueViewModelTest {
    private val mockFtueRepository: FtueRepository = mock()

    private lateinit var viewModel: ItemsListFtueViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ItemsListFtueViewModel(mockFtueRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize starts FTUE when enabled and list has items`() = runTest {
        viewModel.initialize(shouldStartFtue = true, itemCount = 1)

        val currentStep = viewModel.currentStep.first()
        val isActive = viewModel.isActive.first()

        assertThat(currentStep).isEqualTo(ItemsListFtueViewModel.ItemsListFtueStep.WAITING_TAP_HINT)
        assertThat(isActive).isTrue()
    }

    @Test
    fun `dismiss marks items list FTUE as completed`() = runTest {
        viewModel.dismiss()

        verify(mockFtueRepository).setListFtueCompleted(true)
    }
}
