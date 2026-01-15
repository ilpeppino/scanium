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
            baseName = "ScaniumTelemetry"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:telemetry-contract"))
            api(project(":shared:diagnostics"))
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}

// Android configuration
android {
    namespace = "com.scanium.telemetry.facade"
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
