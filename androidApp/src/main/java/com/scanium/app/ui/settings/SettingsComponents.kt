package com.scanium.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import kotlinx.coroutines.launch

@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .alpha(if (enabled) 1f else 0.5f)

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(text = it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = title) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        modifier = rowModifier,
    )
}

@Composable
fun SettingNavigationRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    enabled: Boolean = true,
) {
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = title) } },
        trailingContent =
            if (showChevron && enabled) {
                { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_navigate_forward)) }
            } else {
                null
            },
        modifier = rowModifier,
    )
}

@Composable
fun SettingActionRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = title) } },
        modifier = rowModifier,
    )
}

data class SegmentOption<T>(
    val value: T,
    val label: String,
    val description: String? = null,
)

@Composable
fun <T> SettingSegmentedRow(
    title: String,
    subtitle: String? = null,
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val selectedState = option.value == selected
                FilterChip(
                    selected = selectedState,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        options.firstOrNull { it.value == selected }?.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingDropdownRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box {
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = {
                    Column {
                        subtitle?.let { Text(it) }
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                leadingContent = icon?.let { { Icon(it, contentDescription = title) } },
                trailingContent = { Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.cd_expand_menu)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.Start)
                        .alpha(if (enabled) 1f else 0.5f)
                        .then(
                            if (enabled) {
                                Modifier.clickable { expanded = true }
                            } else {
                                Modifier
                            },
                        ),
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onOptionSelected(value)
                        },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

/**
 * Settings row with icon, title, description, and segmented button controls.
 * Provides a consistent layout matching other settings rows:
 * - Leading icon (24dp)
 * - Title and description
 * - Segmented controls in a row below (equal width buttons)
 * - Selected option description at the bottom
 */
@Composable
fun <T> SettingIconSegmentedRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header row with icon, title, and subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Segmented controls row with equal-width buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val selectedState = option.value == selected
                FilterChip(
                    selected = selectedState,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Selected option description
        options.firstOrNull { it.value == selected }?.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Data class representing a selectable option for ValuePickerSettingRow.
 *
 * @param value The value of this option
 * @param label The display label for this option
 * @param description Optional description text shown below the label in the picker
 * @param isRecommended Whether this option should be marked as "Recommended"
 */
data class SettingOption<T>(
    val value: T,
    val label: String,
    val description: String? = null,
    val isRecommended: Boolean = false,
)

/**
 * A reusable settings row that displays a current value and opens a modal bottom sheet
 * to select from a list of options. This replaces inline segmented controls and dropdowns.
 *
 * Requirements from spec:
 * - Single-row showing current value on the right with chevron
 * - Tapping opens a modal bottom sheet to select the value
 * - Bottom sheet shows per-option helper text and indicates "Recommended"
 * - Consistent layout: icon left, title + description middle, value + chevron right
 *
 * @param title The setting title
 * @param subtitle Optional description text shown below the title
 * @param icon Optional leading icon
 * @param currentValue The currently selected value
 * @param options List of selectable options
 * @param onValueSelected Callback when a new value is selected
 * @param enabled Whether the row is interactive
 * @param modalTitle Optional title for the bottom sheet (defaults to title)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ValuePickerSettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    currentValue: T,
    options: List<SettingOption<T>>,
    onValueSelected: (T) -> Unit,
    enabled: Boolean = true,
    modalTitle: String? = null,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Find the label for the current value
    val currentLabel = options.find { it.value == currentValue }?.label ?: ""

    // Main row
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button) { showSheet = true }
                } else {
                    Modifier
                },
            )

    ListItem(
        headlineContent = { Text(title) },
        supportingContent =
            subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        leadingContent = icon?.let { { Icon(it, contentDescription = title) } },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (enabled) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.cd_navigate_forward),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = rowModifier,
    )

    // Bottom sheet picker
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp),
            ) {
                // Sheet title
                Text(
                    text = modalTitle ?: title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )

                // Options list
                options.forEachIndexed { index, option ->
                    val isSelected = option.value == currentValue

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            onValueSelected(option.value)
                                            sheetState.hide()
                                            showSheet = false
                                        }
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    )
                                    if (option.isRecommended) {
                                        Text(
                                            text = stringResource(R.string.settings_recommended),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }

                                option.description?.let { desc ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = stringResource(R.string.cd_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    if (index < options.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
        }
    }
}
