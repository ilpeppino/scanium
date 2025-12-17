import Foundation

// Temporary Swift-side mirror of the shared models so the UI can be wired up
// before the KMP framework is available.
struct ImageRef {
    let uri: URL?
    let width: Int
    let height: Int
}

struct NormalizedRect {
    let x: Double
    let y: Double
    let width: Double
    let height: Double
}

struct PriceRange {
    let low: Double
    let high: Double

    var formatted: String {
        "€\(Int(low)) - €\(Int(high))"
    }
}

enum ItemCategory: String, CaseIterable {
    case fashion = "Fashion"
    case homeGood = "Home Good"
    case food = "Food Product"
    case place = "Place"
    case plant = "Plant"
    case electronics = "Electronics"
    case document = "Document"
    case unknown = "Unknown"

    var displayName: String { rawValue }
}

enum ItemListingStatus: String {
    case notListed = "Not Listed"
    case listingInProgress = "Posting..."
    case listedActive = "Listed"
    case listingFailed = "Failed"

    var displayName: String { rawValue }
}

enum ConfidenceLevel: CaseIterable {
    case low
    case medium
    case high

    var threshold: Double {
        switch self {
        case .low:
            return 0.0
        case .medium:
            return 0.5
        case .high:
            return 0.75
        }
    }

    var displayName: String {
        switch self {
        case .low:
            return "Low"
        case .medium:
            return "Medium"
        case .high:
            return "High"
        }
    }
}

struct ScannedItem: Identifiable {
    let id: String
    let thumbnail: ImageRef?
    let thumbnailRef: ImageRef?
    let category: ItemCategory
    let priceRange: PriceRange
    let confidence: Double
    let timestamp: Date
    let recognizedText: String?
    let barcodeValue: String?
    let boundingBox: NormalizedRect?
    let labelText: String?
    let fullImageUri: URL?
    let fullImagePath: String?
    let listingStatus: ItemListingStatus
    let listingId: String?
    let listingUrl: URL?

    var formattedConfidence: String {
        "\(Int(confidence * 100))%"
    }

    var confidenceLevel: ConfidenceLevel {
        ConfidenceLevel.allCases
            .sorted { $0.threshold > $1.threshold }
            .first { confidence >= $0.threshold } ?? .low
    }
}

enum MockItems {
    static let sample: [ScannedItem] = [
        ScannedItem(
            id: "item-fashion-1",
            thumbnail: ImageRef(uri: nil, width: 320, height: 320),
            thumbnailRef: nil,
            category: .fashion,
            priceRange: PriceRange(low: 40, high: 85),
            confidence: 0.86,
            timestamp: Date(),
            recognizedText: nil,
            barcodeValue: nil,
            boundingBox: NormalizedRect(x: 0.1, y: 0.2, width: 0.3, height: 0.4),
            labelText: "Sneaker",
            fullImageUri: nil,
            fullImagePath: nil,
            listingStatus: .notListed,
            listingId: nil,
            listingUrl: nil
        ),
        ScannedItem(
            id: "item-electronics-2",
            thumbnail: ImageRef(uri: nil, width: 320, height: 320),
            thumbnailRef: nil,
            category: .electronics,
            priceRange: PriceRange(low: 120, high: 250),
            confidence: 0.73,
            timestamp: Date(),
            recognizedText: nil,
            barcodeValue: "01234567890",
            boundingBox: NormalizedRect(x: 0.4, y: 0.35, width: 0.25, height: 0.25),
            labelText: "Vintage camera",
            fullImageUri: URL(string: "https://example.com/camera.jpg"),
            fullImagePath: nil,
            listingStatus: .listedActive,
            listingId: "EB123",
            listingUrl: URL(string: "https://ebay.example.com/EB123")
        ),
        ScannedItem(
            id: "item-document-3",
            thumbnail: ImageRef(uri: nil, width: 160, height: 200),
            thumbnailRef: nil,
            category: .document,
            priceRange: PriceRange(low: 0, high: 0),
            confidence: 0.64,
            timestamp: Date(),
            recognizedText: "Serial: SN-8483-22",
            barcodeValue: nil,
            boundingBox: nil,
            labelText: "Warranty slip",
            fullImageUri: nil,
            fullImagePath: "file:///documents/warranty.pdf",
            listingStatus: .listingInProgress,
            listingId: nil,
            listingUrl: nil
        ),
        ScannedItem(
            id: "item-home-4",
            thumbnail: ImageRef(uri: nil, width: 240, height: 240),
            thumbnailRef: nil,
            category: .homeGood,
            priceRange: PriceRange(low: 25, high: 65),
            confidence: 0.42,
            timestamp: Date(),
            recognizedText: nil,
            barcodeValue: nil,
            boundingBox: NormalizedRect(x: 0.2, y: 0.25, width: 0.4, height: 0.35),
            labelText: "Table lamp",
            fullImageUri: nil,
            fullImagePath: nil,
            listingStatus: .listingFailed,
            listingId: nil,
            listingUrl: nil
        )
    ]
}
