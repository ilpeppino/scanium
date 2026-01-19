plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()

    // JVM target for running tests without Android SDK (CI/container-friendly)
    jvm()

    // iOS targets
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ScaniumTestUtils"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Dependencies on core modules for testing
            implementation(project(":shared:core-models"))
            implementation(project(":shared:core-tracking"))

            // Kotlin test utilities
            implementation(kotlin("test"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
        }
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
