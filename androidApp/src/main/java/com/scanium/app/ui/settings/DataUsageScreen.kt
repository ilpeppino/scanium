package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Usage & Transparency") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "How Scanium Uses Your Data",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            DataUsageSection(
                title = "On-Device Processing",
                body = "Most of Scanium's magic happens directly on your phone. Object detection, barcode scanning, and text recognition are performed using on-device machine learning models. Your camera feed is processed locally and frames are discarded immediately after analysis."
            )

            DataUsageSection(
                title = "Cloud Classification (Optional)",
                body = "If you enable 'Cloud Classification' in settings, Scanium sends cropped images of detected objects to our secure servers for more accurate identification. These images are processed transiently and are not permanently stored linked to your identity. We do NOT send full scene images, only the specific object detected."
            )

            DataUsageSection(
                title = "What We Do NOT Collect",
                body = "• We do not access your contacts, calendar, or other personal data.\n• We do not track your location unless you explicitly attach it to a scan (future feature).\n• We do not sell your personal data to third parties."
            )

            DataUsageSection(
                title = "Crash Reports & Diagnostics (Optional)",
                body = "If you opt-in to 'Share Diagnostics', we collect anonymous crash reports and performance data to help us fix bugs and improve the app. This data is de-identified and does not contain personal information."
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "You are in control.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You can toggle Cloud Classification and Diagnostics at any time in the Settings menu.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun DataUsageSection(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
