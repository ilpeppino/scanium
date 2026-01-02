plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget()
    jvm()

    // iOS targets
    val iosX64Target =
        iosX64 {
            binaries.framework {
                baseName = "ScaniumCoreExport"
                isStatic = true
            }
        }
    val iosArm64Target =
        iosArm64 {
            binaries.framework {
                baseName = "ScaniumCoreExport"
                isStatic = true
            }
        }
    val iosSimulatorArm64Target =
        iosSimulatorArm64 {
            binaries.framework {
                baseName = "ScaniumCoreExport"
                isStatic = true
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared:core-models"))
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

        iosX64Target.compilations["main"].defaultSourceSet.dependsOn(iosMain)
        iosArm64Target.compilations["main"].defaultSourceSet.dependsOn(iosMain)
        iosSimulatorArm64Target.compilations["main"].defaultSourceSet.dependsOn(iosMain)

        iosX64Target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
        iosArm64Target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
        iosSimulatorArm64Target.compilations["test"].defaultSourceSet.dependsOn(iosTest)
    }
}

android {
    namespace = "com.scanium.shared.core.export"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
