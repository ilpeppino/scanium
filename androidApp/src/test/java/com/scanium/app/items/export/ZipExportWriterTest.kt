package com.scanium.app.items.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.scanium.core.export.ExportItem
import com.scanium.core.export.ExportPayload
import com.scanium.shared.core.models.model.ImageRef
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZipExportWriterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun writeToCache_includesAllImagesWithUniqueNames() = runBlocking {
        val imageRefs = listOf(
            ImageRef.Bytes(bytes = "one".toByteArray(), mimeType = "image/jpeg", width = 1, height = 1),
            ImageRef.Bytes(bytes = "two".toByteArray(), mimeType = "image/jpeg", width = 1, height = 1),
            ImageRef.Bytes(bytes = "three".toByteArray(), mimeType = "image/jpeg", width = 1, height = 1),
        )

        val item =
            ExportItem(
                id = "item-zip",
                title = "Zip Item",
                description = "Test",
                category = "Test",
                imageRef = imageRefs.first(),
                imageRefs = imageRefs,
            )
        val payload = ExportPayload(items = listOf(item), createdAt = Instant.DISTANT_PAST)

        val result = ZipExportWriter().writeToCache(context, payload).getOrThrow()
        val outputDir = File("build/test-outputs").apply { mkdirs() }
        val copyTarget = File(outputDir, "scanium-export-test.zip")
        result.zipFile.copyTo(copyTarget, overwrite = true)

        ZipFile(copyTarget).use { zip ->
            val entries = buildList {
                val enumeration = zip.entries()
                while (enumeration.hasMoreElements()) {
                    add(enumeration.nextElement().name)
                }
            }
            assertThat(entries).contains("items.csv")
            assertThat(entries).contains("images/item_item-zip_001.jpg")
            assertThat(entries).contains("images/item_item-zip_002.jpg")
            assertThat(entries).contains("images/item_item-zip_003.jpg")
        }

        assertThat(result.photosRequested).isEqualTo(3)
        assertThat(result.photosWritten).isEqualTo(3)
        assertThat(result.photosSkipped).isEqualTo(0)
    }
}
