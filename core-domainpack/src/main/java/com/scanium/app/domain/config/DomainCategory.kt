package com.scanium.app.domain.config

import kotlinx.serialization.Serializable

/**
 * Represents a category definition in a Domain Pack.
 *
 * Categories provide a fine-grained, config-driven taxonomy that maps to the app's
 * coarse-grained ItemCategory enum. This allows domain-specific classification
 * (e.g., "sofa", "laptop", "running shoes") while maintaining compatibility with
 * existing ItemCategory-based code.
 *
 * Key features:
 * - Hierarchical structure via parentId (e.g., "furniture" -> "sofa")
 * - CLIP prompts for future on-device classification
 * - Mapping to existing ItemCategory enum
 * - Priority for tie-breaking when multiple categories match
 * - Enable/disable flag for A/B testing or gradual rollout
 *
 * Example:
 * ```
 * DomainCategory(
 *   id = "electronics_laptop",
 *   displayName = "Laptop",
 *   parentId = "electronics",
 *   itemCategoryName = "ELECTRONICS",
 *   prompts = ["a photo of a laptop computer", "a photo of a notebook computer"],
 *   priority = 15,
 *   enabled = true
 * )
 * ```
 *
 * @property id Stable unique identifier (e.g., "electronics_laptop", "furniture_sofa")
 * @property displayName Human-readable name for UI display (e.g., "Laptop", "Sofa")
 * @property parentId Optional parent category ID for hierarchical grouping
 * @property itemCategoryName Name of the ItemCategory enum value this maps to
 * @property prompts List of CLIP-style text prompts for classification (future use)
 * @property priority Priority value for tie-breaking (higher = preferred)
 * @property enabled Whether this category is active (allows disabling without code changes)
 */
@Serializable
data class DomainCategory(
    val id: String,
    val displayName: String,
    val parentId: String?,
    val itemCategoryName: String,
    val prompts: List<String>,
    val priority: Int? = null,
    val enabled: Boolean,
)
