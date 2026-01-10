***REMOVED*** iOS setup (SwiftUI scaffold)

A starter SwiftUI project lives in `iosApp/ScaniumiOS.xcodeproj`.

***REMOVED******REMOVED*** Opening the project
1. Open Xcode (15 or newer recommended).
2. Choose **File ▸ Open...** and select `iosApp/ScaniumiOS.xcodeproj` from the repository root.
3. Run on a simulator/device after selecting the `ScaniumiOS` scheme. (No build expected in CI yet; do **not** run from Gradle.)

***REMOVED******REMOVED*** Preparing for the KMP framework
- Build the shared XCFramework when available (planned Gradle task: `./gradlew :shared:assembleXCFramework`). The output will live at `shared/build/XCFrameworks/release/Shared.xcframework`.
- Copy or symlink the generated `Shared.xcframework` into `iosApp/Frameworks/` (kept in git so Xcode can reference it). The project already looks for the binary at `iosApp/Frameworks/Shared.xcframework`.
- In Xcode, drag `Shared.xcframework` from `Frameworks` into the `ScaniumiOS` target and set **Embed** to **Embed & Sign** under **Frameworks, Libraries, and Embedded Content**. This will ensure the app bundles the framework once it exists.

***REMOVED******REMOVED*** Swift glue
- `iosApp/ScaniumiOS/SharedBridge.swift` is the single import point for the `Shared` XCFramework and will house Swift-friendly façade functions. The implementation is a no-op until the KMP build is available.
- `iosApp/ScaniumiOS/ScaniumSharedTypes.swift` centralizes mapping helpers to adapt KMP models into SwiftUI-facing types.

***REMOVED******REMOVED*** Next steps
- The app currently shows a single SwiftUI screen with placeholder items.
- Upcoming work will integrate the shared Kotlin Multiplatform framework once the KMP modules are ready.
- Keep iOS-only UI logic here; shared business logic should flow through the KMP framework bindings.
