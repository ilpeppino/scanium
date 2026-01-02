package com.scanium.app.selling.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.R
import com.scanium.app.data.SettingsRepository
import com.scanium.app.di.ListingViewModelFactoryEntryPoint
import com.scanium.app.items.ScannedItem
import com.scanium.app.model.toImageBitmap
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.domain.ListingCondition
import dagger.hilt.android.EntryPointAccessors

/**
 * Screen for exporting items to eBay (mock implementation).
 *
 * Part of ARCH-001/DX-003: Updated to use Hilt's assisted injection for ViewModel creation,
 * reducing boilerplate by accessing the factory through EntryPoints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellOnEbayScreen(
    onNavigateBack: () -> Unit,
    selectedItems: List<ScannedItem>,
    marketplaceService: EbayMarketplaceService,
    itemsViewModel: com.scanium.app.items.ItemsViewModel,
) {
    val context = LocalContext.current
    val assistedFactory =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                ListingViewModelFactoryEntryPoint::class.java,
            ).listingViewModelFactory()
        }
    val viewModel: ListingViewModel =
        viewModel(
            factory =
                ListingViewModel.provideFactory(
                    assistedFactory = assistedFactory,
                    selectedItems = selectedItems,
                    itemsViewModel = itemsViewModel,
                ),
        )
    val uiState by viewModel.uiState.collectAsState()

    // SEC-010: Prevent screenshots of listing drafts (prices, item images)
    // Respects developer mode setting to allow screenshots when enabled
    val settingsRepository = remember { SettingsRepository(context) }
    val allowScreenshots by settingsRepository.devAllowScreenshotsFlow.collectAsState(initial = false)

    DisposableEffect(allowScreenshots) {
        val window = (context as? Activity)?.window
        if (!allowScreenshots) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            // Only clear if we set it
            if (!allowScreenshots) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export to eBay (Mock)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = { viewModel.postSelectedToEbay() },
                    enabled = uiState.drafts.isNotEmpty() && !uiState.isPosting,
                    modifier =
                        Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                ) {
                    Text("Export to eBay (Mock)")
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.drafts, key = { it.draft.scannedItemId }) { draftState ->
                ListingDraftCard(
                    draftState = draftState,
                    onTitleChanged = { viewModel.updateDraftTitle(draftState.draft.scannedItemId, it) },
                    onPriceChanged = { viewModel.updateDraftPrice(draftState.draft.scannedItemId, it) },
                    onConditionChanged = { viewModel.updateDraftCondition(draftState.draft.scannedItemId, it) },
                )
            }
        }
    }
}

@Composable
private fun ListingDraftCard(
    draftState: ListingDraftState,
    onTitleChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onConditionChanged: (ListingCondition) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                draftState.draft.originalItem.thumbnail?.toImageBitmap()?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.cd_item_thumbnail),
                        modifier =
                            Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = draftState.draft.title,
                        onValueChange = onTitleChanged,
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = String.format("%.2f", draftState.draft.price),
                        onValueChange = onPriceChanged,
                        label = { Text("Price (${draftState.draft.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ConditionPicker(
                condition = draftState.draft.condition,
                onConditionChanged = onConditionChanged,
            )

            PostingStatusRow(draftState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionPicker(
    condition: ListingCondition,
    onConditionChanged: (ListingCondition) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = condition.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Condition") },
            modifier =
                Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ListingCondition.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onConditionChanged(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PostingStatusRow(draftState: ListingDraftState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Status: ${draftState.status.name}")
        when (draftState.status) {
            PostingStatus.POSTING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            PostingStatus.SUCCESS ->
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.cd_posting_success),
                    tint = MaterialTheme.colorScheme.primary,
                )
            PostingStatus.FAILURE ->
                Icon(
                    Icons.Default.Error,
                    contentDescription = stringResource(R.string.cd_posting_failed),
                    tint = MaterialTheme.colorScheme.error,
                )
            else -> {}
        }
    }
}
