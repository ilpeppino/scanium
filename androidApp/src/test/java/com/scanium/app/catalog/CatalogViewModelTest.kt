package com.scanium.app.catalog

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.common.truth.Truth.assertThat
import com.scanium.app.catalog.model.CatalogModel
import com.scanium.app.catalog.ui.CatalogViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun whenBrandIsNull_thenSuggestionsClearedAndFreeTextDisabled() =
        runTest {
            val repository = mockk<CatalogRepository>()
            coEvery { repository.brands(any()) } returns emptyList()
            val viewModel = CatalogViewModel(repository)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.selectedBrand).isNull()
            assertThat(state.suggestions).isEmpty()
            assertThat(state.allowFreeText).isFalse()
            assertThat(state.isLoading).isFalse()
        }

    @Test
    fun whenQueryShorterThanMinimum_thenNoFetchAndSuggestionsEmpty() =
        runTest {
            val repository = mockk<CatalogRepository>()
            coEvery { repository.brands(any()) } returns emptyList()
            val viewModel = CatalogViewModel(repository)

            viewModel.onBrandSelected("Samsung")
            viewModel.onModelQueryChanged("g")

            advanceTimeBy(300)

            coVerify(exactly = 0) { repository.models(any(), any()) }
            val state = viewModel.uiState.value
            assertThat(state.suggestions).isEmpty()
            assertThat(state.isLoading).isFalse()
        }

    @Test
    fun debounceDelaysModelSearch() =
        runTest {
            val repository = mockk<CatalogRepository>()
            coEvery { repository.brands(any()) } returns emptyList()
            coEvery { repository.models(any(), any()) } returns listOf(model("Galaxy S24"))

            val viewModel = CatalogViewModel(repository)
            viewModel.onBrandSelected("Samsung")
            viewModel.onModelQueryChanged("ga")

            advanceTimeBy(299)
            coVerify(exactly = 0) { repository.models(any(), any()) }

            advanceTimeBy(1)
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.models("electronics_phone", "Samsung") }
        }

    @Test
    fun newKeystrokeCancelsPreviousSearch() =
        runTest {
            val repository = mockk<CatalogRepository>()
            coEvery { repository.brands(any()) } returns emptyList()

            val firstList = listOf(model("Galaxy S20"))
            val secondList = listOf(model("Galaxy S24"))

            // First call delays, second call returns immediately
            coEvery { repository.models(any(), any()) } returns firstList andThen secondList

            val viewModel = CatalogViewModel(repository)
            viewModel.onBrandSelected("Samsung")
            viewModel.onModelQueryChanged("ga")

            advanceTimeBy(300)
            advanceTimeBy(100)

            viewModel.onModelQueryChanged("gal")
            advanceTimeBy(300)
            advanceUntilIdle()

            val suggestions = viewModel.uiState.value.suggestions
            assertThat(suggestions).containsExactlyElementsIn(secondList).inOrder()
        }

    @Test
    fun brandChangeClearsModelSelectionAndQuery() =
        runTest {
            val repository = mockk<CatalogRepository>()
            coEvery { repository.brands(any()) } returns emptyList()
            val viewModel = CatalogViewModel(repository)

            viewModel.onBrandSelected("Samsung")
            viewModel.onModelSuggestionSelected(model("Galaxy S24"))
            viewModel.onBrandSelected("Apple")

            val state = viewModel.uiState.value
            assertThat(state.selectedBrand).isEqualTo("Apple")
            assertThat(state.selectedModel).isNull()
            assertThat(state.modelQuery).isEmpty()
            assertThat(state.suggestions).isEmpty()
        }

    @Test
    fun filteringOrderPrioritizesPrefixThenWordPrefixThenContainsThenAlias() =
        runTest {
            val repository = mockk<CatalogRepository>()
            coEvery { repository.brands(any()) } returns emptyList()
            coEvery { repository.models(any(), any()) } returns
                listOf(
                    model("Galaxy S24"),
                    model("Samsung Galaxy Note"),
                    model("Super Galaxy"),
                    model("Phone X", aliases = listOf("Galactic X")),
                )

            val viewModel = CatalogViewModel(repository)
            viewModel.onBrandSelected("Samsung")
            viewModel.onModelQueryChanged("gal")

            advanceTimeBy(300)
            advanceUntilIdle()

            val labels = viewModel.uiState.value.suggestions.map { it.modelLabel }
            assertThat(labels).containsExactly(
                "Galaxy S24",
                "Samsung Galaxy Note",
                "Super Galaxy",
                "Phone X",
            ).inOrder()
        }

    @Test
    fun limitsSuggestionsToTenAndReportsResultCount() =
        runTest {
            val repository = mockk<CatalogRepository>()
            coEvery { repository.brands(any()) } returns emptyList()
            val models = (1..12).map { index -> model("Galaxy $index") }
            coEvery { repository.models(any(), any()) } returns models

            val viewModel = CatalogViewModel(repository)
            viewModel.onBrandSelected("Samsung")
            viewModel.onModelQueryChanged("ga")

            advanceTimeBy(300)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.suggestions).hasSize(10)
            assertThat(state.resultCount).isEqualTo(12)
        }

    private fun model(
        label: String,
        aliases: List<String>? = null,
    ): CatalogModel =
        CatalogModel(
            modelLabel = label,
            modelQid = null,
            aliases = aliases,
            source = null,
        )
}
