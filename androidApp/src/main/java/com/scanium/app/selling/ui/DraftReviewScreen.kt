package com.scanium.app.selling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.di.DraftReviewViewModelFactoryEntryPoint
import com.scanium.app.listing.DraftFieldKey
import com.scanium.app.listing.DraftStatus
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraft
import com.scanium.app.listing.ListingDraftFormatter
import com.scanium.app.model.toImageBitmap
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.items.ItemsViewModel
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.scanium.app.R
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.scanium.app.selling.util.ListingClipboardHelper
import com.scanium.app.selling.util.ListingShareHelper
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.foundation.layout.Box

/**
 * Screen for reviewing and editing listing drafts.
 *
 * Part of ARCH-001/DX-003: Updated to use Hilt's assisted injection for ViewModel creation,
 * reducing boilerplate by accessing the factory through EntryPoints.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftReviewScreen(
    itemIds: List<String>,
    onBack: () -> Unit,
    itemsViewModel: ItemsViewModel,
    draftStore: ListingDraftStore,
    onOpenPostingAssist: (List<String>, Int) -> Unit,
    onOpenAssistant: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val assistedFactory = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DraftReviewViewModelFactoryEntryPoint::class.java
        ).draftReviewViewModelFactory()
    }
    val viewModel: DraftReviewViewModel = viewModel(
        factory = DraftReviewViewModel.provideFactory(
            assistedFactory = assistedFactory,
            itemIds = itemIds,
            itemsViewModel = itemsViewModel
        )
    )

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val items by itemsViewModel.items.collectAsState()
    var moreExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Draft review") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveDraft()
                        onBack()
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveDraft()
                            onOpenPostingAssist(state.itemIds, state.currentIndex)
                        },
                        enabled = state.draft != null
                    ) {
                        Text("Posting Assist")
                    }
                    TextButton(
                        onClick = {
                            viewModel.saveDraft()
                            val itemId = state.itemIds.getOrNull(state.currentIndex)?.let { listOf(it) } ?: state.itemIds
                            onOpenAssistant(itemId)
                        },
                        enabled = state.draft != null
                    ) {
                        Text("Ask Assistant")
                    }
                    IconButton(onClick = { viewModel.saveDraft() }, enabled = state.draft != null) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save draft")
                    }
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy all") },
                            onClick = {
                                moreExpanded = false
                                val draft = state.draft
                                if (draft != null) {
                                    val selectedProfile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
                                        ?: ExportProfiles.generic()
                                    val export = ListingDraftFormatter.format(draft, selectedProfile)
                                    ListingClipboardHelper.copy(context, "Listing package", export.clipboardText)
                                    scope.launch { snackbarHostState.showSnackbar("Listing copied") }
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar("No draft to copy") }
                                }
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val draft = state.draft
            FloatingActionButton(
                onClick = {
                    if (draft == null) {
                        scope.launch { snackbarHostState.showSnackbar("No draft to share") }
                        return@FloatingActionButton
                    }
                    scope.launch {
                        val currentItem = items.firstOrNull { it.id == draft.itemId }
                        val selectedProfile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
                            ?: ExportProfiles.generic()
                        val export = ListingDraftFormatter.format(draft, selectedProfile)
                        val shareImages = draft.photos.map { it.image }.ifEmpty {
                            listOfNotNull(currentItem?.thumbnailRef ?: currentItem?.thumbnail)
                        }
                        val imageUris = ListingShareHelper.writeShareImages(
                            context = context,
                            itemId = draft.itemId,
                            images = shareImages
                        )
                        if (shareImages.isNotEmpty() && imageUris.isEmpty()) {
                            snackbarHostState.showSnackbar("Unable to attach images; sharing text only")
                        } else if (shareImages.isEmpty()) {
                            snackbarHostState.showSnackbar("No photos available; sharing text only")
                        }
                        val intent = ListingShareHelper.buildShareIntent(
                            contentResolver = context.contentResolver,
                            text = export.shareText,
                            imageUris = imageUris
                        )
                        val chooser = Intent.createChooser(intent, "Share listing")
                        context.startActivity(chooser)
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Share listing")
            }
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
                Icon(imageVector = Icons.Default.Warning, contentDescription = stringResource(R.string.cd_no_draft_available))
                Text("No draft available for this item")
            }
            return@Scaffold
        }

        val selectedProfile = remember(state.selectedProfileId, state.profiles) {
            state.profiles.firstOrNull { it.id == state.selectedProfileId } ?: ExportProfiles.generic()
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (state.totalCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.currentIndex + 1}/${state.totalCount}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = viewModel::goToPrevious,
                                enabled = state.currentIndex > 0
                            ) {
                                Text("Prev")
                            }
                            TextButton(
                                onClick = viewModel::goToNext,
                                enabled = state.currentIndex < state.totalCount - 1
                            ) {
                                Text("Next")
                            }
                        }
                    }
                }
            }

            item {
                ExportProfileSelector(
                    profiles = state.profiles,
                    selectedProfileId = state.selectedProfileId,
                    onProfileSelected = viewModel::selectProfile
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = draft.title.value.orEmpty().ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                val text = draft.title.value.orEmpty()
                                if (text.isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("No title to copy") }
                                } else {
                                    ListingClipboardHelper.copy(context, "Listing title", text)
                                    scope.launch { snackbarHostState.showSnackbar("Title copied") }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy title")
                        }
                    }
                }
            }

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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Description", style = MaterialTheme.typography.titleMedium)
                            IconButton(
                                onClick = {
                                    val text = draft.description.value.orEmpty()
                                    if (text.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("No description to copy") }
                                    } else {
                                        ListingClipboardHelper.copy(context, "Listing description", text)
                                        scope.launch { snackbarHostState.showSnackbar("Description copied") }
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy description")
                            }
                        }
                        OutlinedTextField(
                            value = draft.description.value.orEmpty(),
                            onValueChange = viewModel::updateDescription,
                            label = { Text("Edit description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4
                        )
                    }
                }
            }

            item {
                ConditionSelector(
                    condition = draft.fields[DraftFieldKey.CONDITION]?.value.orEmpty(),
                    onConditionChange = viewModel::updateCondition
                )
            }

            item {
                OutlinedTextField(
                    value = formatPrice(draft.price.value),
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

@Composable
private fun ExportProfileSelector(
    profiles: List<com.scanium.app.listing.ExportProfileDefinition>,
    selectedProfileId: com.scanium.app.listing.ExportProfileId,
    onProfileSelected: (com.scanium.app.listing.ExportProfileId) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = profiles.ifEmpty { listOf(ExportProfiles.generic()) }
    val selected = options.firstOrNull { it.id == selectedProfileId } ?: ExportProfiles.generic()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Export profile", style = MaterialTheme.typography.labelLarge)
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
                Text(selected.displayName)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.displayName) },
                    onClick = {
                        expanded = false
                        onProfileSelected(profile.id)
                    }
                )
            }
        }
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

private fun formatPrice(value: Double?): String {
    if (value == null) return ""
    val formatter = java.text.DecimalFormat("0.***REMOVED******REMOVED***")
    formatter.maximumFractionDigits = 2
    formatter.minimumFractionDigits = 0
    return formatter.format(value)
}
