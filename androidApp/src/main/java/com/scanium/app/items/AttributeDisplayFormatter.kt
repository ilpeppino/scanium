package com.scanium.app.items

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Locale

object AttributeDisplayFormatter {
    private val capitalizedKeys =
        setOf(
            "brand",
            "itemType",
            "model",
            "color",
            "secondaryColor",
            "material",
            "size",
            "condition",
        )

    fun shouldCapitalize(attributeKey: String?): Boolean = attributeKey != null && attributeKey in capitalizedKeys

    fun formatForDisplay(
        context: Context,
        attributeKey: String?,
        value: String,
    ): String {
        if (!shouldCapitalize(attributeKey)) {
            return value
        }
        return formatForDisplay(context, value)
    }

    fun visualTransformation(
        context: Context,
        attributeKey: String?,
    ): VisualTransformation {
        if (!shouldCapitalize(attributeKey)) {
            return VisualTransformation.None
        }
        return VisualTransformation { text ->
            val transformed = formatForDisplay(context, text.text)
            TransformedText(AnnotatedString(transformed), OffsetMapping.Identity)
        }
    }

    private fun formatForDisplay(
        context: Context,
        value: String,
    ): String {
        if (value.isEmpty()) {
            return value
        }
        val locales = context.resources.configuration.locales
        val locale = if (locales.isEmpty) Locale.getDefault() else locales[0]
        return value.replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(locale)
            } else {
                char.toString()
            }
        }
    }
}
