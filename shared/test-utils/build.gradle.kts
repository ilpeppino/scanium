plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()

    // JVM target for running tests without Android SDK (CI/container-friendly)
    jvm()

    val iosArm64Target = iosArm64() {
        binaries.framework {
            baseName = "ScaniumTestUtils"
            isStatic = true
        }
    }
    val iosSimulatorArm64Target = iosSimulatorArm64() {
        binaries.framework {
            baseName = "ScaniumTestUtils"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Dependencies on core modules for testing
                implementation(project(":shared:core-models"))
                implementation(project(":shared:core-tracking"))

                // Kotlin test utilities
                implementation(kotlin("test"))
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
    namespace = "com.scanium.test"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
