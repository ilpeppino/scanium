package com.scanium.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner for Hilt instrumented tests.
 * Part of ARCH-001: Hilt DI migration.
 *
 * To use this runner, update testInstrumentationRunner in build.gradle.kts:
 * testInstrumentationRunner = "com.scanium.app.HiltTestRunner"
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
    }
}
