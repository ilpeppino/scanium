import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.ksp)
    kotlin("plugin.serialization") version "2.0.0"
    // SEC-002: SBOM generation for supply chain security
    id("org.cyclonedx.bom") version "1.8.2"
    // SEC-003: Automated CVE scanning
    // IMPORTANT: When updating AGP (build.gradle.kts), ensure this plugin version
    // remains compatible. Check compatibility matrix at:
    // https://github.com/dependency-check/dependency-check-gradle
    // Current: AGP 8.5.0 → Dependency-Check 10.0.4
    id("org.owasp.dependencycheck") version "10.0.4"
    id("org.jetbrains.kotlinx.kover")
    jacoco
    // ARCH-001: Hilt DI framework - version in gradle/libs.versions.toml
    alias(libs.plugins.hilt.android)
}

// Load local.properties for API configuration (not committed to git)
private val localProperties by lazy(LazyThreadSafetyMode.NONE) {
    Properties().apply {
        findLocalPropertiesFile()?.let { propsFile ->
            propsFile.inputStream().use { load(it) }
        }
    }
}

/**
 * Allow Android Studio to open either the repo root or the androidApp module directly.
 * When androidApp is opened standalone, rootProject points to /androidApp so we fall
 * back to the parent directory to locate the shared local.properties file.
 */
private fun findLocalPropertiesFile() =
    sequenceOf(
        rootProject.file("local.properties"),
        rootProject.projectDir.parentFile?.resolve("local.properties"),
    ).firstOrNull { file ->
        file?.exists() == true
    }?.also { file ->
        if (file.parentFile != rootProject.projectDir) {
            logger.info("Loading local.properties from ${file.parentFile}")
        }
    }

private fun localPropertyOrEnv(
    key: String,
    envKey: String,
    defaultValue: String = "",
): String = localProperties.getProperty(key) ?: System.getenv(envKey) ?: defaultValue

val saveClassifierCropsDebug = localPropertyOrEnv("scanium.classifier.save_crops.debug", envKey = "", defaultValue = "false").toBoolean()

