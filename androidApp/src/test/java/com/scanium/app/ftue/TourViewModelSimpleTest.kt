package com.scanium.app.ftue

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import com.google.common.truth.Truth.assertThat
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.model.user.UserEdition
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
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TourViewModelSimpleTest {
    @Mock
    private lateinit var mockFtueRepository: FtueRepository

    @Mock
    private lateinit var mockItemsViewModel: ItemsViewModel

    private lateinit var viewModel: TourViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TourViewModel(mockFtueRepository, mockItemsViewModel)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `currentStepIndex defaults to negative one`() = runTest {
        val currentStepIndex = viewModel.currentStepIndex.first()
        assertThat(currentStepIndex).isEqualTo(-1)
    }

    @Test
    fun `currentStep is null when tour not started`() = runTest {
        val currentStep = viewModel.currentStep.first()
        assertThat(currentStep).isNull()
    }

    @Test
    fun `startTour sets current step index to zero`() = runTest {
        viewModel.startTour()

        val currentStepIndex = viewModel.currentStepIndex.first()
        assertThat(currentStepIndex).isEqualTo(0)
    }

    @Test
    fun `startTour sets current step to welcome`() = runTest {
        viewModel.startTour()

        val currentStep = viewModel.currentStep.first()
        assertThat(currentStep).isNotNull()
        assertThat(currentStep!!.key).isEqualTo(TourStepKey.WELCOME)
    }

    @Test
    fun `nextStep advances to next tour step`() = runTest {
        viewModel.startTour()
        viewModel.nextStep()

        val currentStepIndex = viewModel.currentStepIndex.first()
        assertThat(currentStepIndex).isEqualTo(1)
    }

    @Test
    fun `nextStep completes tour at last step`() = runTest {
        viewModel.startTour()
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.nextStep()

        val currentStepIndex = viewModel.currentStepIndex.first()
        assertThat(currentStepIndex).isEqualTo(-1)

        verify(mockFtueRepository).setCompleted(true)
    }

    @Test
    fun `previousStep goes back one step`() = runTest {
        viewModel.startTour()
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.previousStep()

        val currentStepIndex = viewModel.currentStepIndex.first()
        assertThat(currentStepIndex).isEqualTo(1)
    }

    @Test
    fun `previousStep stays at first step`() = runTest {
        viewModel.startTour()
        viewModel.previousStep()

        val currentStepIndex = viewModel.currentStepIndex.first()
        assertThat(currentStepIndex).isEqualTo(0)
    }

    @Test
    fun `skipTour completes tour and marks as completed`() = runTest {
        viewModel.skipTour()

        val currentStepIndex = viewModel.currentStepIndex.first()
        assertThat(currentStepIndex).isEqualTo(-1)

        verify(mockFtueRepository).setCompleted(true)
    }

    @Test
    fun `completeTour completes tour and marks as completed`() = runTest {
        viewModel.completeTour()

        val currentStepIndex = viewModel.currentStepIndex.first()
        assertThat(currentStepIndex).isEqualTo(-1)

        verify(mockFtueRepository).setCompleted(true)
    }

    @Test
    fun `registerTargetBounds adds bounds to registry`() = runTest {
        val key = "test_key"
        val bounds = Rect(0f, 0f, 100f, 100f)

        viewModel.registerTargetBounds(key, bounds)

        val targetBounds = viewModel.targetBounds.first()
        assertThat(targetBounds).containsKey(key)
        assertThat(targetBounds[key]).isEqualTo(bounds)
    }

    @Test
    fun `registerTargetBounds merges multiple bounds`() = runTest {
        viewModel.registerTargetBounds("key1", Rect(0f, 0f, 100f, 100f))
        viewModel.registerTargetBounds("key2", Rect(50f, 50f, 150f, 150f))

        val targetBounds = viewModel.targetBounds.first()
        assertThat(targetBounds).hasSize(2)
        assertThat(targetBounds["key1"]).isEqualTo(Rect(0f, 0f, 100f, 100f))
        assertThat(targetBounds["key2"]).isEqualTo(Rect(50f, 50f, 150f, 150f))
    }

    @Test
    fun `clearTargetBounds removes specific bound`() = runTest {
        viewModel.registerTargetBounds("key1", Rect(0f, 0f, 100f, 100f))
        viewModel.registerTargetBounds("key2", Rect(50f, 50f, 150f, 150f))

        viewModel.clearTargetBounds("key1")

        val targetBounds = viewModel.targetBounds.first()
        assertThat(targetBounds).hasSize(1)
        assertThat(targetBounds).doesNotContainKey("key1")
        assertThat(targetBounds["key2"]).isNotNull()
    }

    @Test
    fun `clearTargetBounds is no-op when key not found`() = runTest {
        viewModel.registerTargetBounds("key1", Rect(0f, 0f, 100f, 100f))

        viewModel.clearTargetBounds("nonexistent_key")

        val targetBounds = viewModel.targetBounds.first()
        assertThat(targetBounds).hasSize(1)
    }

    @Test
    fun `isTourActive is false when FTUE completed`() = runTest {
        viewModel.skipTour()

        val isTourActive = viewModel.isTourActive.first()
        assertThat(isTourActive).isFalse()
    }

    @Test
    fun `isTourActive is true when force enabled`() = runTest {
        viewModel.skipTour()
        whenever(mockFtueRepository.forceEnabledFlow).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(true))

        val isTourActive = viewModel.isTourActive.first()
        assertThat(isTourActive).isTrue()
    }
}
