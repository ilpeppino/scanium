package com.scanium.app.model

import com.scanium.shared.core.models.assistant.AssistantAction as SharedAssistantAction
import com.scanium.shared.core.models.assistant.AssistantActionType as SharedAssistantActionType
import com.scanium.shared.core.models.assistant.AssistantMessage as SharedAssistantMessage
import com.scanium.shared.core.models.assistant.AssistantPrefs as SharedAssistantPrefs
import com.scanium.shared.core.models.assistant.AssistantPromptBuilder as SharedAssistantPromptBuilder
import com.scanium.shared.core.models.assistant.AssistantPromptRequest as SharedAssistantPromptRequest
import com.scanium.shared.core.models.assistant.AssistantRegion as SharedAssistantRegion
import com.scanium.shared.core.models.assistant.AssistantResponse as SharedAssistantResponse
import com.scanium.shared.core.models.assistant.AssistantRole as SharedAssistantRole
import com.scanium.shared.core.models.assistant.AssistantTone as SharedAssistantTone
import com.scanium.shared.core.models.assistant.AssistantUnits as SharedAssistantUnits
import com.scanium.shared.core.models.assistant.AssistantVerbosity as SharedAssistantVerbosity
import com.scanium.shared.core.models.assistant.ConfidenceTier as SharedConfidenceTier
import com.scanium.shared.core.models.assistant.EvidenceBullet as SharedEvidenceBullet
import com.scanium.shared.core.models.assistant.ExportProfileSnapshot as SharedExportProfileSnapshot
import com.scanium.shared.core.models.assistant.ItemAttributeSnapshot as SharedItemAttributeSnapshot
import com.scanium.shared.core.models.assistant.ItemContextSnapshot as SharedItemContextSnapshot
import com.scanium.shared.core.models.assistant.ItemContextSnapshotBuilder as SharedItemContextSnapshotBuilder
import com.scanium.shared.core.models.assistant.SuggestedAttribute as SharedSuggestedAttribute
import com.scanium.shared.core.models.assistant.SuggestedDraftUpdate as SharedSuggestedDraftUpdate

typealias AssistantRole = SharedAssistantRole
typealias AssistantMessage = SharedAssistantMessage
typealias AssistantActionType = SharedAssistantActionType
typealias AssistantAction = SharedAssistantAction
typealias AssistantResponse = SharedAssistantResponse
typealias ConfidenceTier = SharedConfidenceTier
typealias EvidenceBullet = SharedEvidenceBullet
typealias SuggestedAttribute = SharedSuggestedAttribute
typealias SuggestedDraftUpdate = SharedSuggestedDraftUpdate
typealias ItemAttributeSnapshot = SharedItemAttributeSnapshot
typealias ItemContextSnapshot = SharedItemContextSnapshot
typealias ExportProfileSnapshot = SharedExportProfileSnapshot
typealias AssistantPromptRequest = SharedAssistantPromptRequest
typealias AssistantPromptBuilder = SharedAssistantPromptBuilder
typealias ItemContextSnapshotBuilder = SharedItemContextSnapshotBuilder

// Personalization types
typealias AssistantPrefs = SharedAssistantPrefs
typealias AssistantTone = SharedAssistantTone
typealias AssistantRegion = SharedAssistantRegion
typealias AssistantUnits = SharedAssistantUnits
typealias AssistantVerbosity = SharedAssistantVerbosity
