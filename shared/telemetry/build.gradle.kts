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
    val iosX64Target =
        iosX64 {
            binaries.framework {
                baseName = "ScaniumTelemetry"
                isStatic = true
            }
        }
    val iosArm64Target =
        iosArm64 {
            binaries.framework {
                baseName = "ScaniumTelemetry"
                isStatic = true
            }
        }
    val iosSimulatorArm64Target =
        iosSimulatorArm64 {
            binaries.framework {
                baseName = "ScaniumTelemetry"
                isStatic = true
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":shared:telemetry-contract"))
                api(project(":shared:diagnostics"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
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

        iosX64Target.compilations["main"].defaultSourceSet.dependsOn(iosMain)
        iosArm64Target.compilations["main"].defaultSourceSet.dependsOn(iosMain)
        iosSimulatorArm64Target.compilations["main"].defaultSourceSet.dependsOn(iosMain)

        iosX64Target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
        iosArm64Target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
        iosSimulatorArm64Target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
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
