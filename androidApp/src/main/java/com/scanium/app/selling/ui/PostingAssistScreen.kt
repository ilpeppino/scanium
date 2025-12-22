package com.scanium.app.selling.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanium.app.data.ExportProfilePreferences
import com.scanium.app.data.PostingTargetPreferences
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.listing.ExportProfiles
import com.scanium.app.listing.ListingDraftFormatter
import com.scanium.app.listing.PostingAssistPlanBuilder
import com.scanium.app.listing.PostingStep
import com.scanium.app.listing.PostingStepId
import com.scanium.app.selling.export.AssetExportProfileRepository
import com.scanium.app.selling.persistence.ListingDraftStore
import com.scanium.app.selling.posting.PostingTargetDefaults
import com.scanium.app.selling.util.ListingClipboardHelper
import com.scanium.app.selling.util.ListingShareHelper
import kotlinx.coroutines.launch

@Composable
fun PostingAssistScreen(
    itemIds: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
    itemsViewModel: ItemsViewModel,
    draftStore: ListingDraftStore
) {
    val context = LocalContext.current
    val profileRepository = remember { AssetExportProfileRepository(context) }
    val profilePreferences = remember { ExportProfilePreferences(context) }
    val targetPreferences = remember { PostingTargetPreferences(context) }
    val viewModel: PostingAssistViewModel = viewModel(
        factory = PostingAssistViewModel.factory(
            itemIds = itemIds,
            startIndex = startIndex,
            itemsViewModel = itemsViewModel,
            draftStore = draftStore,
            exportProfileRepository = profileRepository,
            exportProfilePreferences = profilePreferences,
            postingTargetPreferences = targetPreferences
        )
    )

    val state by viewModel.uiState.collectAsState()
    val items by itemsViewModel.items.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var targetDialogVisible by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var highlightedStep by remember { mutableStateOf<PostingStepId?>(null) }

    val draft = state.draft
    val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId } ?: ExportProfiles.generic()
    val plan = state.plan

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Posting Assist")
                        Text(
                            text = buildSubtitle(profile.displayName, state),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { targetDialogVisible = true }) {
                        Icon(imageVector = Icons.Default.Launch, contentDescription = "Open target")
                    }
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy all") },
                            onClick = {
                                moreExpanded = false
                                if (draft != null) {
                                    val export = ListingDraftFormatter.format(draft, profile)
                                    ListingClipboardHelper.copy(context, "Listing package", export.clipboardText)
                                    Log.d("PostingAssist", "step copied: all")
                                    scope.launch { snackbarHostState.showSnackbar("Listing copied") }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change target") },
                            onClick = {
                                moreExpanded = false
                                targetDialogVisible = true
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ActionRow(
                onCopyNext = {
                    val nextId = viewModel.copyNextStepId()
                    val step = plan?.steps?.firstOrNull { it.id == nextId } ?: plan?.steps?.firstOrNull()
                    if (step != null) {
                        copyStep(step, listState, context, snackbarHostState, scope, plan) { highlightedStep = it }
                        Log.d("PostingAssist", "copy next: ${step.id}")
                    }
                },
                onCopyAll = {
                    if (draft != null) {
                        val export = ListingDraftFormatter.format(draft, profile)
                        ListingClipboardHelper.copy(context, "Listing package", export.clipboardText)
                        scope.launch { snackbarHostState.showSnackbar("Listing copied") }
                    }
                },
                onShare = {
                    if (draft == null) {
                        scope.launch { snackbarHostState.showSnackbar("No draft to share") }
                        return@ActionRow
                    }
                    scope.launch {
                        Log.d("PostingAssist", "share invoked")
                        val currentItem = items.firstOrNull { it.id == draft.itemId }
                        val export = ListingDraftFormatter.format(draft, profile)
                        val shareImages = draft.photos.map { it.image }.ifEmpty {
                            listOfNotNull(currentItem?.thumbnailRef ?: currentItem?.thumbnail)
                        }
                        val imageUris = ListingShareHelper.writeShareImages(
                            context = context,
                            itemId = draft.itemId,
                            images = shareImages
                        )
                        val intent = ListingShareHelper.buildShareIntent(
                            contentResolver = context.contentResolver,
                            text = export.shareText,
                            imageUris = imageUris
                        )
                        val chooser = Intent.createChooser(intent, "Share listing")
                        context.startActivity(chooser)
                    }
                }
            )
        }
    ) { padding ->
        if (draft == null || plan == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No draft available")
            }
            return@Scaffold
        }

        PostingAssistContent(
            modifier = Modifier.padding(padding),
            plan = plan,
            profileName = profile.displayName,
            listState = listState,
            highlightedStep = highlightedStep,
            onCopyStep = { step ->
                copyStep(step, listState, context, snackbarHostState, scope, plan) { highlightedStep = it }
                Log.d("PostingAssist", "step copied: ${step.id}")
            },
            onProfileClick = { viewModel.selectProfile(it) },
            profiles = state.profiles,
            selectedProfileId = state.selectedProfileId,
            currentIndex = state.currentIndex,
            totalCount = state.totalCount,
            onPrev = viewModel::goToPrevious,
            onNext = viewModel::goToNext,
            missing = plan.missingRequired
        )
    }

    if (targetDialogVisible) {
        TargetDialog(
            targets = state.targets,
            selectedTargetId = state.selectedTargetId,
            customTargetValue = state.customTargetValue.orEmpty(),
            onDismiss = { targetDialogVisible = false },
            onTargetSelected = {
                viewModel.selectTarget(it)
                targetDialogVisible = false
            },
            onCustomValueChanged = {
                viewModel.updateCustomTarget(it)
                targetDialogVisible = false
            },
            onOpenTarget = {
                viewModel.currentTarget()?.let { target ->
                    openTarget(context, target, snackbarHostState, scope)
                }
                targetDialogVisible = false
            }
        )
    }
}

