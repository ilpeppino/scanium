package com.scanium.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanium.app.BuildConfig
import com.scanium.app.R
import com.scanium.app.ml.classification.ClassificationMode

/**
 * Loading overlay shown during ML Kit model download on first launch.
 */
@Composable
internal fun ModelLoadingOverlay(state: ModelDownloadState) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .padding(32.dp)
                    .fillMaxWidth(0.85f),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text =
                        when (state) {
                            is ModelDownloadState.Checking -> stringResource(R.string.camera_model_preparing)
                            is ModelDownloadState.Downloading -> stringResource(R.string.camera_model_downloading)
                            else -> stringResource(R.string.camera_model_loading)
                        },
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.camera_model_first_launch_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Error dialog shown when ML Kit model download fails.
 */
@Composable
internal fun ModelErrorDialog(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.camera_model_init_failed)) },
        text = {
            Column {
                Text(error)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.camera_requirements_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.camera_requirements_internet), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.camera_requirements_storage), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.camera_requirements_network), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.camera_continue_anyway))
            }
        },
    )
}

/**
 * Configuration status banner shown when cloud mode is enabled but not configured.
 *
 * Surfaces the configuration requirement from DEV_GUIDE.md to avoid accidental
 * network use once API keys are provided.
 */
@Composable
internal fun ConfigurationStatusBanner(
    classificationMode: ClassificationMode,
    modifier: Modifier = Modifier,
) {
    val isCloudConfigured = BuildConfig.SCANIUM_API_BASE_URL.isNotBlank()
    val showBanner = classificationMode == ClassificationMode.CLOUD && !isCloudConfigured

    if (showBanner) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
            tonalElevation = 4.dp,
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.cd_configuration_warning),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = stringResource(R.string.camera_cloud_not_configured),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.camera_cloud_not_configured_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}
