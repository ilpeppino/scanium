package com.scanium.app.selling.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.data.PostingTargetPreferences
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.ExportProfileDefinition
import com.scanium.app.listing.ExportProfileId
import com.scanium.app.listing.ExportProfileRepository
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftBuilder
import com.scanium.app.listing.PostingAssistPlan
import com.scanium.app.listing.PostingAssistPlanBuilder
import com.scanium.app.listing.PostingStepId
import com.scanium.app.logging.CorrelationIds
import com.scanium.app.logging.ScaniumLog
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.selling.posting.PostingTarget
import com.scanium.app.selling.posting.PostingTargetDefaults
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostingAssistUiState(
    val itemIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val draft: ListingDraft? = null,
    val profiles: List<ExportProfileDefinition> = emptyList(),
    val selectedProfileId: ExportProfileId = ExportProfileId.GENERIC,
    val plan: PostingAssistPlan? = null,
    val targets: List<PostingTarget> = emptyList(),
    val selectedTargetId: String = PostingTargetDefaults.DEFAULT_BROWSER_ID,
    val customTargetValue: String? = null,
    val errorMessage: String? = null,
) {
    val totalCount: Int
        get() = itemIds.size

    val currentItemId: String?
        get() = itemIds.getOrNull(currentIndex)
}

/**
 * ViewModel for the Posting Assist screen that guides users through listing completion.
 *
 * Part of ARCH-001/DX-003: Migrated to Hilt assisted injection to reduce boilerplate.
 * Runtime parameters (itemIds, startIndex, itemsViewModel) are passed via @Assisted annotation,
 * while singleton dependencies are injected by Hilt.
 */
