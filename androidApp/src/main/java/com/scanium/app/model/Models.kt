package com.scanium.app.model

// Typealiases to shared models for backward compatibility
// These allow selling/assistant code to use com.scanium.app.model imports
// while actually using the shared multiplatform models

typealias AssistantResponse = com.scanium.shared.core.models.assistant.AssistantResponse
typealias AssistantMessage = com.scanium.shared.core.models.assistant.AssistantMessage
typealias AssistantRole = com.scanium.shared.core.models.assistant.AssistantRole
typealias AssistantAction = com.scanium.shared.core.models.assistant.AssistantAction
typealias AssistantActionType = com.scanium.shared.core.models.assistant.AssistantActionType
typealias AssistantPrefs = com.scanium.shared.core.models.assistant.AssistantPrefs
typealias AssistantTone = com.scanium.shared.core.models.assistant.AssistantTone
typealias AssistantRegion = com.scanium.shared.core.models.assistant.AssistantRegion
typealias AssistantUnits = com.scanium.shared.core.models.assistant.AssistantUnits
typealias AssistantVerbosity = com.scanium.shared.core.models.assistant.AssistantVerbosity
typealias ConfidenceTier = com.scanium.shared.core.models.assistant.ConfidenceTier
typealias EvidenceBullet = com.scanium.shared.core.models.assistant.EvidenceBullet
typealias SuggestedAttribute = com.scanium.shared.core.models.assistant.SuggestedAttribute
typealias SuggestedDraftUpdate = com.scanium.shared.core.models.assistant.SuggestedDraftUpdate
typealias ItemContextSnapshot = com.scanium.shared.core.models.assistant.ItemContextSnapshot
typealias ItemAttributeSnapshot = com.scanium.shared.core.models.assistant.ItemAttributeSnapshot
typealias AttributeSource = com.scanium.shared.core.models.assistant.AttributeSource
typealias ExportProfileSnapshot = com.scanium.shared.core.models.assistant.ExportProfileSnapshot
typealias ItemContextSnapshotBuilder = com.scanium.shared.core.models.assistant.ItemContextSnapshotBuilder

// Pricing models (Phase 3)
typealias PricingPrefs = com.scanium.shared.core.models.assistant.PricingPrefs
typealias PricingInsights = com.scanium.shared.core.models.assistant.PricingInsights
typealias PricingResult = com.scanium.shared.core.models.assistant.PricingResult
typealias PriceRange = com.scanium.shared.core.models.assistant.PriceRange
typealias ComparableListing = com.scanium.shared.core.models.assistant.ComparableListing
