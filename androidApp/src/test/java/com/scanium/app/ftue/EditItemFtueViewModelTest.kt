package com.scanium.app.ftue

import com.google.common.truth.Truth.assertThat
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditItemFtueViewModelTest {
    @Mock
    private lateinit var mockFtueRepository: FtueRepository

    private lateinit var viewModel: EditItemFtueViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = EditItemFtueViewModel(mockFtueRepository)
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
}
