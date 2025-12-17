# iOS setup (SwiftUI scaffold)

A starter SwiftUI project lives in `iosApp/ScaniumiOS.xcodeproj`.

## Opening the project
1. Open Xcode (15 or newer recommended).
2. Choose **File ▸ Open...** and select `iosApp/ScaniumiOS.xcodeproj` from the repository root.
3. Run on a simulator/device after selecting the `ScaniumiOS` scheme. (No build expected in CI yet; do **not** run from Gradle.)

## Next steps
- The app currently shows a single SwiftUI screen with placeholder items.
- Upcoming work will integrate the shared Kotlin Multiplatform framework once the KMP modules are ready.
- Keep iOS-only UI logic here; shared business logic should flow through the KMP framework bindings.
