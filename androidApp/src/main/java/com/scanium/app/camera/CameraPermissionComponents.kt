package com.scanium.app.camera

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import com.scanium.app.R

/**
 * UI shown when camera hardware is unavailable or binding fails.
 */
@Composable
internal fun CameraErrorContent(
    error: CameraErrorState?,
    onRetry: () -> Unit,
    onViewItems: () -> Unit,
) {
    val resolvedError =
        error ?: CameraErrorState(
            title = stringResource(R.string.camera_unavailable_title),
            message = stringResource(R.string.camera_unavailable_message),
            canRetry = true,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = resolvedError.title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = resolvedError.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (resolvedError.canRetry) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        TextButton(onClick = onViewItems) {
            Text(stringResource(R.string.camera_view_items))
        }
    }
}

/**
 * UI shown when camera permission is denied.
 *
 * Provides educative content and context-aware actions based on permission state:
 * - First request: Shows rationale and grant permission button
 * - Denied with rationale: Explains importance and offers to try again
 * - Permanently denied: Guides user to open system settings
 */
@OptIn(ExperimentalPermissionsApi::class)
@VisibleForTesting
@Composable
internal fun PermissionDeniedContent(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val isPermanentlyDenied =
        !permissionState.status.shouldShowRationale &&
            !permissionState.status.isGranted

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Camera icon
        Icon(
            imageVector = Icons.Default.Camera,
            contentDescription = stringResource(R.string.cd_camera_permission_required),
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = stringResource(R.string.camera_permission_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description - varies based on permission state
        Text(
            text =
                if (isPermanentlyDenied) {
                    stringResource(R.string.camera_permission_description_settings)
                } else {
                    stringResource(R.string.camera_permission_description_rationale)
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Feature list - what camera enables
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.camera_permission_features_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FeatureItem("📦", stringResource(R.string.camera_permission_feature_detection))
                FeatureItem("📸", stringResource(R.string.camera_permission_feature_capture))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Privacy note
        Text(
            text = stringResource(R.string.camera_permission_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons - different based on permission state
        if (isPermanentlyDenied) {
            // Permission permanently denied - guide to settings
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(0.8f),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_open_settings),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.camera_open_settings))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.camera_permission_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            // Permission can still be requested
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(0.8f),
            ) {
                Text(stringResource(R.string.camera_grant_camera_access))
            }
        }
    }
}

/**
 * Helper composable for feature list items
 */
@Composable
internal fun FeatureItem(
    icon: String,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(32.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
