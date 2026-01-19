package com.scanium.app.items.export.bundle

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles plain text export and sharing via Android intents.
 *
 * Provides:
 * - Share text via system share sheet
 * - Copy text to clipboard
 * - Format options (full, compact, single)
 */
@Singleton
class PlainTextExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "PlainTextExporter"
        }

        /**
         * Text format options for export.
         */
        enum class TextFormat {
            /** Full marketplace-ready format with all details */
            FULL,

            /** Compact list format for quick sharing */
            COMPACT,

            /** Single item format (uses first bundle only) */
            SINGLE,
        }

        /**
         * Result of a text export operation.
         */
        sealed class ExportTextResult {
            data class Success(
                val text: String,
                val itemCount: Int,
            ) : ExportTextResult()

            data class Error(
                val message: String,
            ) : ExportTextResult()
        }

        /**
         * Generate export text from bundles.
         *
         * @param result The export bundle result
         * @param format The text format to use
         * @return Formatted text
         */
        fun generateText(
            result: ExportBundleResult,
            format: TextFormat = TextFormat.FULL,
        ): ExportTextResult {
            if (result.bundles.isEmpty()) {
                return ExportTextResult.Error("No items to export")
            }

            val text =
                when (format) {
                    TextFormat.FULL -> {
                        ListingTextFormatter.formatMultiple(result.bundles)
                    }

                    TextFormat.COMPACT -> {
                        ListingTextFormatter.formatCompactList(result.bundles)
                    }

                    TextFormat.SINGLE -> {
                        if (result.bundles.size == 1) {
                            ListingTextFormatter.formatSingle(result.bundles.first())
                        } else {
                            ListingTextFormatter.formatMultiple(result.bundles)
                        }
                    }
                }

            return ExportTextResult.Success(text, result.bundles.size)
        }

        /**
         * Create a share intent for the export text.
         *
         * @param result The export bundle result
         * @param format The text format to use
         * @return Intent for sharing, or null if no items
         */
        fun createShareIntent(
            result: ExportBundleResult,
            format: TextFormat = TextFormat.FULL,
        ): Intent? {
            val textResult = generateText(result, format)

            return when (textResult) {
                is ExportTextResult.Success -> {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, textResult.text)
                        putExtra(Intent.EXTRA_SUBJECT, "Scanium Export (${textResult.itemCount} items)")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }

                is ExportTextResult.Error -> {
                    Log.w(TAG, "Cannot create share intent: ${textResult.message}")
                    null
                }
            }
        }

        /**
         * Copy export text to clipboard.
         *
         * @param result The export bundle result
         * @param format The text format to use
         * @return true if copied successfully
         */
        fun copyToClipboard(
            result: ExportBundleResult,
            format: TextFormat = TextFormat.FULL,
        ): Boolean {
            val textResult = generateText(result, format)

            return when (textResult) {
                is ExportTextResult.Success -> {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip =
                            ClipData.newPlainText(
                                "Scanium Export",
                                textResult.text,
                            )
                        clipboard.setPrimaryClip(clip)
                        Log.i(TAG, "Copied ${textResult.itemCount} items to clipboard")
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy to clipboard: ${e.message}")
                        false
                    }
                }

                is ExportTextResult.Error -> {
                    Log.w(TAG, "Cannot copy to clipboard: ${textResult.message}")
                    false
                }
            }
        }

        /**
         * Share a single bundle as text.
         */
        fun createSingleShareIntent(bundle: ExportItemBundle): Intent {
            val text = ListingTextFormatter.formatSingle(bundle)
            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, bundle.title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /**
         * Copy a single bundle's text to clipboard.
         */
        fun copySingleToClipboard(bundle: ExportItemBundle): Boolean =
            try {
                val text = ListingTextFormatter.formatSingle(bundle)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(bundle.title, text)
                clipboard.setPrimaryClip(clip)
                Log.i(TAG, "Copied item ${bundle.itemId} to clipboard")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy to clipboard: ${e.message}")
                false
            }
    }