@Composable
private fun PostingAssistContent(
    modifier: Modifier,
    plan: com.scanium.app.listing.PostingAssistPlan,
    profileName: String,
    listState: LazyListState,
    highlightedStep: PostingStepId?,
    onCopyStep: (PostingStep) -> Unit,
    onProfileClick: (com.scanium.app.listing.ExportProfileId) -> Unit,
    profiles: List<com.scanium.app.listing.ExportProfileDefinition>,
    selectedProfileId: com.scanium.app.listing.ExportProfileId,
    currentIndex: Int,
    totalCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    missing: List<PostingStepId>
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = profileName, style = MaterialTheme.typography.titleMedium)
                    if (totalCount > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onPrev, enabled = currentIndex > 0) { Text("Prev") }
                            Text("${currentIndex + 1}/$totalCount", style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = onNext, enabled = currentIndex < totalCount - 1) { Text("Next") }
                        }
                    }
                }
                ProfileSelector(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = onProfileClick
                )
                ProgressHeader(plan = plan, missing = missing)
            }
        }

        items(plan.steps) { step ->
            StepCard(step = step, highlighted = highlightedStep == step.id, onCopy = { onCopyStep(step) })
        }
        item { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
private fun ProfileSelector(
    profiles: List<com.scanium.app.listing.ExportProfileDefinition>,
    selectedProfileId: com.scanium.app.listing.ExportProfileId,
    onProfileSelected: (com.scanium.app.listing.ExportProfileId) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = profiles.ifEmpty { listOf(ExportProfiles.generic()) }
    val selected = options.firstOrNull { it.id == selectedProfileId } ?: ExportProfiles.generic()

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
            Text(selected.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun ProgressHeader(plan: com.scanium.app.listing.PostingAssistPlan, missing: List<PostingStepId>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Completeness", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = plan.completenessScore / 100f, modifier = Modifier.fillMaxWidth())
            Text("${plan.completenessScore}% complete")
            if (missing.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    missing.forEach { id ->
                        AssistChip(onClick = {}, label = { Text(id.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: PostingStep, highlighted: Boolean, onCopy: () -> Unit) {
    val background = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(step.label, style = MaterialTheme.typography.titleMedium)
                    if (step.isRequired) {
                        Text("•", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(step.value.ifBlank { "" }, style = MaterialTheme.typography.bodyMedium)
                if (!step.isComplete && step.isRequired) {
                    Text("Required", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onCopy) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy ${step.label}")
            }
        }
    }
}

@Composable
private fun ActionRow(
    onCopyNext: () -> Unit,
    onCopyAll: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onCopyNext, modifier = Modifier.weight(1f)) {
            Text("Copy Next")
        }
        Button(onClick = onCopyAll, modifier = Modifier.weight(1f)) {
            Text("Copy All")
        }
        Button(onClick = onShare, modifier = Modifier.weight(1f)) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.size(4.dp))
            Text("Share")
        }
    }
}

@Composable
private fun TargetDialog(
    targets: List<com.scanium.app.selling.posting.PostingTarget>,
    selectedTargetId: String,
    customTargetValue: String,
    onDismiss: () -> Unit,
    onTargetSelected: (String) -> Unit,
    onCustomValueChanged: (String) -> Unit,
    onOpenTarget: () -> Unit
) {
    var customValue by remember { mutableStateOf(customTargetValue) }
    var currentSelection by remember { mutableStateOf(selectedTargetId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (currentSelection == PostingTargetDefaults.CUSTOM_TARGET_ID) {
                    onCustomValueChanged(customValue)
                } else {
                    onTargetSelected(currentSelection)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = {
                onOpenTarget()
            }) { Text("Open target") }
        },
        title = { Text("Posting target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(target.label)
                        TextButton(onClick = { currentSelection = target.id }) {
                            Text(if (currentSelection == target.id) "Selected" else "Select")
                        }
                    }
                }
                TextField(
                    value = customValue,
                    onValueChange = {
                        customValue = it
                        currentSelection = PostingTargetDefaults.CUSTOM_TARGET_ID
                    },
                    label = { Text("Custom URL") }
                )
            }
        }
    )
}

private fun buildSubtitle(profileName: String, state: PostingAssistUiState): String {
    val indexPart = if (state.totalCount > 1) " • ${state.currentIndex + 1}/${state.totalCount}" else ""
    return "$profileName$indexPart"
}

private fun copyStep(
    step: PostingStep,
    listState: LazyListState,
    context: android.content.Context,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    plan: com.scanium.app.listing.PostingAssistPlan,
    onHighlightedChange: (PostingStepId) -> Unit
) {
    if (step.value.isBlank()) {
        scope.launch { snackbarHostState.showSnackbar("No ${step.label.lowercase()} to copy") }
        return
    }
    ListingClipboardHelper.copy(context, step.label, step.value)
    onHighlightedChange(step.id)
    scope.launch {
        val index = plan.steps.indexOfFirst { it.id == step.id }.coerceAtLeast(0)
        listState.animateScrollToItem(index)
        snackbarHostState.showSnackbar("${step.label} copied")
    }
}

private fun openTarget(
    context: android.content.Context,
    target: com.scanium.app.selling.posting.PostingTarget,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val uri = Uri.parse(target.value.ifBlank { "https://www.google.com" })
    val intent = when (target.type) {
        com.scanium.app.selling.posting.PostingTargetType.URL, com.scanium.app.selling.posting.PostingTargetType.DEEPLINK ->
            Intent(Intent.ACTION_VIEW, uri)
        com.scanium.app.selling.posting.PostingTargetType.APP ->
            context.packageManager.getLaunchIntentForPackage(target.value)
    }
    val resolved = intent?.takeIf { it.resolveActivity(context.packageManager) != null }
        ?: Intent(Intent.ACTION_VIEW, uri)
    if (resolved.resolveActivity(context.packageManager) != null) {
        context.startActivity(resolved)
    } else {
        scope.launch { snackbarHostState.showSnackbar("Unable to open target") }
    }
}
