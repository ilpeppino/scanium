package com.scanium.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

fun openInAppBrowser(
    context: Context,
    url: String,
) {
    val uri = Uri.parse(url)
    try {
        val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabsIntent.launchUrl(context, uri)
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (fallbackError: Exception) {
            android.util.Log.e("InAppBrowser", "Failed to open URL: $url", fallbackError)
        }
    }
}
