package com.scanium.app.selling.util

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.scanium.app.model.resolveBytes
import com.scanium.shared.core.models.model.ImageRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object ListingShareHelper {
    private const val SHARE_DIR = "listing_share"
    private const val MAX_IMAGES_DEFAULT = 6

    suspend fun writeShareImages(
        context: Context,
        itemId: String,
        images: List<ImageRef>,
        maxImages: Int = MAX_IMAGES_DEFAULT,
    ): List<Uri> =
        withContext(Dispatchers.IO) {
            val outputDir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            val authority = "${context.packageName}.fileprovider"

            images
                .mapNotNull { it.resolveBytes() }
                .take(maxImages)
                .mapIndexedNotNull { index, image ->
                    if (!image.mimeType.startsWith("image/")) return@mapIndexedNotNull null
                    val extension = mimeToExtension(image.mimeType)
                    val safeId = itemId.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val file = File(outputDir, "${safeId}_${index + 1}.$extension")
                    file.writeBytes(image.bytes)
                    FileProvider.getUriForFile(context, authority, file)
                }
        }

    fun buildShareIntent(
        contentResolver: ContentResolver,
        text: String,
        imageUris: List<Uri>,
    ): Intent {
        val intent =
            if (imageUris.size > 1) {
                Intent(Intent.ACTION_SEND_MULTIPLE)
            } else {
                Intent(Intent.ACTION_SEND)
            }

        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        when (imageUris.size) {
            0 -> {
                intent.type = "text/plain"
            }

            1 -> {
                intent.type = "image/*"
                intent.putExtra(Intent.EXTRA_STREAM, imageUris.first())
                intent.clipData = ClipData.newUri(contentResolver, "Listing image", imageUris.first())
            }

            else -> {
                intent.type = "image/*"
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
                val clipData = ClipData.newUri(contentResolver, "Listing images", imageUris.first())
                imageUris.drop(1).forEach { uri ->
                    clipData.addItem(ClipData.Item(uri))
                }
                intent.clipData = clipData
            }
        }

        return intent
    }

    private fun mimeToExtension(mimeType: String): String =
        when (mimeType.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
}