android {
    namespace = "com.scanium.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scanium.app"
        minSdk = 24
        targetSdk = 34

        val versionCodeEnv = localPropertyOrEnv("scanium.version.code", "SCANIUM_VERSION_CODE", "1").toInt()
        val versionNameEnv = localPropertyOrEnv("scanium.version.name", "SCANIUM_VERSION_NAME", "1.0")

        versionCode = versionCodeEnv
        versionName = versionNameEnv

        // Cloud classification API configuration
        // Base URL read from local.properties (dev) or environment variables (CI/production)
        // API keys are provisioned at runtime and stored in the Android Keystore.
        //
        // For dual-mode configuration (LAN + Remote):
        //   scanium.api.base.url=https://your-public-backend.com  ***REMOVED*** Remote/production URL
        //   scanium.api.base.url.debug=http://192.168.1.100:3000  ***REMOVED*** LAN URL for debug builds
        //
        // Debug builds will use .debug URL if set, otherwise fall back to main URL
        // Release builds always use the main URL (scanium.api.base.url)
        val apiBaseUrl = localPropertyOrEnv("scanium.api.base.url", "SCANIUM_API_BASE_URL")
        val apiBaseUrlDebug = localPropertyOrEnv("scanium.api.base.url.debug", "SCANIUM_API_BASE_URL_DEBUG", apiBaseUrl)
        val apiKey = localPropertyOrEnv("scanium.api.key", "SCANIUM_API_KEY")
        // SEC-002: Sentry DSN Security Consideration
        // The DSN is intentionally embedded in the APK as this is Sentry's designed behavior.
        // Sentry DSNs are "semi-public" - they identify where to send events but do not
        // grant access to read data. See: https://docs.sentry.io/concepts/key-concepts/dsn-explainer/
        //
        // Mitigations (configured in Sentry project settings):
        //   1. Rate limiting - Enable quota management to cap events per period
        //   2. IP filtering - Configure inbound filters to block suspicious sources
        //   3. DSN rotation - Rotate monthly via Sentry project settings
        //   4. Data scrubbing - Enable server-side PII scrubbing
        //
        // See: docs/observability/SENTRY_ALERTING.md for configuration details
        val sentryDsn = localPropertyOrEnv("scanium.sentry.dsn", "SCANIUM_SENTRY_DSN")
        val telemetryDataRegion =
            localPropertyOrEnv(
                "scanium.telemetry.data_region",
                "SCANIUM_TELEMETRY_DATA_REGION",
                "US",
            )

        // OTLP (OpenTelemetry Protocol) configuration
        val otlpEndpoint = localPropertyOrEnv("scanium.otlp.endpoint", "SCANIUM_OTLP_ENDPOINT", "")
        val otlpEnabled = localPropertyOrEnv("scanium.otlp.enabled", "SCANIUM_OTLP_ENABLED", "false")

        // SEC-003: TLS Certificate pinning for MITM protection
        // Set the SHA-256 certificate pin hash in local.properties or environment:
        //   scanium.api.certificate.pin=sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
        // To obtain the pin hash for your server, use:
        //   openssl s_client -servername <host> -connect <host>:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
        val certificatePin = localPropertyOrEnv("scanium.api.certificate.pin", "SCANIUM_API_CERTIFICATE_PIN", "")

        // Note: SCANIUM_API_BASE_URL and CLOUD_CLASSIFIER_URL are set per-buildType (debug/release)
        // to support different URLs for LAN (debug) vs Remote (release) modes
        buildConfigField("String", "SCANIUM_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "SCANIUM_API_CERTIFICATE_PIN", "\"$certificatePin\"")
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"") // SEC-002: Semi-public by design
        buildConfigField("String", "OTLP_ENDPOINT", "\"$otlpEndpoint\"")
        buildConfigField("boolean", "OTLP_ENABLED", otlpEnabled)
        buildConfigField("String", "TELEMETRY_DATA_REGION", "\"$telemetryDataRegion\"")

        // Legacy field for backward compatibility (deprecated)
        buildConfigField("String", "CLOUD_CLASSIFIER_API_KEY", "\"$apiKey\"")

        // ARCH-001: Use Hilt test runner for instrumented tests
        testInstrumentationRunner = "com.scanium.app.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = localPropertyOrEnv("scanium.keystore.file", "SCANIUM_KEYSTORE_FILE")
            if (keystorePath.isNotEmpty() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = localPropertyOrEnv("scanium.keystore.password", "SCANIUM_KEYSTORE_PASSWORD")
                keyAlias = localPropertyOrEnv("scanium.key.alias", "SCANIUM_KEY_ALIAS")
                keyPassword = localPropertyOrEnv("scanium.key.password", "SCANIUM_KEY_PASSWORD")
            }
        }
    }

    // Product flavors for internal development vs beta testing
    flavorDimensions += "distribution"
    productFlavors {
        create("dev") {
            dimension = "distribution"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // Developer Mode available - enables access to Developer Options screen
            buildConfigField("boolean", "DEV_MODE_ENABLED", "true")
        }
        create("beta") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            // Developer Mode disabled - hides Developer Options for external testers
            buildConfigField("boolean", "DEV_MODE_ENABLED", "false")
        }
    }

    buildTypes {
        debug {
            // Debug builds use LAN base URL if configured, otherwise fall back to remote URL
            val debugUrl = localPropertyOrEnv("scanium.api.base.url.debug", "SCANIUM_API_BASE_URL_DEBUG", "")
            val effectiveDebugUrl = debugUrl.ifEmpty {
                localPropertyOrEnv("scanium.api.base.url", "SCANIUM_API_BASE_URL")
            }
            buildConfigField("String", "SCANIUM_API_BASE_URL", "\"$effectiveDebugUrl\"")
            buildConfigField("String", "CLOUD_CLASSIFIER_URL", "\"$effectiveDebugUrl/classify\"")
            buildConfigField("boolean", "CLASSIFIER_SAVE_CROPS", saveClassifierCropsDebug.toString())
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Release builds always use the main (remote) base URL
            val releaseUrl = localPropertyOrEnv("scanium.api.base.url", "SCANIUM_API_BASE_URL")
            buildConfigField("String", "SCANIUM_API_BASE_URL", "\"$releaseUrl\"")
            buildConfigField("String", "CLOUD_CLASSIFIER_URL", "\"$releaseUrl/classify\"")
            buildConfigField("boolean", "CLASSIFIER_SAVE_CROPS", "false")

            // Only sign if we have a valid configuration
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    bundle {
        abi {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        language {
            enableSplit = true
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
    implementation(project(":shared:core-export"))
    implementation(project(":shared:telemetry"))
    implementation(project(":shared:telemetry-contract"))

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
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
    implementation("androidx.security:security-crypto:1.0.0")

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

    // ML Kit Image Labeling for on-device classification
    implementation("com.google.mlkit:image-labeling:17.0.8")

    // Coroutines for Play Services (needed for ML Kit await)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing:6.1.0")
    implementation("com.android.billingclient:billing-ktx:6.1.0")

    // Kotlinx Serialization (for Domain Pack JSON and OTLP export)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

    // OkHttp for cloud classification API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Testing - Unit Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("androidx.arch.core:core-testing:2.2.0") // For LiveData/Flow testing
    testImplementation("org.robolectric:robolectric:4.11.1") // For Android framework classes in unit tests
    testImplementation("androidx.test:core:1.5.0") // For ApplicationProvider
    testImplementation(project(":shared:test-utils"))

    // Testing - Instrumented Tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Debug implementations
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Crash Reporting
    implementation("io.sentry:sentry-android:7.14.0")

    // ARCH-001: Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Hilt Testing
    testImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    kspTest("com.google.dagger:hilt-android-compiler:2.51.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.51.1")
}

koverReport {
    defaults {
        verify {
            rule {
                bound {
                    minValue = 75
                }
            }
        }
    }
}

// ============================================================================
// Backend Configuration Validation
// ============================================================================
// Validates and prints backend URL configuration at build time.
// Fails devDebug builds if SCANIUM_API_BASE_URL_DEBUG is blank.

/**
 * Masks sensitive values for safe printing (shows first 8 chars only).
 */
fun maskSecret(value: String): String = when {
    value.isEmpty() -> "(not set)"
    value.length <= 8 -> "***"
    else -> "${value.take(8)}..."
}

// Create validation task for backend configuration
tasks.register("validateBackendConfig") {
    group = "verification"
    description = "Validates backend API URL configuration and prints resolved values"

    doLast {
        val debugUrl = localPropertyOrEnv("scanium.api.base.url.debug", "SCANIUM_API_BASE_URL_DEBUG", "")
        val releaseUrl = localPropertyOrEnv("scanium.api.base.url", "SCANIUM_API_BASE_URL")
        val effectiveDebugUrl = debugUrl.ifEmpty { releaseUrl }
        val apiKey = localPropertyOrEnv("scanium.api.key", "SCANIUM_API_KEY")

        println("")
        println("┌─────────────────────────────────────────────────────────────┐")
        println("│  Scanium Backend Configuration                             │")
        println("├─────────────────────────────────────────────────────────────┤")
        println("│  Debug URL (effective):  ${effectiveDebugUrl.padEnd(36)}│")
        println("│  Release URL:            ${releaseUrl.ifEmpty { "(not set)" }.padEnd(36)}│")
        println("│  API Key:                ${maskSecret(apiKey).padEnd(36)}│")
        println("└─────────────────────────────────────────────────────────────┘")
        println("")

        // Validation warnings
        if (releaseUrl.isEmpty()) {
            logger.warn("⚠️  WARNING: scanium.api.base.url is not set. Release builds will have no backend URL.")
        }
        if (effectiveDebugUrl.isEmpty()) {
            logger.warn("⚠️  WARNING: No backend URL configured for debug builds.")
        }
        if (apiKey.isEmpty()) {
            logger.warn("⚠️  WARNING: scanium.api.key is not set. API authentication will fail.")
        }
    }
}

// Task to validate devDebug configuration and fail if URL is missing
tasks.register("validateDevDebugBackendConfig") {
    group = "verification"
    description = "Validates backend URL is configured for devDebug builds"

    doLast {
        val debugUrl = localPropertyOrEnv("scanium.api.base.url.debug", "SCANIUM_API_BASE_URL_DEBUG", "")
        val releaseUrl = localPropertyOrEnv("scanium.api.base.url", "SCANIUM_API_BASE_URL")
        val effectiveDebugUrl = debugUrl.ifEmpty { releaseUrl }

        if (effectiveDebugUrl.isEmpty()) {
            throw GradleException(
                """
                |
                |╔═══════════════════════════════════════════════════════════════════════╗
                |║  ERROR: Backend URL not configured for devDebug build                 ║
                |╠═══════════════════════════════════════════════════════════════════════╣
                |║                                                                       ║
                |║  Cloud features require a backend URL. Please configure one of:       ║
                |║                                                                       ║
                |║  Option 1: In local.properties (recommended for development)          ║
                |║    scanium.api.base.url.debug=http://192.168.1.100:3000               ║
                |║    ***REMOVED*** or                                                               ║
                |║    scanium.api.base.url=https://your-backend.example.com              ║
                |║                                                                       ║
                |║  Option 2: Via environment variables                                  ║
                |║    export SCANIUM_API_BASE_URL_DEBUG=http://192.168.1.100:3000        ║
                |║    ***REMOVED*** or                                                               ║
                |║    export SCANIUM_API_BASE_URL=https://your-backend.example.com       ║
                |║                                                                       ║
                |║  Run: scripts/android-configure-backend-dev.sh for guided setup       ║
                |║  See: docs/DEV_BACKEND_CONNECTIVITY.md for detailed instructions      ║
                |║                                                                       ║
                |╚═══════════════════════════════════════════════════════════════════════╝
                """.trimMargin()
            )
        }

        println("✓ Backend URL validated: $effectiveDebugUrl")
    }
}

// Hook validation into devDebug builds
tasks.matching { it.name == "assembleDevDebug" || it.name == "installDevDebug" }.configureEach {
    dependsOn("validateDevDebugBackendConfig")
}

// Jacoco HTML report for androidApp unit tests
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(false)
        csv.required.set(false)
        html.required.set(true)
    }
    // Collect execution data from unit tests
    executionData(fileTree(buildDir).include("**/jacoco/testDebugUnitTest.exec"))
    // Source sets for coverage
    val javaSrc = fileTree("src/main/java")
    val kotlinSrc = fileTree("src/main/kotlin")
    sourceDirectories.setFrom(files(javaSrc, kotlinSrc))
    classDirectories.setFrom(
        fileTree("$buildDir/tmp/kotlin-classes/debug") {
            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*")
        },
    )
}
