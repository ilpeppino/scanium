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

    val iosArm64Target = iosArm64() {
        binaries.framework {
            baseName = "ScaniumTelemetryContract"
            isStatic = true
        }
    }
    val iosSimulatorArm64Target = iosSimulatorArm64() {
        binaries.framework {
            baseName = "ScaniumTelemetryContract"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }

        iosArm64Target.compilations["main"].defaultSourceSet.dependsOn(iosMain)
        iosSimulatorArm64Target.compilations["main"].defaultSourceSet.dependsOn(iosMain)

        iosArm64Target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
        iosSimulatorArm64Target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
