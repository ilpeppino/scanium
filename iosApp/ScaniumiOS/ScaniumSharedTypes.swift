import Foundation

***REMOVED***if canImport(Shared)
import Shared
***REMOVED***endif

/// Centralizes Swift-friendly wrappers around models coming from the shared Kotlin framework.
/// Keep SwiftUI surfaces depending on these adapters instead of importing `Shared` directly.
enum ScaniumSharedTypes {
    /// Lightweight view model for list previews; replace with real shared-backed model once available.
    struct ItemPreview: Identifiable {
        let id: UUID
        let title: String
        let subtitle: String?
    }

    ***REMOVED***if canImport(Shared)
    static func mapSharedItem(_ sharedItem: Any) -> ItemPreview {
        _ = sharedItem
        // TODO: Map shared KMP item models into SwiftUI-friendly types.
        return ItemPreview(id: UUID(), title: "shared-item", subtitle: nil)
    }
    ***REMOVED***else
    static func placeholderItems() -> [ItemPreview] {
        // Placeholder to keep SwiftUI previews compiling until the shared framework is wired in.
        []
    }
    ***REMOVED***endif
}
