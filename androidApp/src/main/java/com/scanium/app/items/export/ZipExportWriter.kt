package com.scanium.app.items.export

import android.content.Context
import com.scanium.android.platform.adapters.toImageRefJpeg
import com.scanium.android.platform.adapters.toBitmap
import com.scanium.app.items.ThumbnailCache
import com.scanium.core.export.ExportPayload
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExportWriter(
    private val csvSerializer: CsvExportSerializer = CsvExportSerializer()
) {
    private companion object {
        const val EXPORTS_DIR = "exports"
        const val IMAGES_DIR = "images"
        const val JPEG_MIME = "image/jpeg"
        const val JPG_MIME = "image/jpg"
    }

    suspend fun writeToCache(context: Context, payload: ExportPayload): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "scanium-export-$timestamp.zip"
            val outputDir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
            val file = File(outputDir, fileName)
            val imageFilenames = mutableMapOf<String, String>()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
                payload.items.forEach { item ->
                    val imageBytes = resolveImageBytes(item.imageRef)?.let { ensureJpegBytes(it) }
                    if (imageBytes != null) {
                        val entryName = imageEntryName(item.id)
                        zip.putNextEntry(ZipEntry(entryName))
                        zip.write(imageBytes)
                        zip.closeEntry()
                        imageFilenames[item.id] = entryName
                    }
                }

                zip.putNextEntry(ZipEntry("items.csv"))
                val writer = OutputStreamWriter(zip, Charsets.UTF_8)
                csvSerializer.writeTo(writer, payload.items) { item ->
                    imageFilenames[item.id].orEmpty()
                }
                writer.flush()
                zip.closeEntry()
            }

            file
        }
    }

    private fun resolveImageBytes(imageRef: ImageRef?): ImageRef.Bytes? = when (imageRef) {
        is ImageRef.CacheKey -> ThumbnailCache.get(imageRef.key)
        is ImageRef.Bytes -> imageRef
        else -> null
    }

    private suspend fun ensureJpegBytes(imageRef: ImageRef.Bytes): ByteArray? {
        if (imageRef.mimeType.equals(JPEG_MIME, ignoreCase = true) ||
            imageRef.mimeType.equals(JPG_MIME, ignoreCase = true)
        ) {
            return imageRef.bytes
        }
        return runCatching {
            imageRef.toBitmap().toImageRefJpeg().bytes
        }.getOrNull()
    }

    internal fun imageEntryName(itemId: String): String = "$IMAGES_DIR/item_$itemId.jpg"
}
