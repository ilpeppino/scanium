package com.scanium.app.selling.generation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scanium.app.R
import com.scanium.shared.core.models.assistant.ConfidenceTier
import com.scanium.shared.core.models.listing.GeneratedListing
import com.scanium.shared.core.models.listing.GeneratedListingWarning

/**
 * Screen displaying generated marketplace listing content.
 *
 * Features:
 * - Generated title and description with copy buttons
 * - Confidence indicators
 * - Warnings for items needing verification
 * - Regenerate and edit actions
 *
 * @param onNavigateBack Callback to navigate back
 * @param onUseListing Callback when user accepts the listing (title, description)
 * @param viewModel The view model managing generation state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratedListingScreen(
    onNavigateBack: () -> Unit,
    onUseListing: ((title: String, description: String) -> Unit)? = null,
    viewModel: ListingGenerationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.generated_listing_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val currentState = state) {
                is ListingGenerationState.Idle -> {
                    IdleContent()
                }

                is ListingGenerationState.Loading -> {
                    LoadingContent()
                }

                is ListingGenerationState.Success -> {
                    SuccessContent(
                        listing = currentState.listing,
                        onCopyTitle = { copyToClipboard(context, "Title", currentState.listing.title) },
                        onCopyDescription = { copyToClipboard(context, "Description", currentState.listing.description) },
                        onRegenerate = { viewModel.retry() },
                        onUseListing =
                            onUseListing?.let {
                                { it(currentState.listing.title, currentState.listing.description) }
                            },
                    )
                }

                is ListingGenerationState.Error -> {
                    ErrorContent(
                        message = currentState.message,
                        retryable = currentState.retryable,
                        onRetry = { viewModel.retry() },
                        onGoBack = onNavigateBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.generated_listing_select_item),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.generated_listing_generating),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuccessContent(
    listing: GeneratedListing,
    onCopyTitle: () -> Unit,
    onCopyDescription: () -> Unit,
    onRegenerate: () -> Unit,
    onUseListing: (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Title section
        ListingFieldCard(
            label = "Title",
            value = listing.title,
            confidence = listing.titleConfidence,
            onCopy = onCopyTitle,
        )

        // Description section
        ListingFieldCard(
            label = "Description",
            value = listing.description,
            confidence = listing.descriptionConfidence,
            onCopy = onCopyDescription,
            isMultiline = true,
        )

        // Warnings section
        if (listing.warnings.isNotEmpty()) {
            WarningsSection(warnings = listing.warnings)
        }

        // Suggested next photo
        listing.suggestedNextPhoto?.let { suggestion ->
            SuggestedPhotoCard(suggestion = suggestion)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRegenerate,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.common_regenerate))
            }

            if (onUseListing != null) {
                Button(
                    onClick = onUseListing,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.common_use_this))
                }
            }
        }
    }
}

@Composable
private fun ListingFieldCard(
    label: String,
    value: String,
    confidence: ConfidenceTier,
    onCopy: () -> Unit,
    isMultiline: Boolean = false,
) {
    val confidenceColor =
        when (confidence) {
            ConfidenceTier.HIGH -> MaterialTheme.colorScheme.primary
            ConfidenceTier.MED -> MaterialTheme.colorScheme.tertiary
            ConfidenceTier.LOW -> MaterialTheme.colorScheme.error
        }

    val accessibilityDescription = "$label: $value. Confidence: ${confidence.name}"

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityDescription },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )

                    // Confidence indicator
                    Box(
                        modifier =
                            Modifier
                                .background(
                                    confidenceColor.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.small,
                                ).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text =
                                when (confidence) {
                                    ConfidenceTier.HIGH -> stringResource(R.string.export_assistant_confidence_high)
                                    ConfidenceTier.MED -> stringResource(R.string.export_assistant_confidence_medium)
                                    ConfidenceTier.LOW -> stringResource(R.string.export_assistant_confidence_low)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = confidenceColor,
                        )
                    }
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.semantics { contentDescription = "Copy $label" },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style =
                    if (isMultiline) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WarningsSection(warnings: List<GeneratedListingWarning>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.generated_listing_please_verify),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            warnings.forEach { warning ->
                Row(
                    modifier = Modifier.padding(start = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "\u2022",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = warning.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedPhotoCard(suggestion: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Column {
                Text(
                    text = stringResource(R.string.generated_listing_suggested_photo),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onGoBack: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )

            Text(
                text = stringResource(R.string.export_assistant_generation_failed),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onGoBack) {
                    Text(stringResource(R.string.common_go_back))
                }

                if (retryable) {
                    Button(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
}
