// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("com.android.library") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
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
