plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.scanium.app"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // Depend on shared KMP module for tracking logic
    api(project(":shared:core-tracking"))

    implementation(project(":shared:core-models"))
    // Android wrapper for shared models provides platform typealiases (e.g., ScannedItem with Uri)
    implementation(project(":core-models"))
    testImplementation("junit:junit:4.13.2")
}
