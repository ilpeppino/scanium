***REMOVED*** Add project specific ProGuard rules here.
***REMOVED*** You can control the set of applied configuration files using the
***REMOVED*** proguardFiles setting in build.gradle.
***REMOVED***
***REMOVED*** Security hardening (SEC-013, SEC-017):
***REMOVED*** - Enable code obfuscation
***REMOVED*** - Strip debug logging
***REMOVED*** - Remove unused resources

***REMOVED*** =============================================================================
***REMOVED*** Core Android & Kotlin
***REMOVED*** =============================================================================

***REMOVED*** Keep Android framework classes
-keep class android.** { *; }
-keep class androidx.** { *; }

***REMOVED*** Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

***REMOVED*** =============================================================================
***REMOVED*** ML Kit & CameraX (Required for functionality)
***REMOVED*** =============================================================================

***REMOVED*** Keep ML Kit classes (required for proper inference)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

***REMOVED*** Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

***REMOVED*** =============================================================================
***REMOVED*** Scanium App Classes
***REMOVED*** =============================================================================

***REMOVED*** Keep MainActivity (entry point)
-keep class com.scanium.app.MainActivity { *; }

***REMOVED*** Keep ScaniumApp composable
-keep class com.scanium.app.ScaniumAppKt { *; }

***REMOVED*** Obfuscate all internal classes while preserving structure
-keep,allowobfuscation class com.scanium.app.** { *; }

***REMOVED*** Keep data classes and enums (for serialization)
-keep class com.scanium.app.**.domain.** { *; }
-keep class com.scanium.app.**.model.** { *; }
-keep class com.scanium.app.items.ScannedItem { *; }

***REMOVED*** Keep Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

***REMOVED*** =============================================================================
***REMOVED*** Serialization (Kotlinx Serialization)
***REMOVED*** =============================================================================

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

***REMOVED*** Keep Serializer classes
-keep,includedescriptorclasses class com.scanium.app.**$$serializer { *; }
-keepclassmembers class com.scanium.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.scanium.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

***REMOVED*** =============================================================================
***REMOVED*** Security: Strip Debug Logging (SEC-017)
***REMOVED*** =============================================================================

***REMOVED*** Remove all android.util.Log calls in release builds
***REMOVED*** This strips 304 log statements identified in the security assessment
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

***REMOVED*** Remove println statements
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

***REMOVED*** Remove printStackTrace calls (security leak)
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

***REMOVED*** =============================================================================
***REMOVED*** Optimization
***REMOVED*** =============================================================================

***REMOVED*** Enable aggressive optimization
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

***REMOVED*** Remove unused code
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

***REMOVED*** =============================================================================
***REMOVED*** Debugging Support (Crash Reports)
***REMOVED*** =============================================================================

***REMOVED*** Keep line numbers for crash reports (minimal info leak, high debugging value)
-keepattributes SourceFile,LineNumberTable

***REMOVED*** Rename source file attribute to hide real file names
-renamesourcefileattribute SourceFile
