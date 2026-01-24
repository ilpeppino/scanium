package com.scanium.app.classification.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity for storing classification corrections locally.
 * Corrections are synced to the backend when online.
 */
@Entity(
    tableName = "classification_corrections",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["correctedAt"]),
        Index(value = ["syncedToBackend"]),
    ],
)
data class ClassificationCorrectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val imageHash: String,
    val originalCategoryId: String?,
    val originalCategoryName: String?,
    val originalConfidence: Float?,
    val correctedCategoryId: String,
    val correctedCategoryName: String,
    val correctionMethod: String, // "tap_alternative" | "manual_entry" | "search"
    val notes: String? = null,
    val visualContext: String, // JSON string of perception signals
    val correctedAt: Long = System.currentTimeMillis(),
    val syncedToBackend: Boolean = false,
    val syncedAt: Long? = null,
)
