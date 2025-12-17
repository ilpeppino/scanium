***REMOVED*** Kotlin Multiplatform framework drop location

This folder is intentionally kept in the repo so Xcode can reference the upcoming KMP framework without requiring a build yet.

Expected artifact: `Shared.xcframework` produced by the shared Gradle module (e.g., via `./gradlew :shared:assembleXCFramework`).
Copy the generated `.xcframework` here so `ScaniumiOS` can embed it from `iosApp/Frameworks/Shared.xcframework`.
