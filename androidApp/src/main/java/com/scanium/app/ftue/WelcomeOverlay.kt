package com.scanium.app.ftue

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R

/**
 * Full-screen welcome overlay shown at the start of the FTUE tour.
 * Displays core app functionalities with icons and bullet points.
 *
 * @param onStart Callback when "Start Tour" button is clicked
 * @param onSkip Callback when "Skip" button is clicked
 * @param modifier Modifier for the overlay
 */
@Composable
fun WelcomeOverlay(
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                // Title
                Text(
                    text = stringResource(R.string.ftue_welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Introduction
                Text(
                    text = stringResource(R.string.ftue_welcome_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Feature bullets
                FeatureBullet(
                    icon = Icons.Default.Camera,
                    contentDescription = stringResource(R.string.cd_feature_scan_items),
                    text = stringResource(R.string.ftue_feature_scan_items),
                )

                Spacer(modifier = Modifier.height(12.dp))

                FeatureBullet(
                    icon = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.cd_feature_view_items),
                    text = stringResource(R.string.ftue_feature_view_items),
                )

                Spacer(modifier = Modifier.height(12.dp))

                FeatureBullet(
                    icon = Icons.Default.ShoppingCart,
                    contentDescription = stringResource(R.string.cd_feature_export_items),
                    text = stringResource(R.string.ftue_feature_export_items),
                )

                Spacer(modifier = Modifier.height(12.dp))

                FeatureBullet(
                    icon = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.cd_feature_ai_assistant),
                    text = stringResource(R.string.ftue_feature_ai_assistant),
                )

                Spacer(modifier = Modifier.height(12.dp))

                FeatureBullet(
                    icon = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.cd_feature_settings),
                    text = stringResource(R.string.ftue_feature_customize_settings),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.ftue_skip_tour))
                    }

                    Spacer(Modifier.width(16.dp))

                    Button(onClick = onStart) {
                        Text(stringResource(R.string.ftue_start_tour))
                    }
                }
            }
        }
    }
}

/**
 * Individual feature bullet point with icon and text.
 *
 * @param icon Icon to display before the text
 * @param text Feature description text
 * @param modifier Modifier for the row
 */
@Composable
private fun FeatureBullet(
    icon: ImageVector,
    contentDescription: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
