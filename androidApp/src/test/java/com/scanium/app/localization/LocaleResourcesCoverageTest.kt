package com.scanium.app.localization

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression guard test that ensures all supported app languages have corresponding
 * Android locale resource directories with strings.xml files.
 *
 * This test fails if:
 * - A new language is added to AppLanguage enum without creating the corresponding resource folder
 * - An existing locale resource folder or strings.xml file is accidentally deleted
 *
 * When this test fails, run: bash scripts/localization/generate-placeholder-locales.sh
 */
@RunWith(RobolectricTestRunner::class)
class LocaleResourcesCoverageTest {
    private fun getProjectRoot(): File {
        val userDir = File(System.getProperty("user.dir"))
        return if (userDir.name == "scanium") {
            userDir
        } else {
            // Fallback: walk up to find the scanium directory
            var current = userDir
            while (current.parentFile != null) {
                if (current.name == "scanium") {
                    return current
                }
                current = current.parentFile!!
            }
            userDir
        }
    }

    private val baseResDir: File
        get() = File(getProjectRoot(), "androidApp/src/main/res")

    /**
     * Discover supported locales from AppLanguage.kt enum
     */
    private fun discoverSupportedLocales(): Set<String> {
        val projectRoot = getProjectRoot()
        val appLanguageFile = File(projectRoot, "androidApp/src/main/java/com/scanium/app/model/AppLanguage.kt")
        if (!appLanguageFile.exists()) {
            fail("AppLanguage.kt not found at $appLanguageFile")
        }

        val content = appLanguageFile.readText()
        val regex = """"([^"]+)"""".toRegex()

        return regex.findAll(content)
            .map { it.groupValues[1] }
            .filter { it.isNotEmpty() }
            .filter { it != "system" } // system is a pseudo-locale, not a real language
            .toSet()
    }

    /**
     * Map BCP-47 locale tag to Android resource qualifier
     */
    private fun getBcpToAndroidQualifier(locale: String): String? =
        when (locale) {
            "en" -> "values"
            "es" -> "values-es"
            "it" -> "values-it"
            "fr" -> "values-fr"
            "nl" -> "values-nl"
            "de" -> "values-de"
            "pt-BR" -> "values-pt-rBR"
            else -> null
        }

    @Test
    fun allSupportedLocalesHaveResourceFolders() {
        val locales = discoverSupportedLocales()
        assertTrue("No supported locales discovered from AppLanguage.kt", locales.isNotEmpty())

        for (locale in locales) {
            val qualifier = getBcpToAndroidQualifier(locale)
            assertNotNull("Unknown BCP-47 locale: $locale", qualifier)

            if (qualifier == "values") {
                // Base English locale, skip
                continue
            }

            val resDir = File(baseResDir, qualifier!!)
            assertTrue(
                "Locale resource directory missing: $qualifier (for locale $locale) at $resDir. " +
                    "Run: bash scripts/localization/generate-placeholder-locales.sh",
                resDir.exists() && resDir.isDirectory,
            )

            val stringsFile = File(resDir, "strings.xml")
            assertTrue(
                "Strings file missing: $qualifier/strings.xml (for locale $locale). " +
                    "Run: bash scripts/localization/generate-placeholder-locales.sh",
                stringsFile.exists() && stringsFile.isFile,
            )
        }
    }

    @Test
    fun noUnmappedLocaleResourceFolders() {
        // Verify that all values-XX directories correspond to a supported locale
        val supportedLocales = discoverSupportedLocales()
        val supportedQualifiers =
            supportedLocales
                .mapNotNull { getBcpToAndroidQualifier(it) }
                .toSet()

        val resDirectories =
            baseResDir.listFiles()?.filter {
                it.isDirectory && it.name.startsWith("values-")
            } ?: emptyList()

        for (resDir in resDirectories) {
            val isNightMode = resDir.name == "values-night"
            val isApiLevel = resDir.name == "values-v31" || resDir.name.matches(Regex("values-v\\d+"))

            if (!isNightMode && !isApiLevel) {
                assertTrue(
                    "Orphaned locale resource folder found: ${resDir.name}. " +
                        "This folder does not correspond to any supported app language. " +
                        "Supported locales: $supportedLocales",
                    resDir.name in supportedQualifiers,
                )
            }
        }
    }
}
