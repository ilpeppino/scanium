package com.scanium.app.items.export.bundle

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats export bundles as marketplace-friendly plain text.
 *
 * Output format is optimized for copy-pasting into marketplace listings:
 * - Title line (bold-styled with markers)
 * - Description paragraph
 * - Bullet points (if available)
 * - Attributes section
 * - Status indicator for items needing AI
 */
object ListingTextFormatter {
    private val SEPARATOR = "─".repeat(40)
    private const val SECTION_SEPARATOR = "\n\n"

    /**
     * Format a single bundle as marketplace text.
     */
    fun formatSingle(bundle: ExportItemBundle): String =
        buildString {
            // Title
            appendLine("📦 ${bundle.title}")
            appendLine()

            // Description
            appendLine(bundle.description)

            // Bullets
            if (bundle.bullets.isNotEmpty()) {
                appendLine()
                appendLine("Highlights:")
                bundle.bullets.forEach { bullet ->
                    appendLine("• $bullet")
                }
            }

            // Key attributes not already in description
            val additionalAttrs = formatAdditionalAttributes(bundle)
            if (additionalAttrs.isNotBlank()) {
                appendLine()
                append(additionalAttrs)
            }

            // Status indicator
            if (bundle.needsAi) {
                appendLine()
                appendLine("⚠️ [NEEDS_AI] Run Export Assistant for better description")
            }

            // Photo count
            if (bundle.photoCount > 0) {
                appendLine()
                appendLine("📷 ${bundle.photoCount} photo(s) available")
            }
        }

    /**
     * Format multiple bundles as a combined text block.
     * Each item is separated by a visual divider.
     */
    fun formatMultiple(bundles: List<ExportItemBundle>): String =
        buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("   SCANIUM EXPORT - ${bundles.size} item(s)")
            appendLine("   ${formatDate(System.currentTimeMillis())}")
            appendLine("═══════════════════════════════════════")
            appendLine()

            bundles.forEachIndexed { index, bundle ->
                appendLine("[${index + 1}/${bundles.size}] ${bundle.title}")
                appendLine(SEPARATOR)
                appendLine()

                // Description
                appendLine(bundle.description)

                // Bullets
                if (bundle.bullets.isNotEmpty()) {
                    appendLine()
                    bundle.bullets.forEach { bullet ->
                        appendLine("• $bullet")
                    }
                }

                // Key attributes
                val additionalAttrs = formatAdditionalAttributes(bundle)
                if (additionalAttrs.isNotBlank()) {
                    appendLine()
                    append(additionalAttrs)
                }

                // Status indicator
                if (bundle.needsAi) {
                    appendLine()
                    appendLine("⚠️ [NEEDS_AI]")
                }

                // Photo count
                appendLine()
                appendLine("📷 ${bundle.photoCount} photo(s)")

                // Separator between items
                if (index < bundles.size - 1) {
                    appendLine()
                    appendLine()
                }
            }

            appendLine()
            appendLine("═══════════════════════════════════════")
            appendLine("   Exported with Scanium")
            appendLine("═══════════════════════════════════════")
        }

    /**
     * Format as a compact list suitable for quick sharing.
     */
    fun formatCompactList(bundles: List<ExportItemBundle>): String =
        buildString {
            appendLine("Scanium Items (${bundles.size})")
            appendLine()

            bundles.forEachIndexed { index, bundle ->
                append("${index + 1}. ${bundle.title}")
                if (bundle.needsAi) append(" ⚠️")
                appendLine()

                // One-line description preview
                val preview =
                    bundle.description
                        .replace("\n", " ")
                        .take(80)
                        .let { if (bundle.description.length > 80) "$it..." else it }
                appendLine("   $preview")

                // Price if available
                val price =
                    bundle.attributes["userPrice"]?.value
                        ?: bundle.attributes["priceRange"]?.value
                if (price != null) {
                    appendLine("   Price: $price")
                }

                appendLine()
            }
        }

    /**
     * Format additional attributes not typically in the description.
     */
    private fun formatAdditionalAttributes(bundle: ExportItemBundle): String {
        val attrs = mutableListOf<String>()

        // Category
        attrs.add("Category: ${bundle.category.displayName}")

        // Confidence tier if available
        bundle.confidenceTier?.let { tier ->
            attrs.add("AI Confidence: $tier")
        }

        return attrs.joinToString("\n")
    }

    private fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return format.format(Date(timestamp))
    }
}
