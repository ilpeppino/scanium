plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    // Android target
    androidTarget()

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        // Common source sets
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // Android source sets
        val androidMain by getting
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        // iOS source sets
        val iosMain by creating {
            dependsOn(commonMain)
        }

        val iosTest by creating {
            dependsOn(commonTest)
        }

        // Configure individual iOS targets to use shared iosMain/iosTest
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

        val iosX64Test by getting { dependsOn(iosTest) }
        val iosArm64Test by getting { dependsOn(iosTest) }
        val iosSimulatorArm64Test by getting { dependsOn(iosTest) }
    }
}

// Android configuration
android {
    namespace = "com.scanium.shared.core.models"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
