package com.scanium.app.items.export.bundle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.scanium.app.items.ThumbnailCache
import com.scanium.shared.core.models.model.ImageRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates structured ZIP bundles for export.
 *
 * ZIP Structure:
 * ```
 * scanium-export-YYYYMMDD-HHmmss.zip
 * ├── export-manifest.json
 * └── items/
 *     ├── <itemId1>/
 *     │   ├── listing.txt
 *     │   ├── listing.json
 *     │   └── photos/
 *     │       ├── 001.jpg
 *     │       ├── 002.jpg
 *     │       └── ...
 *     ├── <itemId2>/
 *     │   └── ...
 *     └── ...
 * ```
 *
 * Performance considerations:
 * - Streams photos directly from disk (no full memory load)
 * - Uses buffered I/O for efficiency
 * - Compresses photos at 85% JPEG quality to reduce size
 */
@Singleton
class BundleZipExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "BundleZipExporter"
        private const val EXPORTS_DIR = "bundle_exports"
        private const val BUFFER_SIZE = 8192
        private const val JPEG_QUALITY = 85
        private const val MAX_THUMBNAIL_SIZE = 1200 // Max dimension for thumbnails
    }

    /**
     * Progress callback for export operations.
     */
    interface ProgressCallback {
        fun onProgress(current: Int, total: Int, stage: ExportStage)
        fun onStageChange(stage: ExportStage)
    }

    enum class ExportStage {
        PREPARING,
        COPYING_PHOTOS,
        CREATING_ZIP,
        FINALIZING,
    }

    /**
     * Export result containing the ZIP file and metadata.
     */
    data class ExportResult(
        val zipFile: File,
        val itemCount: Int,
        val photoCount: Int,
        val fileSizeBytes: Long,
    )

    /**
     * Create a ZIP export from bundles.
     *
     * @param result The export bundle result
     * @param progressCallback Optional progress callback
     * @return Result containing the ZIP file or error
     */
    suspend fun createZip(
        result: ExportBundleResult,
        progressCallback: ProgressCallback? = null,
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            progressCallback?.onStageChange(ExportStage.PREPARING)

            // Create output directory
            val outputDir = File(context.cacheDir, EXPORTS_DIR).apply {
                if (!exists()) mkdirs()
                // Clean old exports (older than 1 hour)
                cleanOldExports(this)
            }

            // Generate filename
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val zipFile = File(outputDir, "scanium-export-$timestamp.zip")

            var totalPhotos = 0

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                // Write manifest
                progressCallback?.onProgress(0, result.bundles.size + 1, ExportStage.PREPARING)
                writeManifest(zip, result)

                // Process each bundle
                result.bundles.forEachIndexed { index, bundle ->
                    progressCallback?.onProgress(index + 1, result.bundles.size + 1, ExportStage.COPYING_PHOTOS)

                    val photosWritten = writeItemBundle(zip, bundle)
                    totalPhotos += photosWritten

                    Log.d(TAG, "Processed item ${bundle.itemId}: $photosWritten photos")
                }

                progressCallback?.onStageChange(ExportStage.FINALIZING)
            }

            Log.i(TAG, "Created ZIP: ${zipFile.absolutePath}, size: ${zipFile.length()} bytes")

            ExportResult(
                zipFile = zipFile,
                itemCount = result.bundles.size,
                photoCount = totalPhotos,
                fileSizeBytes = zipFile.length(),
            )
        }
    }

    /**
     * Write the export manifest to the ZIP.
     */
    private fun writeManifest(zip: ZipOutputStream, result: ExportBundleResult) {
        val manifest = ManifestBuilder.build(result)
        zip.putNextEntry(ZipEntry("export-manifest.json"))
        zip.write(manifest.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /**
     * Write a single item bundle to the ZIP.
     *
     * @return Number of photos written
     */
    private fun writeItemBundle(zip: ZipOutputStream, bundle: ExportItemBundle): Int {
        val itemPath = "items/${bundle.itemId}"

        // Write listing.txt
        val listingText = ListingTextFormatter.formatSingle(bundle)
        zip.putNextEntry(ZipEntry("$itemPath/listing.txt"))
        zip.write(listingText.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // Write listing.json (with relative photo paths)
        val listingJson = ListingJsonFormatter.formatSingleString(bundle)
        zip.putNextEntry(ZipEntry("$itemPath/listing.json"))
        zip.write(listingJson.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        // Write photos
        var photoIndex = 0

        // Write primary photo (thumbnail) if available
        bundle.primaryPhotoUri?.let { uri ->
            if (writePhoto(zip, "$itemPath/photos", photoIndex, File(uri))) {
                photoIndex++
            }
        }

        // Write additional photos
        for (photoUri in bundle.photoUris) {
            val photoFile = File(photoUri)
            if (photoFile.exists() && writePhoto(zip, "$itemPath/photos", photoIndex, photoFile)) {
                photoIndex++
            }
        }

        return photoIndex
    }

    /**
     * Write a photo to the ZIP, optionally resizing and stripping EXIF.
     *
     * @return true if photo was written successfully
     */
    private fun writePhoto(
        zip: ZipOutputStream,
        basePath: String,
        index: Int,
        photoFile: File,
    ): Boolean {
        if (!photoFile.exists()) {
            Log.w(TAG, "Photo file not found: ${photoFile.absolutePath}")
            return false
        }

        return try {
            val entryName = "$basePath/${String.format("%03d", index + 1)}.jpg"
            zip.putNextEntry(ZipEntry(entryName))

            // For JPEG files, we can either:
            // 1. Stream directly (fastest, preserves EXIF)
            // 2. Re-encode (strips EXIF, can resize)
            // We choose to re-encode to strip EXIF for privacy
            val processed = processPhoto(photoFile)
            if (processed != null) {
                zip.write(processed)
            } else {
                // Fallback: stream directly
                streamFile(photoFile, zip)
            }

            zip.closeEntry()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write photo: ${e.message}")
            false
        }
    }

    /**
     * Process a photo: decode, optionally resize, and re-encode.
     * This strips EXIF metadata for privacy.
     *
     * @return Processed JPEG bytes, or null if processing fails
     */
    private fun processPhoto(photoFile: File): ByteArray? {
        return try {
            // Decode with sampling if image is very large
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(photoFile.absolutePath, options)

            // Calculate sample size if image is larger than target
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize

            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath, options)
                ?: return null

            // Resize if still too large
            val resized = resizeIfNeeded(bitmap)

            // Encode as JPEG (this strips EXIF)
            val output = java.io.ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)

            // Clean up
            if (resized !== bitmap) {
                resized.recycle()
            }
            bitmap.recycle()

            output.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process photo: ${e.message}")
            null
        }
    }

    /**
     * Calculate sample size for decoding large images.
     */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val maxDim = maxOf(width, height)

        // Target max dimension of 2400px for export
        val targetMax = 2400

        while (maxDim / sampleSize > targetMax) {
            sampleSize *= 2
        }

        return sampleSize
    }

    /**
     * Resize bitmap if it exceeds maximum dimensions.
     */
    private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_THUMBNAIL_SIZE) {
            return bitmap
        }

        val scale = MAX_THUMBNAIL_SIZE.toFloat() / maxDim
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Stream a file directly to the ZIP output.
     */
    private fun streamFile(file: File, out: ZipOutputStream) {
        BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
        }
    }

    /**
     * Clean old export files (older than 1 hour).
     */
    private fun cleanOldExports(dir: File) {
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        dir.listFiles()?.filter { it.lastModified() < oneHourAgo }?.forEach { file ->
            Log.d(TAG, "Cleaning old export: ${file.name}")
            file.delete()
        }
    }

    /**
     * Get total size estimate for the export (before compression).
     */
    fun estimateExportSize(result: ExportBundleResult): Long {
        var totalSize = 0L

        // Manifest estimate
        totalSize += 2048 // ~2KB for manifest

        // Each bundle
        for (bundle in result.bundles) {
            // Text files
            totalSize += 4096 // ~4KB for listing.txt + listing.json

            // Photos (estimate from file sizes)
            bundle.photoUris.forEach { uri ->
                totalSize += File(uri).length()
            }
        }

        return totalSize
    }
}
