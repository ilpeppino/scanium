***REMOVED*** Add project specific ProGuard rules here.
***REMOVED*** You can control the set of applied configuration files using the
***REMOVED*** proguardFiles setting in build.gradle.

***REMOVED*** Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

***REMOVED*** Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

***REMOVED*** Keep model classes
-keep class com.example.objecta.items.** { *; }
-keep class com.example.objecta.ml.** { *; }
