package com.scanium.app.selling.util

import android.util.Log
import com.scanium.app.domain.DomainPackProvider
import com.scanium.app.ScannedItem
import kotlinx.coroutines.runBlocking

object ListingTitleBuilder {
    private const val MAX_TITLE_LENGTH = 80
    private const val USED_PREFIX = "Used"
    private val fallbackDomainNames =
        mapOf(
            "furniture_sofa" to "Sofa",
            "furniture_chair" to "Chair",
            "furniture_table" to "Table",
        )

    fun buildTitle(item: ScannedItem): String {
        val labelText = item.labelText?.trim().takeUnless { it.isNullOrEmpty() }
        val domainCategoryName = item.domainCategoryId?.let { resolveDomainCategory(it) }?.takeUnless { it.isBlank() }
        val categoryName =
            item.category.displayName
                .trim()
                .takeUnless { it.isEmpty() }
        val baseLabel = domainCategoryName ?: labelText ?: categoryName ?: "Item"
        val capitalizedLabel = capitalizeFirst(baseLabel)
        val title = "$USED_PREFIX $capitalizedLabel".trim()

        return if (title.length <= MAX_TITLE_LENGTH) {
            title
        } else {
            title.substring(0, MAX_TITLE_LENGTH).trimEnd()
        }
    }

    private fun resolveDomainCategory(id: String): String? {
        if (!DomainPackProvider.isInitialized) {
            return fallbackDomainNames[id]
        }
        val resolvedName =
            runCatching {
                runBlocking {
                    val pack = DomainPackProvider.repository.getActiveDomainPack()
                    pack.categories.firstOrNull { it.id == id }?.displayName
                }
            }.onFailure {
                Log.w("ListingTitleBuilder", "Failed to resolve domain category $id", it)
            }.getOrNull()
                ?.takeUnless { it.isNullOrBlank() }

        return resolvedName ?: fallbackDomainNames[id]
    }

    private fun capitalizeFirst(value: String): String {
        if (value.isEmpty()) return value
        val first = value[0].uppercaseChar()
        return if (value.length == 1) {
            first.toString()
        } else {
            first.toString() + value.substring(1)
        }
    }
}
