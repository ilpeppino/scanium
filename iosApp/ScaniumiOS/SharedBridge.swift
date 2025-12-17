import Foundation

/// Temporary glue for wiring the KMP-generated Shared.xcframework into the SwiftUI app.
/// The functions here remain no-ops until the framework is available.
enum SharedBridge {
    /// Captures configuration that the shared Kotlin entry point will eventually require.
    struct BootstrapConfiguration {
        /// Placeholder for dependency hints (analytics endpoints, feature flags, etc.).
        var notes: String

        init(notes: String = "KMP bootstrap pending") {
            self.notes = notes
        }
    }

    /// Abstracts the shared session lifecycle so SwiftUI code can be written before the framework exists.
    protocol Session {
        func start()
        func stop()
    }

    /// Creates a session backed by the Shared.xcframework when available; otherwise returns a stub.
    static func makeSession(configuration: BootstrapConfiguration = BootstrapConfiguration()) -> Session {
        #if canImport(Shared)
        return KmpBackedSession(configuration: configuration)
        #else
        return StubSession(configuration: configuration)
        #endif
    }
}

private struct StubSession: SharedBridge.Session {
    let configuration: SharedBridge.BootstrapConfiguration

    func start() {
        // No-op until the Shared.xcframework is integrated.
    }

    func stop() {
        // No-op until the Shared.xcframework is integrated.
    }
}

#if canImport(Shared)
import Shared

/// Placeholder adapter that will wrap the real shared entry point once the Kotlin framework is built.
private struct KmpBackedSession: SharedBridge.Session {
    let configuration: SharedBridge.BootstrapConfiguration

    func start() {
        // TODO: Initialize and start the shared scan session from the Shared framework.
    }

    func stop() {
        // TODO: Tear down the shared scan session and release shared resources.
    }
}
#endif
