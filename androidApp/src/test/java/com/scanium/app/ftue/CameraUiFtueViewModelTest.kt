package com.scanium.app.ftue

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CameraUiFtueViewModelTest {
    private val mockFtueRepository: FtueRepository = mock()

    private lateinit var viewModel: CameraUiFtueViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CameraUiFtueViewModel(mockFtueRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize starts FTUE when conditions are met`() = runTest {
        whenever(mockFtueRepository.cameraUiFtueForceShowFlow).thenReturn(MutableStateFlow(true))
        whenever(mockFtueRepository.cameraUiFtueCompletedFlow).thenReturn(MutableStateFlow(false))

        viewModel.initialize(
            cameraPermissionGranted = true,
            previewVisible = true,
            allAnchorsRegistered = true,
        )

        val currentStep = viewModel.currentStep.first()
        val isActive = viewModel.isActive.first()

        assertThat(currentStep).isEqualTo(CameraUiFtueViewModel.CameraUiFtueStep.SHUTTER)
        assertThat(isActive).isTrue()
    }

    @Test
    fun `nextStep completes and persists completion`() = runTest {
        whenever(mockFtueRepository.cameraUiFtueForceShowFlow).thenReturn(MutableStateFlow(true))
        whenever(mockFtueRepository.cameraUiFtueCompletedFlow).thenReturn(MutableStateFlow(false))

        viewModel.initialize(
            cameraPermissionGranted = true,
            previewVisible = true,
            allAnchorsRegistered = true,
        )

        repeat(4) { viewModel.nextStep() }

        val currentStep = viewModel.currentStep.first()
        assertThat(currentStep).isEqualTo(CameraUiFtueViewModel.CameraUiFtueStep.COMPLETED)
        verify(mockFtueRepository).setCameraUiFtueCompleted(true)
    }
}
