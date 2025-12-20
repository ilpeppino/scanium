package com.scanium.app.selling.util

import com.scanium.app.items.ScannedItem

object ListingTitleBuilder {
    private const val MAX_TITLE_LENGTH = 80
    private const val USED_PREFIX = "Used"

    fun buildTitle(item: ScannedItem): String {
        val labelText = item.labelText?.trim().takeUnless { it.isNullOrEmpty() }
        val categoryName = item.category.displayName.trim().takeUnless { it.isEmpty() }
        val baseLabel = labelText ?: categoryName ?: "Item"
        val capitalizedLabel = capitalizeFirst(baseLabel)
        val title = "$USED_PREFIX $capitalizedLabel".trim()

        return if (title.length <= MAX_TITLE_LENGTH) {
            title
        } else {
            title.substring(0, MAX_TITLE_LENGTH).trimEnd()
        }
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
