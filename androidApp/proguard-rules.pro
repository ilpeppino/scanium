# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# Security hardening (SEC-013, SEC-017):
# - Enable code obfuscation
# - Strip debug logging
# - Remove unused resources

# =============================================================================
# Core Android & Kotlin
# =============================================================================

# Avoid broad keep rules that block obfuscation
# Framework/AndroidX classes are handled by the default Android rules; only app-specific
# entry points are kept below.

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# =============================================================================
# ML Kit & CameraX (Required for functionality)
# =============================================================================

# Keep ML Kit classes (required for proper inference)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# =============================================================================
# Scanium App Classes
# =============================================================================

# Keep MainActivity (entry point)
-keep class com.scanium.app.MainActivity { *; }

# Keep ScaniumApp composable
-keep class com.scanium.app.ScaniumAppKt { *; }

# Obfuscate all internal classes while preserving structure
-keep,allowobfuscation class com.scanium.app.** { *; }

# Keep data classes and enums (for serialization)
-keep class com.scanium.app.**.domain.** { *; }
-keep class com.scanium.app.**.model.** { *; }
-keep class com.scanium.app.items.ScannedItem { *; }

# Keep Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# =============================================================================
# Serialization (Kotlinx Serialization)
# =============================================================================

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Serializer classes
-keep,includedescriptorclasses class com.scanium.app.**$$serializer { *; }
-keepclassmembers class com.scanium.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.scanium.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# =============================================================================
# Security: Strip Debug Logging (SEC-017)
# =============================================================================

# Remove all android.util.Log calls in release builds
# This strips 304 log statements identified in the security assessment
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# Remove println statements
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Remove printStackTrace calls (security leak)
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# =============================================================================
# Optimization
# =============================================================================

# Enable aggressive optimization
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove unused code
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# =============================================================================
# Debugging Support (Crash Reports)
# =============================================================================

# Keep line numbers for crash reports (minimal info leak, high debugging value)
-keepattributes SourceFile,LineNumberTable

# Rename source file attribute to hide real file names
-renamesourcefileattribute SourceFile