class PostingAssistViewModel
    @AssistedInject
    constructor(
        @Assisted private val itemIds: List<String>,
        @Assisted private val startIndex: Int,
        @Assisted private val itemsViewModel: ItemsViewModel,
        private val draftStore: ListingDraftStore,
        private val exportProfileRepository: ExportProfileRepository,
        private val exportProfilePreferences: ExportProfilePreferences,
        private val postingTargetPreferences: PostingTargetPreferences,
    ) : ViewModel() {
        /**
         * Factory for creating PostingAssistViewModel instances with assisted injection.
         * Part of ARCH-001/DX-003: Replaces verbose manual Factory class.
         */
        @AssistedFactory
        interface Factory {
            fun create(
                itemIds: List<String>,
                startIndex: Int,
                itemsViewModel: ItemsViewModel,
            ): PostingAssistViewModel
        }

        private val draftCache = mutableMapOf<String, ListingDraft>()
        private val _uiState =
            MutableStateFlow(
                PostingAssistUiState(
                    itemIds = itemIds,
                    currentIndex = startIndex.coerceIn(0, (itemIds.size - 1).coerceAtLeast(0)),
                ),
            )
        val uiState: StateFlow<PostingAssistUiState> = _uiState.asStateFlow()

        init {
            loadProfiles()
            loadTargets()
            loadDraftForCurrent()
        }

        fun goToPrevious() {
            val state = _uiState.value
            if (state.currentIndex <= 0) return
            _uiState.update { it.copy(currentIndex = state.currentIndex - 1, errorMessage = null) }
            loadDraftForCurrent()
        }

        fun goToNext() {
            val state = _uiState.value
            if (state.currentIndex >= state.totalCount - 1) return
            _uiState.update { it.copy(currentIndex = state.currentIndex + 1, errorMessage = null) }
            loadDraftForCurrent()
        }

        fun selectProfile(profileId: ExportProfileId) {
            _uiState.update { it.copy(selectedProfileId = profileId) }
            viewModelScope.launch {
                exportProfilePreferences.setLastProfileId(profileId)
            }
            rebuildPlan()
        }

        fun selectTarget(targetId: String) {
            _uiState.update { it.copy(selectedTargetId = targetId) }
            viewModelScope.launch {
                postingTargetPreferences.setLastTargetId(targetId)
            }
        }

        fun updateCustomTarget(url: String) {
            _uiState.update { it.copy(customTargetValue = url, selectedTargetId = PostingTargetDefaults.CUSTOM_TARGET_ID) }
            viewModelScope.launch {
                postingTargetPreferences.setCustomUrl(url)
                postingTargetPreferences.setLastTargetId(PostingTargetDefaults.CUSTOM_TARGET_ID)
            }
            loadTargets()
        }

        fun rebuildPlan() {
            val state = _uiState.value
            val draft = state.draft ?: return
            val profile =
                state.profiles.firstOrNull { it.id == state.selectedProfileId }
                    ?: ExportProfiles.generic()
            val plan = PostingAssistPlanBuilder.build(draft, profile)
            val correlationId = CorrelationIds.newDraftRequestId()
            ScaniumLog.i(
                TAG,
                "Posting assist plan built correlationId=$correlationId itemId=${draft.itemId} " +
                    "profile=${profile.id.value} missing=${plan.missingRequired.size}",
            )
            _uiState.update { it.copy(plan = plan) }
        }

        private fun loadDraftForCurrent() {
            val state = _uiState.value
            val currentId = state.currentItemId ?: return
            draftCache[currentId]?.let { cached ->
                _uiState.update { it.copy(draft = cached, errorMessage = null) }
                rebuildPlan()
                return
            }

            viewModelScope.launch {
                val savedDraft = draftStore.getByItemId(currentId)
                val draft =
                    savedDraft ?: itemsViewModel.items.value
                        .firstOrNull { it.id == currentId }
                        ?.let { ListingDraftBuilder.build(it) }
                draft?.let { draftCache[currentId] = it }
                draft?.let {
                    val correlationId = CorrelationIds.newDraftRequestId()
                    ScaniumLog.i(TAG, "Posting assist loaded draft correlationId=$correlationId itemId=${it.itemId}")
                }
                _uiState.update { it.copy(draft = draft, errorMessage = null) }
                rebuildPlan()
            }
        }

        private fun loadProfiles() {
            viewModelScope.launch {
                val profiles = exportProfileRepository.getProfiles().ifEmpty { listOf(ExportProfiles.generic()) }
                val defaultId = exportProfileRepository.getDefaultProfileId()
                val persistedId = exportProfilePreferences.getLastProfileId(defaultId)
                val selectedId =
                    profiles.firstOrNull { it.id == persistedId }?.id
                        ?: profiles.firstOrNull { it.id == defaultId }?.id
                        ?: ExportProfiles.generic().id
                _uiState.update { it.copy(profiles = profiles, selectedProfileId = selectedId) }
                rebuildPlan()
            }
        }

        private fun loadTargets() {
            viewModelScope.launch {
                val customUrl = postingTargetPreferences.getCustomUrl()
                val presets = PostingTargetDefaults.presets()
                val targets = if (customUrl.isNullOrBlank()) presets else presets + PostingTargetDefaults.custom(customUrl)
                val persistedId = postingTargetPreferences.getLastTargetId(PostingTargetDefaults.DEFAULT_BROWSER_ID)
                val selectedId =
                    targets.firstOrNull { it.id == persistedId }?.id
                        ?: PostingTargetDefaults.DEFAULT_BROWSER_ID
                _uiState.update { it.copy(targets = targets, selectedTargetId = selectedId, customTargetValue = customUrl) }
            }
        }

        fun copyNextStepId(): PostingStepId? = _uiState.value.plan?.let { PostingAssistPlanBuilder.selectNextStep(it).id }

        fun currentTarget(): PostingTarget? {
            val state = _uiState.value
            return state.targets.firstOrNull { it.id == state.selectedTargetId }
                ?: state.targets.firstOrNull { it.id == PostingTargetDefaults.DEFAULT_BROWSER_ID }
        }

        companion object {
            private const val TAG = "PostingAssist"

            /**
             * Creates a ViewModelProvider.Factory for PostingAssistViewModel using Hilt's assisted factory.
             * Part of ARCH-001/DX-003: Simplified factory creation with Hilt.
             *
             * @param assistedFactory The Hilt-generated assisted factory
             * @param itemIds The list of item IDs to assist with
             * @param startIndex The initial index to display
             * @param itemsViewModel The shared ItemsViewModel instance
             */
            fun provideFactory(
                assistedFactory: Factory,
                itemIds: List<String>,
                startIndex: Int,
                itemsViewModel: ItemsViewModel,
            ): ViewModelProvider.Factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        assistedFactory.create(itemIds, startIndex, itemsViewModel) as T
                }
        }
    }
