import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.0-1.0.24"
    kotlin("plugin.serialization") version "2.0.0"
    // SEC-002: SBOM generation for supply chain security
    id("org.cyclonedx.bom") version "1.8.2"
    // SEC-003: Automated CVE scanning
    id("org.owasp.dependencycheck") version "10.0.4"
}

// Load local.properties for API configuration (not committed to git)
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.scanium.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scanium.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Cloud classification API configuration
        // Read from local.properties (dev) or environment variables (CI/production)
        // Required keys in local.properties:
        //   scanium.api.base.url=https://your-backend.com/api/v1
        //   scanium.api.key=your-dev-api-key
        val apiBaseUrl = localProperties.getProperty("scanium.api.base.url")
            ?: System.getenv("SCANIUM_API_BASE_URL")
            ?: ""
        val apiKey = localProperties.getProperty("scanium.api.key")
            ?: System.getenv("SCANIUM_API_KEY")
            ?: ""

        buildConfigField("String", "SCANIUM_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "SCANIUM_API_KEY", "\"$apiKey\"")

        // Legacy fields for backward compatibility (deprecated)
        buildConfigField("String", "CLOUD_CLASSIFIER_URL", "\"$apiBaseUrl/classify\"")
        buildConfigField("String", "CLOUD_CLASSIFIER_API_KEY", "\"$apiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// SEC-002: Configure SBOM (Software Bill of Materials) generation
// Generates CycloneDX SBOM in JSON format for supply chain security
tasks.cyclonedxBom {
    // Include all build variants
    includeConfigs.set(listOf("releaseRuntimeClasspath", "debugRuntimeClasspath"))
    // Skip configurations that aren't needed
    skipConfigs.set(listOf("lintClassPath", "jacocoAgent", "jacocoAnt"))
    // Output format
    outputFormat.set("json")
    outputName.set("scanium-bom")
    // Include license information
    includeLicenseText.set(false)
    // Component version comes from project version
    projectType.set("application")
    schemaVersion.set("1.5")
}

// SEC-003: Configure OWASP Dependency-Check for CVE scanning
// Scans dependencies for known vulnerabilities from NVD database
dependencyCheck {
    // Formats for vulnerability reports
    formats = listOf("HTML", "JSON", "SARIF")

    // Fail build on CVSS score >= 7.0 (HIGH severity)
    failBuildOnCVSS = 7.0f

    // Suppress false positives (can be configured per-project)
    suppressionFile = file("dependency-check-suppressions.xml").takeIf { it.exists() }?.absolutePath

    // Analyzer configurations
    analyzers.apply {
        // Enable experimental analyzers
        experimentalEnabled = false
        // Disable slow analyzers not needed for Android
        archiveEnabled = false
        assemblyEnabled = false
        nuspecEnabled = false
    }

    // NVD API key (optional, improves download speed)
    // Set via environment variable: DEPENDENCY_CHECK_NVD_API_KEY
    nvd.apiKey = System.getenv("DEPENDENCY_CHECK_NVD_API_KEY") ?: ""

    // Cache NVD data for faster subsequent runs
    data.directory = "${project.buildDir}/dependency-check-data"
}

dependencies {
    implementation(project(":core-models"))
    implementation(project(":core-tracking"))
    implementation(project(":core-domainpack"))
    implementation(project(":core-scan"))
    implementation(project(":core-contracts"))
    implementation(project(":android-ml-mlkit"))
    implementation(project(":android-camera-camerax"))
    implementation(project(":android-platform-adapters"))

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("androidx.exifinterface:exifinterface:1.3.6")

    // ML Kit Object Detection
    implementation("com.google.mlkit:object-detection:17.0.1")

    // ML Kit Barcode Scanning
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Coroutines for Play Services (needed for ML Kit await)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Kotlinx Serialization (for Domain Pack JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // OkHttp for cloud classification API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testing - Unit Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("androidx.arch.core:core-testing:2.2.0") // For LiveData/Flow testing
    testImplementation("org.robolectric:robolectric:4.11.1") // For Android framework classes in unit tests
    testImplementation("androidx.test:core:1.5.0") // For ApplicationProvider

    // Testing - Instrumented Tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Debug implementations
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
