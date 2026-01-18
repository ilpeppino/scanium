package com.scanium.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scanium.app.BuildConfig

/**
 * DEV-only watermark overlay showing build fingerprint on camera screen.
 *
 * Position: top-left to avoid blocking camera controls (bottom buttons, top-right dev indicator).
 * Format: "dev a1b2c3d 2026-01-18 13:42:10"
 *
 * This helps verify that the deployed APK matches the working tree during development and testing.
 */
@Composable
fun CameraWatermark(modifier: Modifier = Modifier) {
    // Extract just the date portion from ISO-8601 timestamp
    val buildDate = BuildConfig.BUILD_TIME_UTC.substringBefore('T')
    val buildTime = BuildConfig.BUILD_TIME_UTC.substringAfter('T').substringBefore('.')

    val watermarkText = "${BuildConfig.FLAVOR} ${BuildConfig.GIT_SHA} $buildDate $buildTime"

    Text(
        text = watermarkText,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.7f),
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
