package com.scanium.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R

/**
 * Reusable donation content composable.
 * Displays donation buttons for PayPal donations.
 *
 * @param onDonationClicked Optional callback when a donation button is clicked (for analytics)
 * @param showFooter Whether to show the PayPal footer text
 */
@Composable
fun DonationContent(
    modifier: Modifier = Modifier,
    onDonationClicked: ((amount: Int) -> Unit)? = null,
    showFooter: Boolean = true,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
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

        if (showFooter) {
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
        modifier =
            modifier.semantics {
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
private fun openPayPalDonation(
    context: Context,
    amount: Int,
) {
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
