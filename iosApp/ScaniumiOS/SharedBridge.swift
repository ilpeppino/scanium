import Foundation

#if canImport(Shared)
import Shared
#endif

/// Single entry point for importing the `Shared` XCFramework and exposing Swift-friendly facades.
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

    /// Abstracts the source of items for SwiftUI surfaces.
    protocol DataSource {
        func loadItems() -> [ScannedItem]
    }

    /// Creates a session backed by the Shared.xcframework when available; otherwise returns a stub.
    static func makeSession(configuration: BootstrapConfiguration = BootstrapConfiguration()) -> Session {
        #if canImport(Shared)
        return KmpBackedSession(configuration: configuration)
        #else
        return StubSession(configuration: configuration)
        #endif
    }

    /// Returns either mock Swift data or the shared KMP-backed items based on the active toggle.
    static func makeDataSource(
        configuration: BootstrapConfiguration = BootstrapConfiguration(),
        useMocks: Bool = FeatureFlags.useMocks
    ) -> DataSource {
        if useMocks {
            return MockDataSource(configuration: configuration)
        }

        #if canImport(Shared)
        return SharedDataSource(configuration: configuration)
        #else
        return MockDataSource(configuration: configuration)
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

private struct MockDataSource: SharedBridge.DataSource {
    let configuration: SharedBridge.BootstrapConfiguration

    func loadItems() -> [ScannedItem] {
        _ = configuration
        return MockItems.sample
    }
}

#if canImport(Shared)
private struct SharedDataSource: SharedBridge.DataSource {
    let configuration: SharedBridge.BootstrapConfiguration

    func loadItems() -> [ScannedItem] {
        _ = configuration
        let sampleItems = SampleItemsProvider().sampleItems()
        return sampleItems.compactMap(ScannedItem.init(shared:))
    }
}

private extension ScannedItem {
    init?(shared: SharedScannedItem) {
        guard let pricePair = shared.priceRange as? KotlinPair else { return nil }
        let low = pricePair.first as? Double ?? 0
        let high = pricePair.second as? Double ?? 0

        let mappedCategory: ItemCategory
        switch shared.category {
        case SharedItemCategory.fashion:
            mappedCategory = .fashion
        case SharedItemCategory.homeGood:
            mappedCategory = .homeGood
        case SharedItemCategory.food:
            mappedCategory = .food
        case SharedItemCategory.place:
            mappedCategory = .place
        case SharedItemCategory.plant:
            mappedCategory = .plant
        case SharedItemCategory.electronics:
            mappedCategory = .electronics
        case SharedItemCategory.document:
            mappedCategory = .document
        default:
            mappedCategory = .unknown
        }

        let mappedListing: ItemListingStatus
        switch shared.listingStatus {
        case .listingInProgress:
            mappedListing = .listingInProgress
        case .listedActive:
            mappedListing = .listedActive
        case .listingFailed:
            mappedListing = .listingFailed
        default:
            mappedListing = .notListed
        }

        let boundingBox: NormalizedRect?
        if let sharedBox = shared.boundingBox as? SharedNormalizedRect {
            boundingBox = NormalizedRect(
                x: Double(sharedBox.left),
                y: Double(sharedBox.top),
                width: Double(sharedBox.width),
                height: Double(sharedBox.height)
            )
        } else {
            boundingBox = nil
        }

        self.init(
            id: shared.id,
            thumbnail: nil,
            thumbnailRef: nil,
            category: mappedCategory,
            priceRange: PriceRange(low: low, high: high),
            confidence: Double(shared.confidence),
            timestamp: Date(timeIntervalSince1970: TimeInterval(shared.timestamp) / 1000),
            recognizedText: shared.recognizedText,
            barcodeValue: shared.barcodeValue,
            boundingBox: boundingBox,
            labelText: shared.labelText,
            fullImageUri: (shared.fullImageUri as? String).flatMap(URL.init(string:)),
            fullImagePath: shared.fullImagePath,
            listingStatus: mappedListing,
            listingId: shared.listingId,
            listingUrl: (shared.listingUrl as? String).flatMap(URL.init(string:))
        )
    }
}
#endif
