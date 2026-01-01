package com.scanium.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
                    modifier = Modifier.weight(1f, fill = false),
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
