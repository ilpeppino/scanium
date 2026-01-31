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
class CameraFtueViewModelTest {
    private val mockFtueRepository: FtueRepository = mock()

    private lateinit var viewModel: CameraFtueViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CameraFtueViewModel(mockFtueRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize marks FTUE as completed and inactive`() = runTest {
        viewModel.initialize(shouldStartFtue = true, hasExistingItems = false)

        val currentStep = viewModel.currentStep.first()
        val isActive = viewModel.isActive.first()

        assertThat(currentStep).isEqualTo(CameraFtueViewModel.CameraFtueStep.COMPLETED)
        assertThat(isActive).isFalse()
    }

    @Test
    fun `dismiss marks camera FTUE as completed`() = runTest {
        viewModel.dismiss()

        verify(mockFtueRepository).setCameraFtueCompleted(true)
    }
}
