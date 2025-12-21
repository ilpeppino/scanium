package com.scanium.app.selling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.listing.DraftFieldKey
import com.scanium.app.listing.DraftStatus
import com.scanium.app.listing.ListingDraft
import com.scanium.app.model.toImageBitmap
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.items.ItemsViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftReviewScreen(
    itemId: String,
    onBack: () -> Unit,
    itemsViewModel: ItemsViewModel,
    draftStore: ListingDraftStore
) {
    val viewModel: DraftReviewViewModel = viewModel(
        factory = DraftReviewViewModel.factory(
            itemId = itemId,
            itemsViewModel = itemsViewModel,
            draftStore = draftStore
        )
    )

    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Draft review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveDraft() }, enabled = state.draft != null) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save draft")
                    }
                }
            )
        }
    ) { padding ->
        val draft = state.draft
        if (draft == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                Text("No draft available for this item")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DraftPhotoRow(draft)
            }

            item {
                OutlinedTextField(
                    value = draft.title.value.orEmpty(),
                    onValueChange = viewModel::updateTitle,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = draft.description.value.orEmpty(),
                    onValueChange = viewModel::updateDescription,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                ConditionSelector(
                    condition = draft.fields[DraftFieldKey.CONDITION]?.value.orEmpty(),
                    onConditionChange = viewModel::updateCondition
                )
            }

            item {
                OutlinedTextField(
                    value = draft.price.value?.toString().orEmpty(),
                    onValueChange = viewModel::updatePrice,
                    label = { Text("Price (EUR)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                KeyFieldsCard(draft)
            }

            item {
                CompletenessCard(draft)
            }
        }
    }
}

@Composable
private fun DraftPhotoRow(draft: ListingDraft) {
    val photo = draft.photos.firstOrNull()?.image?.toImageBitmap()
    photo?.let {
        Image(
            bitmap = it,
            contentDescription = "Draft photo",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionSelector(condition: String, onConditionChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Used", "New", "Open box")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Condition", style = MaterialTheme.typography.labelLarge)
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
            colors = CardDefaults.cardColors()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(condition.ifBlank { "Select condition" })
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onConditionChange(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun KeyFieldsCard(draft: ListingDraft) {
    val fields = draft.fields
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Key fields", style = MaterialTheme.typography.titleMedium)
            Text("Category: ${fields[DraftFieldKey.CATEGORY]?.value ?: ""}")
            Text("Brand: ${fields[DraftFieldKey.BRAND]?.value ?: ""}")
            Text("Model: ${fields[DraftFieldKey.MODEL]?.value ?: ""}")
            Text("Color: ${fields[DraftFieldKey.COLOR]?.value ?: ""}")
        }
    }
}

@Composable
private fun CompletenessCard(draft: ListingDraft) {
    val completeness = draft.completeness
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Completeness", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = completeness.score / 100f,
                modifier = Modifier.fillMaxWidth()
            )
            Text("${completeness.score}% complete")
            if (completeness.missing.isNotEmpty()) {
                val missingText = completeness.missing.joinToString { it.name.lowercase() }
                Text("Missing: $missingText")
            }
            if (draft.status == DraftStatus.SAVED) {
                Text("Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
