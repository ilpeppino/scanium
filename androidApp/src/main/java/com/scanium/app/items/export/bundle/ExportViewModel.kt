package com.scanium.app.items.export.bundle

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanium.app.items.ScannedItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for managing export operations.
 *
 * Coordinates between:
 * - ExportBundleRepository (builds bundles from items)
 * - BundleZipExporter (creates ZIP files)
 * - PlainTextExporter (creates share intents)
 */
@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val bundleRepository: ExportBundleRepository,
        private val zipExporter: BundleZipExporter,
        private val textExporter: PlainTextExporter,
    ) : ViewModel() {
        companion object {
            private const val TAG = "ExportViewModel"
        }

        private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
        val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

        private val _bundleResult = MutableStateFlow<ExportBundleResult?>(null)
        val bundleResult: StateFlow<ExportBundleResult?> = _bundleResult.asStateFlow()

        private var currentItems: List<ScannedItem> = emptyList()
        private var selectedIds: Set<String> = emptySet()

        /**
         * Prepare export bundles for the selected items.
         *
         * @param items All items from the state manager
         * @param selectedIds IDs of items to export
         * @param limits Optional export limits
         */
        fun prepareExport(
            items: List<ScannedItem>,
            selectedIds: Set<String>,
            limits: ExportLimits = ExportLimits(),
        ) {
            this.currentItems = items
            this.selectedIds = selectedIds

            viewModelScope.launch {
                _exportState.value = ExportState.Preparing

                try {
                    val result =
                        bundleRepository.buildBundles(
                            items = items,
                            itemIds = selectedIds,
                            limits = limits,
                        )
                    _bundleResult.value = result
                    _exportState.value = ExportState.Idle

                    Log.i(TAG, "Prepared ${result.bundles.size} bundles for export")
                } catch (e: ExportLimitExceededException) {
                    Log.e(TAG, "Export limit exceeded: ${e.message}")
                    _exportState.value = ExportState.Error(e.message ?: "Export limit exceeded")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to prepare export: ${e.message}", e)
                    _exportState.value = ExportState.Error("Failed to prepare export: ${e.message}")
                }
            }
        }

        /**
         * Export items as a ZIP file.
         */
        fun exportZip() {
            val result = _bundleResult.value ?: return

            viewModelScope.launch {
                try {
                    _exportState.value = ExportState.CreatingZip(0f, 0, result.bundles.size)

                    val zipResult =
                        zipExporter.createZip(
                            result = result,
                            progressCallback =
                                object : BundleZipExporter.ProgressCallback {
                                    override fun onProgress(
                                        current: Int,
                                        total: Int,
                                        stage: BundleZipExporter.ExportStage,
                                    ) {
                                        val progress = current.toFloat() / total.coerceAtLeast(1)
                                        _exportState.value = ExportState.CreatingZip(progress, current, total)
                                    }

                                    override fun onStageChange(stage: BundleZipExporter.ExportStage) {
                                        Log.d(TAG, "Export stage: $stage")
                                    }
                                },
                        )

                    zipResult.fold(
                        onSuccess = { exportResult ->
                            Log.i(
                                TAG,
                                "ZIP created: ${exportResult.zipFile.name}, " +
                                    "${exportResult.itemCount} items, ${exportResult.photoCount} photos",
                            )

                            // Generate text as well for combined export
                            val textResult = textExporter.generateText(result)
                            val text =
                                when (textResult) {
                                    is PlainTextExporter.ExportTextResult.Success -> textResult.text
                                    else -> null
                                }

                            _exportState.value =
                                ExportState.Ready(
                                    result = result,
                                    zipFile = exportResult.zipFile,
                                    text = text,
                                )
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to create ZIP: ${error.message}", error)
                            _exportState.value = ExportState.Error("Failed to create ZIP: ${error.message}")
                        },
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "ZIP export failed: ${e.message}", e)
                    _exportState.value = ExportState.Error("Export failed: ${e.message}")
                }
            }
        }

        /**
         * Export items as text only.
         */
        fun exportText() {
            val result = _bundleResult.value ?: return

            viewModelScope.launch {
                _exportState.value = ExportState.Preparing

                val textResult = textExporter.generateText(result)

                when (textResult) {
                    is PlainTextExporter.ExportTextResult.Success -> {
                        _exportState.value =
                            ExportState.Ready(
                                result = result,
                                zipFile = null,
                                text = textResult.text,
                            )
                    }

                    is PlainTextExporter.ExportTextResult.Error -> {
                        _exportState.value = ExportState.Error(textResult.message)
                    }
                }
            }
        }

        /**
         * Create a share intent for a ZIP file.
         */
        fun createZipShareIntent(zipFile: File): Intent {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, zipFile)

            return Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newUri(context.contentResolver, zipFile.name, uri)
            }
        }

        /**
         * Create a share intent for text.
         */
        fun createTextShareIntent(text: String): Intent {
            val itemCount = _bundleResult.value?.totalItems ?: 0
            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "Scanium Export ($itemCount items)")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /**
         * Copy text to clipboard.
         */
        fun copyTextToClipboard(): Boolean {
            val result = _bundleResult.value ?: return false
            return textExporter.copyToClipboard(result)
        }

        /**
         * Reset export state.
         */
        fun reset() {
            _exportState.value = ExportState.Idle
            _bundleResult.value = null
        }

        /**
         * Check if export is in progress.
         */
        fun isExporting(): Boolean =
            when (_exportState.value) {
                is ExportState.Preparing,
                is ExportState.CreatingZip,
                -> true

                else -> false
            }
    }
