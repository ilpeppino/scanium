package com.scanium.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

            DataUsageSectionWithIcon(
                icon = Icons.Default.PhoneAndroid,
                title = "On-Device Processing",
                body = "Most of Scanium's processing happens directly on your phone:\n\n" +
                    "• Object detection runs locally using ML Kit\n" +
                    "• Barcode and text recognition are fully on-device\n" +
                    "• Camera frames are processed in memory and discarded immediately\n" +
                    "• No data leaves your device unless you explicitly enable cloud features"
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.Cloud,
                title = "Cloud Classification (Opt-in)",
                body = "When enabled in Settings:\n\n" +
                    "• What is sent: Cropped thumbnail images of detected items only (not full camera frames)\n" +
                    "• When it is sent: Only for stable items that need enhanced classification\n" +
                    "• Where it is processed: Scanium backend + Google Cloud Vision API\n" +
                    "• Privacy measures: EXIF metadata is stripped, images are not stored permanently\n\n" +
                    "Toggle: Settings → Cloud Classification"
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.AutoAwesome,
                title = "AI Assistant (Opt-in)",
                body = "When enabled in Settings:\n\n" +
                    "• What is sent: Your question text + item context (category, attributes, condition)\n" +
                    "• Images: Only sent if you enable 'Send Images to Assistant' (off by default)\n" +
                    "• Where it is processed: Scanium backend → AI provider (provider-agnostic)\n" +
                    "• Privacy measures: Raw prompts are not logged, device ID is hashed\n\n" +
                    "Toggles:\n" +
                    "• Settings → Assistant Features\n" +
                    "• Settings → Send Images to Assistant"
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.Mic,
                title = "Voice Mode (Opt-in)",
                body = "When enabled in Settings:\n\n" +
                    "• Microphone: Only active when you press the mic button (never always-on)\n" +
                    "• Speech-to-Text: Processed using Android's built-in speech recognition (on-device where available)\n" +
                    "• Audio storage: Audio is NOT stored or uploaded by default\n" +
                    "• Text-to-Speech: Responses can be spoken aloud using on-device TTS\n\n" +
                    "Toggles:\n" +
                    "• Settings → Voice input (microphone)\n" +
                    "• Settings → Read assistant replies aloud\n" +
                    "• Settings → Auto-send after voice recognition (optional)"
            )

            DataUsageSectionWithIcon(
                icon = Icons.Default.BugReport,
                title = "Crash Reports & Diagnostics (Opt-in)",
                body = "When enabled in Settings:\n\n" +
                    "• What is collected: Anonymous crash reports, app performance metrics\n" +
                    "• What is NOT collected: Raw prompts, OCR text, audio, images, API keys\n" +
                    "• PII redaction: Personal data is automatically redacted\n" +
                    "• Purpose: Help us fix bugs and improve app stability\n\n" +
                    "Toggle: Settings → Share Diagnostics (off by default)"
            )

            DataUsageSection(
                title = "What We Do NOT Collect",
                body = "• We do not access your contacts, calendar, or other personal data\n" +
                    "• We do not track your location\n" +
                    "• We do not store raw audio recordings\n" +
                    "• We do not log your questions or OCR text\n" +
                    "• We do not sell your personal data to third parties"
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "You are in control",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All cloud features are opt-in. You can toggle them at any time in Settings. " +
                            "Use 'Privacy Safe Mode' to quickly disable all cloud data sharing with one tap.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DataUsageSectionWithIcon(icon: ImageVector, title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
