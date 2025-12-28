// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // IMPORTANT: When updating AGP, ensure OWASP Dependency-Check plugin in androidApp/build.gradle.kts
    // remains compatible. CI workflow (.github/workflows/security-cve-scan.yml) validates this.
    // See: https://github.com/dependency-check/dependency-check-gradle
    id("com.android.application") version "8.5.0" apply false
    id("com.android.library") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.7.4" apply false
    // Hilt DI framework (ARCH-001)
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    // ktlint for code style enforcement (DX-002)
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// ktlint configuration (DX-002)
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.5.0")
    android.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    if (path != ":androidApp") {
        configurations.configureEach {
            withDependencies {
                filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                    .firstOrNull { it.dependencyProject.path == ":androidApp" }
                    ?.let {
                        throw GradleException("$path must not depend on :androidApp to keep module layering clean")
                    }
            }
        }
    }
}

tasks.register("checkPortableModules") {
    description = "Verifies core-models and core-tracking have no Android platform imports"
    group = "verification"

    doLast {
        val portableModules = listOf("core-models", "core-tracking")
        val forbiddenImports = listOf("android.graphics", "android.util")
        val violations = mutableListOf<String>()

        portableModules.forEach { moduleName ->
            val moduleDir = file(moduleName)
            if (moduleDir.exists()) {
                fileTree(moduleDir) {
                    include("**/*.kt")
                }.forEach { sourceFile ->
                    val content = sourceFile.readText()
                    forbiddenImports.forEach { forbidden ->
                        if (content.contains("import $forbidden")) {
                            violations.add("${sourceFile.relativeTo(rootDir)}: import $forbidden.*")
                        }
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Portability violation: core modules must not import Android platform types.\n" +
                "Found forbidden imports:\n  ${violations.joinToString("\n  ")}\n" +
                "See CLAUDE.md 'Shared Code Rules' for guidance."
            )
        }

        println("✓ Portability check passed: core-models and core-tracking are Android-free")
    }
}

tasks.register("checkNoLegacyImports") {
    description = "Fails build if legacy com.scanium.app.* imports are used after KMP migration"
    group = "verification"

    doLast {
        val legacyPatterns = listOf(
            "import com.scanium.app.tracking.ObjectTracker",
            "import com.scanium.app.aggregation",
            "import com.scanium.app.items.ScannedItem",
            "import com.scanium.app.model.NormalizedRect",
            "import com.scanium.app.ml.ItemCategory"
        )

        val offendingFiles = fileTree("androidApp/src") {
            include("**/*.kt")
        }.mapNotNull { sourceFile ->
            val matchedImports = legacyPatterns.filter { pattern ->
                sourceFile.readText().contains(pattern)
            }

            if (matchedImports.isNotEmpty()) {
                "${sourceFile.relativeTo(rootDir)} -> ${matchedImports.joinToString(", ")}"
            } else {
                null
            }
        }

        if (offendingFiles.isNotEmpty()) {
            throw GradleException(
                "Found legacy com.scanium.app.* imports in Android sources:\n" +
                    offendingFiles.joinToString("\n") +
                    "\nUpdate imports to shared KMP modules (com.scanium.core.*)."
            )
        }

        println("✓ No legacy com.scanium.app.* imports found in androidApp sources")
    }
}

tasks.register("prePushJvmCheck") {
    description = "Lightweight JVM-only validation for shared modules (no Android SDK required)"
    group = "verification"

    dependsOn(
        ":shared:core-models:jvmTest",
        ":shared:core-tracking:jvmTest",
        ":shared:test-utils:jvmTest",
        "checkPortableModules",
        "checkNoLegacyImports"
    )

    doLast {
        println("""
            ✓ Pre-push JVM checks passed:
              - JVM tests for shared modules
              - Portability checks
              - Legacy import checks
        """.trimIndent())
    }
}
