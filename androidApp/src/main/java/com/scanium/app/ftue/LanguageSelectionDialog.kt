package com.scanium.app.ftue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scanium.app.R
import com.scanium.app.model.AppLanguage

/**
 * Language selection dialog shown during FTUE after camera permission is granted.
 * Allows users to choose their preferred app language with country flags and names.
 *
 * @param currentLanguage The currently selected language (defaults to SYSTEM)
 * @param onLanguageSelected Callback when user confirms language selection
 */
@Composable
fun LanguageSelectionDialog(
    currentLanguage: AppLanguage = AppLanguage.SYSTEM,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    var selectedLanguage by remember { mutableStateOf(currentLanguage) }

    Dialog(onDismissRequest = { /* Non-dismissible - user must select */ }) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title
                Text(
                    text = stringResource(R.string.ftue_language_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = stringResource(R.string.ftue_language_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Language options
                val languageOptions =
                    listOf(
                        LanguageOption(AppLanguage.SYSTEM, "🌐", stringResource(R.string.settings_language_system_default)),
                        LanguageOption(AppLanguage.EN, "🇬🇧", stringResource(R.string.settings_language_en)),
                        LanguageOption(AppLanguage.ES, "🇪🇸", stringResource(R.string.settings_language_es)),
                        LanguageOption(AppLanguage.IT, "🇮🇹", stringResource(R.string.settings_language_it)),
                        LanguageOption(AppLanguage.FR, "🇫🇷", stringResource(R.string.settings_language_fr)),
                        LanguageOption(AppLanguage.NL, "🇳🇱", stringResource(R.string.settings_language_nl)),
                        LanguageOption(AppLanguage.DE, "🇩🇪", stringResource(R.string.settings_language_de)),
                        LanguageOption(AppLanguage.PT_BR, "🇧🇷", stringResource(R.string.settings_language_pt_br)),
                    )

                languageOptions.forEach { option ->
                    LanguageOptionRow(
                        flag = option.flag,
                        name = option.name,
                        isSelected = selectedLanguage == option.language,
                        onClick = {
                            selectedLanguage = option.language
                            onLanguageSelected(option.language)
                        },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * Data class representing a language option with flag emoji and localized name.
 */
private data class LanguageOption(
    val language: AppLanguage,
    val flag: String,
    val name: String,
)

/**
 * A single selectable language option row.
 */
@Composable
private fun LanguageOptionRow(
    flag: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Flag emoji
        Text(
            text = flag,
            fontSize = 28.sp,
            modifier = Modifier.size(40.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Language name
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )

        // Radio button
        RadioButton(
            selected = isSelected,
            onClick = onClick,
        )
    }
}
