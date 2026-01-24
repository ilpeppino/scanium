package com.scanium.app.classification.hypothesis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanium.app.R

/**
 * Dialog shown when user taps "None of these" on hypothesis selection.
 * Allows user to specify the correct category and provide optional notes.
 */
@Composable
fun CorrectionDialog(
    itemId: String,
    imageHash: String,
    predictedCategory: String?,
    predictedConfidence: Float?,
    onDismiss: () -> Unit,
    onCorrectionSubmitted: (correctedCategory: String, notes: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var correctedCategory by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.correction_dialog_title))
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Explanation
                Text(stringResource(R.string.correction_dialog_explanation))

                // Category input
                OutlinedTextField(
                    value = correctedCategory,
                    onValueChange = { correctedCategory = it },
                    label = { Text(stringResource(R.string.correction_dialog_category_label)) },
                    placeholder = { Text(stringResource(R.string.correction_dialog_category_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Optional notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.correction_dialog_notes_label)) },
                    placeholder = { Text(stringResource(R.string.correction_dialog_notes_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                // Show predicted category if available
                if (predictedCategory != null) {
                    Text(
                        text =
                            stringResource(
                                R.string.correction_dialog_predicted,
                                predictedCategory,
                                (predictedConfidence?.times(100))?.toInt() ?: 0,
                            ),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (correctedCategory.isNotBlank()) {
                        onCorrectionSubmitted(
                            correctedCategory.trim(),
                            notes.trim().takeIf { it.isNotEmpty() },
                        )
                    }
                },
                enabled = correctedCategory.isNotBlank(),
            ) {
                Text(stringResource(R.string.correction_dialog_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.correction_dialog_cancel))
            }
        },
        modifier = modifier,
    )
}
