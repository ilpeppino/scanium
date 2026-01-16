package com.scanium.app.test

import org.junit.Test
import java.io.File
import kotlin.test.fail
import org.junit.Assert.fail as assertFail

/**
 * Regression test to ensure no hardcoded English user-visible strings are introduced in UI code.
 *
 * This test scans Kotlin source files in the selling/assistant UI packages to detect common
 * patterns of hardcoded strings that should be externalized to strings.xml resources.
 *
 * Scope: Checks only user-facing UI strings (Text(), labels, button text), not debug logging
 * or internal string operations.
 */
class NoHardcodedEnglishUiStringsTest {

    @Test
    fun `no hardcoded Text() strings in selling UI`() {
        val sourceDir = File("androidApp/src/main/java/com/scanium/app/selling")
        val violations = mutableListOf<String>()

        sourceDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "kt") {
                val content = file.readText()
                val lines = content.split("\n")

                lines.forEachIndexed { index, line ->
                    val lineNum = index + 1

                    // Check for Text("literal string") pattern - excludes stringResource()
                    // Negative lookbehind to exclude stringResource calls
                    if (line.contains("""Text\(["']""") &&
                        !line.contains("stringResource") &&
                        !line.contains("BuildConfig") &&
                        !line.contains("contentDescription") && // excludes String literals in other params
                        !line.contains("contentDescription = ") &&
                        !line.contains("text = ") // parameter assignment
                    ) {
                        // Allow some exceptions: variable assignments, test code, non-UI contexts
                        if (!line.trim().startsWith("//") && !isValidException(line)) {
                            violations.add("${file.path}:$lineNum - Found hardcoded Text(): $line")
                        }
                    }

                    // Check for common hardcoded strings that should be resources
                    val hardcodedPatterns = listOf(
                        "Text(\"Copy" to "button_copy",
                        "Text(\"Apply" to "button_apply",
                        "Text(\"Cancel" to "button_cancel",
                        "Text(\"Retry" to "button_retry",
                        "Text(\"Dismiss" to "button_dismiss",
                        "Text(\"Delete" to "common_delete",
                        "Text(\"Save" to "button_save",
                        "Text(\"Export" to "ebay_export",
                        "Text(\"Posting Assist" to "posting_assist",
                        "Text(\"Draft review" to "dialog_title_draft_review",
                        "Text(\"Completeness" to "label_completeness",
                        "Text(\"Required" to "posting_assist_required",
                        "Text(\"Category" to "form_label_category",
                        "Text(\"Title" to "form_label_title",
                        "Text(\"Price" to "ebay_export_price",
                        "Text(\"Description" to "form_label_description",
                        "Text(\"Condition" to "form_label_condition",
                        "Text(\"No draft" to "draft_review_no_draft"
                    )

                    hardcodedPatterns.forEach { (pattern, resourceKey) ->
                        if (line.contains(pattern) &&
                            !line.contains("stringResource") &&
                            !line.trim().startsWith("//") &&
                            !isValidException(line)
                        ) {
                            violations.add("${file.path}:$lineNum - Hardcoded '$pattern' should use $resourceKey")
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            val message = buildString {
                appendLine("Found ${violations.size} hardcoded English UI string(s) that should use string resources:")
                appendLine()
                violations.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Action: Replace hardcoded strings with stringResource(R.string.*) calls")
                appendLine("Reference: androidApp/src/main/res/values/strings.xml")
            }
            fail(message)
        }
    }

    @Test
    fun `no hardcoded strings in pricing formatters`() {
        val formatterFile = File("androidApp/src/main/java/com/scanium/app/copy/CustomerSafeCopyFormatter.kt")
        if (formatterFile.exists()) {
            val content = formatterFile.readText()
            val lines = content.split("\n")

            val violations = mutableListOf<String>()
            lines.forEachIndexed { index, line ->
                val lineNum = index + 1
                if (line.contains("\"Typical resale") ||
                    line.contains("\"Based on current") ||
                    line.contains("\"Item\"") && line.contains("fallback")
                ) {
                    if (!line.contains("stringResource") && !line.trim().startsWith("//")) {
                        violations.add("$lineNum: $line")
                    }
                }
            }

            if (violations.isNotEmpty()) {
                fail(
                    "Found hardcoded pricing strings in CustomerSafeCopyFormatter:\n" +
                        violations.joinToString("\n")
                )
            }
        }
    }

    @Test
    fun `no hardcoded strings in policy classes`() {
        val policyFile = File("androidApp/src/main/java/com/scanium/app/copy/CustomerSafeCopyPolicy.kt")
        if (policyFile.exists()) {
            val content = policyFile.readText()

            if (content.contains("\"Based on \$contextHint\"") && !content.contains("stringResource")) {
                fail(
                    "Found hardcoded 'Based on' string in CustomerSafeCopyPolicy that should use " +
                        "stringResource(R.string.pricing_based_on_conditions)"
                )
            }
        }
    }

    /**
     * Checks if a line is a valid exception to the no-hardcoded-strings rule.
     * Valid exceptions include:
     * - Comments
     * - String passed to contentDescription (accessibility, not user-visible UI)
     * - Parameter names/values in non-UI contexts
     * - Variable names (StringState, etc.)
     */
    private fun isValidException(line: String): Boolean {
        return line.trim().startsWith("//") ||
                line.contains("contentDescription") ||
                line.contains("BuildConfig") ||
                line.contains("\"\$") ||  // Template strings in logging
                line.contains("// ") ||   // Inline comments
                line.contains("tag = ") || // Logging tags
                line.contains("message = ") ||  // Error/log messages
                line.contains("BuildConfig") ||
                line.contains("\"Mock\"") || // Mock/debug specific strings
                line.contains("TAG")  // Logging TAG variables
    }
}
