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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsFtueViewModelTest {
    private val mockFtueRepository: FtueRepository = mock()

    private lateinit var viewModel: SettingsFtueViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SettingsFtueViewModel(mockFtueRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialize starts FTUE when enabled`() =
        runTest {
            viewModel.initialize(shouldStartFtue = true)

            val currentStep = viewModel.currentStep.first()
            val isActive = viewModel.isActive.first()

            assertThat(currentStep).isEqualTo(SettingsFtueViewModel.SettingsFtueStep.WAITING_LANGUAGE_HINT)
            assertThat(isActive).isTrue()
        }

    @Test
    fun `dismiss marks settings FTUE as completed`() =
        runTest {
            viewModel.dismiss()

            verify(mockFtueRepository).setSettingsFtueCompleted(true)
        }
}
