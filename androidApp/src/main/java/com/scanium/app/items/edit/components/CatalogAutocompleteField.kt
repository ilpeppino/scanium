package com.scanium.app.items.edit.components

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import com.scanium.app.catalog.CatalogSearchResult

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
) {
    var isFocused by remember { mutableStateOf(false) }
    val expanded = isFocused && suggestions.isNotEmpty() && value.isNotBlank()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {},
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                onQueryChange(it)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .onFocusChanged { isFocused = it.isFocused },
            label = { Text(label) },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(
                        onClick = {
                            onClear()
                            onQueryChange("")
                        },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            isError = isError,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            singleLine = true,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {},
        ) {
            suggestions.forEach { result ->
                DropdownMenuItem(
                    text = { Text(result.entry.displayLabel) },
                    onClick = {
                        onSuggestionSelected(result)
                        onQueryChange(result.entry.displayLabel)
                    },
                )
            }
        }
    }
}
