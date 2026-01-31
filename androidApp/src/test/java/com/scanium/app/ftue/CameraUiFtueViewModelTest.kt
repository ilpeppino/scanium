package com.scanium.app.ftue

import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import com.scanium.app.model.user.UserEdition
import dagger.hilt.android.lifecycle.HiltViewModelTest
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModelTest
@RunWith(RobolectricTestRunner::class)
class CameraUiFtueViewModelTest {
    @Mock
    private lateinit var mockFtueRepository: FtueRepository

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
    fun `isCompleted reflects repository completion state`() = runTest {
        whenever(mockFtueRepository.completedFlow).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(false))

        val isCompleted = viewModel.isCompleted.first()
        assertThat(isCompleted).isFalse()
    }

    @Test
    fun `onComplete marks FTUE as completed`() = runTest {
        viewModel.onComplete()

        verify(mockFtueRepository).setCompleted(true)
    }

    @Test
    fun `registerAnchor adds anchor bounds`() = runTest {
        val anchorKey = "test_anchor"
        val bounds = Rect(10f, 20f, 100f, 200f)

        viewModel.registerAnchor(anchorKey, bounds)

        val anchors = viewModel.anchors.first()
        assertThat(anchors).containsKey(anchorKey)
        assertThat(anchors[anchorKey]).isEqualTo(bounds)
    }

    @Test
    fun `registerAnchor merges multiple anchors`() = runTest {
        viewModel.registerAnchor("anchor1", Rect(10f, 20f, 100f, 200f))
        viewModel.registerAnchor("anchor2", Rect(50f, 60f, 150f, 160f))

        val anchors = viewModel.anchors.first()
        assertThat(anchors).hasSize(2)
    }

    @Test
    fun `clearAnchor removes specific anchor`() = runTest {
        viewModel.registerAnchor("anchor1", Rect(10f, 20f, 100f, 200f))
        viewModel.registerAnchor("anchor2", Rect(50f, 60f, 150f, 160f))

        viewModel.clearAnchor("anchor1")

        val anchors = viewModel.anchors.first()
        assertThat(anchors).hasSize(1)
        assertThat(anchors).doesNotContainKey("anchor1")
        assertThat(anchors["anchor2"]).isNotNull()
    }
}
