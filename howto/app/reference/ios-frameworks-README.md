# Kotlin Multiplatform framework drop location

This folder is intentionally kept in the repo so Xcode can reference the upcoming KMP framework
without requiring a build yet.

Integration plan:

- Build the KMP artifact with `./gradlew :shared:assembleXCFramework`. The release build produces
  `shared/build/XCFrameworks/release/Shared.xcframework`.
- Copy or symlink that folder into `iosApp/Frameworks/Shared.xcframework`; the Xcode project
  resolves the framework from this relative path.
- In Xcode (**Frameworks, Libraries, and Embedded Content**), keep `Shared.xcframework` set to *
  *Embed & Sign** so the binary is bundled once available. All Swift entry points should import
  `Shared` only inside `iosApp/ScaniumiOS/SharedBridge.swift` (and related helpers) to keep a single
  integration surface.
