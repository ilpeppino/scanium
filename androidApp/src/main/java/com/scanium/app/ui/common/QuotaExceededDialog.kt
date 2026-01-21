package com.scanium.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanium.app.R

/**
 * Dialog shown when user exceeds their daily Vision API quota.
 *
 * Displays:
 * - Clear message about daily limit
 * - Reset time
 * - Encouragement to support the app via donation
 * - PayPal donation buttons
 *
 * @param quotaLimit The daily quota limit (e.g., 50)
 * @param resetTime The time when quota resets (e.g., "23:45")
 * @param onDismiss Callback when dialog is dismissed
 * @param onDonationClicked Callback when donation button is clicked (for analytics)
 */
@Composable
fun QuotaExceededDialog(
    quotaLimit: Int?,
    resetTime: String?,
    onDismiss: () -> Unit,
    onDonationClicked: ((amount: Int) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.quota_exceeded_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Quota limit message
                if (quotaLimit != null) {
                    Text(
                        text = stringResource(R.string.quota_exceeded_message, quotaLimit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.quota_exceeded_message_generic),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Reset time
                if (resetTime != null) {
                    Text(
                        text = stringResource(R.string.quota_exceeded_reset, resetTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Encouragement message
                Text(
                    text = stringResource(R.string.quota_exceeded_support),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Donation buttons
                DonationContent(
                    onDonationClicked = onDonationClicked,
                    showFooter = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
