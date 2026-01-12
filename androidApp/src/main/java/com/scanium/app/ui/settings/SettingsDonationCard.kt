package com.scanium.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R

/**
 * Donation banner card for the Settings screen.
 *
 * Displays a polite, optional donation invitation using PayPal.
 * - Fully localized using Android string resources
 * - Opens PayPal links externally in browser
 * - Play Store policy compliant (no IAP, no blocking behavior)
 * - Always visible but never intrusive
 */
@Composable
fun SettingsDonationCard(
    modifier: Modifier = Modifier,
    onDonationClicked: ((amount: Int) -> Unit)? = null,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_donation_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body text
            Text(
                text = stringResource(R.string.settings_donation_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Donation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DonationButton(
                    label = stringResource(R.string.settings_donation_coffee_one),
                    amount = 2,
                    context = context,
                    onDonationClicked = onDonationClicked,
                    modifier = Modifier.weight(1f),
                )
                DonationButton(
                    label = stringResource(R.string.settings_donation_coffee_five),
                    amount = 10,
                    context = context,
                    onDonationClicked = onDonationClicked,
                    modifier = Modifier.weight(1f),
                )
                DonationButton(
                    label = stringResource(R.string.settings_donation_coffee_many),
                    amount = 20,
                    context = context,
                    onDonationClicked = onDonationClicked,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer with PayPal text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_donation_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DonationButton(
    label: String,
    amount: Int,
    context: Context,
    onDonationClicked: ((amount: Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = {
            // Emit analytics event if callback provided
            onDonationClicked?.invoke(amount)

            // Open PayPal link externally
            openPayPalDonation(context, amount)
        },
        modifier = modifier.semantics {
            contentDescription = "Donate $amount euros via PayPal"
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "€$amount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Opens a PayPal donation link in the external browser.
 *
 * @param context Android context for starting the intent
 * @param amount Donation amount in EUR
 */
private fun openPayPalDonation(context: Context, amount: Int) {
    val paypalUsername = "GiuseppeTempone"
    val url = "https://paypal.me/$paypalUsername/$amount"

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    // Ensure this opens in external browser, not WebView
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Silently fail if no browser available
        // This is acceptable for a donation feature
    }
}
