package com.scanium.app.selling.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListingShareHelperTest {
    @Test
    fun clipboardCopiesExpectedText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ListingClipboardHelper.copy(context, "Listing", "Hello World")

        val copied = ListingClipboardHelper.getPrimaryText(context)
        assertThat(copied.toString()).isEqualTo("Hello World")
    }

    @Test
    fun shareIntentUsesSendMultipleForMultipleImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uris =
            listOf(
                Uri.parse("content://example/image1"),
                Uri.parse("content://example/image2"),
            )

        val intent =
            ListingShareHelper.buildShareIntent(
                contentResolver = context.contentResolver,
                text = "Share text",
                imageUris = uris,
            )

        assertThat(intent.action).isEqualTo(android.content.Intent.ACTION_SEND_MULTIPLE)
        assertThat(intent.type).isEqualTo("image/*")
        val streams = intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM)
        assertThat(streams).hasSize(2)
        assertThat(intent.clipData).isNotNull()
        assertThat(intent.clipData?.itemCount).isEqualTo(2)
    }
}
