import SwiftUI

struct ContentView: View {
    private let items: [ScannedItem] = SharedBridge
        .makeDataSource(useMocks: FeatureFlags.useMocks)
        .loadItems()

    var body: some View {
        NavigationStack {
            List(items) { item in
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "shippingbox")
                        .foregroundColor(.accentColor)
                        .padding(.top, 4)

                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(item.labelText ?? item.category.displayName)
                                .font(.headline)
                            Spacer()
                            Text(item.priceRange.formatted)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }

                        Text("Confidence: \(item.formattedConfidence) (\(item.confidenceLevel.displayName))")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        if let recognizedText = item.recognizedText {
                            Text(recognizedText)
                                .font(.callout)
                                .foregroundStyle(.secondary)
                        }

                        if let barcode = item.barcodeValue {
                            Text("Barcode: \(barcode)")
                                .font(.callout)
                                .foregroundStyle(.secondary)
                        }

                        Text("Listing: \(item.listingStatus.displayName)")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
            }
            .navigationTitle("Scanium (iOS)")
        }
    }
}

#Preview {
    ContentView()
}
