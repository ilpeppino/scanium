package com.scanium.app.items.edit.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.scanium.app.catalog.CatalogSearchResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogAutocompleteField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<CatalogSearchResult>,
    onQueryChange: (String) -> Unit,
    onSuggestionSelected: (CatalogSearchResult) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    isError: Boolean = false,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var allowAutoExpand by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val canExpand = isFocused && suggestions.isNotEmpty() && value.isNotBlank()
    val fieldBringIntoViewModifier =
        if (bringIntoViewRequester != null) {
            Modifier.bringIntoViewRequester(bringIntoViewRequester)
        } else {
            Modifier
        }

    LaunchedEffect(canExpand, allowAutoExpand) {
        if (allowAutoExpand) {
            expanded = canExpand
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            scope.launch { bringIntoViewRequester?.bringIntoView() }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { shouldExpand ->
            if (!shouldExpand) {
                allowAutoExpand = false
            }
            expanded = shouldExpand && canExpand
            if (expanded) {
                scope.launch { bringIntoViewRequester?.bringIntoView() }
            }
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                allowAutoExpand = true
                onValueChange(it)
                onQueryChange(it)
                expanded = isFocused && suggestions.isNotEmpty() && it.isNotBlank() && allowAutoExpand
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .then(fieldBringIntoViewModifier)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        allowAutoExpand = true
                        expanded =
                            it.isFocused &&
                                suggestions.isNotEmpty() &&
                                value.isNotBlank() &&
                                allowAutoExpand
                        if (it.isFocused) {
                            scope.launch { bringIntoViewRequester?.bringIntoView() }
                        }
                    },
            label = { Text(label) },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onClear()
                            onQueryChange("")
                            allowAutoExpand = false
                            expanded = false
                        },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            isError = isError,
            keyboardOptions =
                KeyboardOptions(
                    imeAction = imeAction,
                    capitalization = KeyboardCapitalization.Words,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        expanded = false
                        onNext()
                    },
                    onDone = { expanded = false },
                ),
            singleLine = true,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                allowAutoExpand = false
                expanded = false
            },
        ) {
            suggestions.forEach { result ->
                DropdownMenuItem(
                    text = { Text(result.entry.displayLabel) },
                    onClick = {
                        allowAutoExpand = false
                        onSuggestionSelected(result)
                        onQueryChange("")
                        expanded = false
                    },
                )
            }
        }
    }
}
