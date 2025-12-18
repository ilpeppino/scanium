plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    androidTarget()

    val iosArm64Target = iosArm64() {
        binaries.framework {
            baseName = "ScaniumCoreModels"
            isStatic = true
        }
    }
    val iosSimulatorArm64Target = iosSimulatorArm64() {
        binaries.framework {
            baseName = "ScaniumCoreModels"
            isStatic = true
        }
    }

    sourceSets {
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

koverReport {
    defaults {
        verify {
            rule {
                bound { minValue = 85 }
            }
        }
    }
}
