plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.0.0"
    id("com.android.library")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    androidTarget()

    // JVM target for running tests without Android SDK (CI/container-friendly)
    jvm()

    // iOS targets
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ScaniumDiagnostics"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:telemetry-contract"))
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        // Configure iosMain to be shared across all iOS targets
        val iosMain by creating {
            dependsOn(commonMain.get())
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}

// Android configuration
android {
    namespace = "com.scanium.diagnostics"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

koverReport {
    defaults {
        verify {
            rule {
                bound { minValue = 85 }
            }
        }
    }
}
