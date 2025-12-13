package com.scanium.app.selling.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.items.ScannedItem
import com.scanium.app.selling.data.EbayMarketplaceService
import com.scanium.app.selling.domain.ListingCondition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellOnEbayScreen(
    onNavigateBack: () -> Unit,
    selectedItems: List<ScannedItem>,
    marketplaceService: EbayMarketplaceService,
    itemsViewModel: com.scanium.app.items.ItemsViewModel,
    viewModel: ListingViewModel = viewModel(
        factory = ListingViewModelFactory(selectedItems, marketplaceService, itemsViewModel)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sell on eBay (Mock)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = { viewModel.postSelectedToEbay() },
                    enabled = uiState.drafts.isNotEmpty() && !uiState.isPosting,
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                ) {
                    Text("Post to eBay (Mock)")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.drafts, key = { it.draft.scannedItemId }) { draftState ->
                ListingDraftCard(
                    draftState = draftState,
                    onTitleChanged = { viewModel.updateDraftTitle(draftState.draft.scannedItemId, it) },
                    onPriceChanged = { viewModel.updateDraftPrice(draftState.draft.scannedItemId, it) },
                    onConditionChanged = { viewModel.updateDraftCondition(draftState.draft.scannedItemId, it) }
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
    onConditionChanged: (ListingCondition) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                draftState.draft.originalItem.thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = draftState.draft.title,
                        onValueChange = onTitleChanged,
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draftState.draft.price.toString(),
                        onValueChange = onPriceChanged,
                        label = { Text("Price (${draftState.draft.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ConditionPicker(
                condition = draftState.draft.condition,
                onConditionChanged = onConditionChanged
            )

            PostingStatusRow(draftState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionPicker(
    condition: ListingCondition,
    onConditionChanged: (ListingCondition) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = condition.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Condition") },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ListingCondition.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onConditionChanged(option)
                        expanded = false
                    }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Status: ${draftState.status.name}")
        when (draftState.status) {
            PostingStatus.POSTING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            PostingStatus.SUCCESS -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            PostingStatus.FAILURE -> Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
