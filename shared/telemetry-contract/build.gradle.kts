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
            baseName = "ScaniumTelemetryContract"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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
    namespace = "com.scanium.telemetry.contract"
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
