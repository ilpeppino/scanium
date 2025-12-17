import SwiftUI

struct ContentView: View {
    private let placeholderItems: [String] = [
        "Vintage camera",
        "Sneakers",
        "Vinyl record",
        "Designer bag"
    ]

    var body: some View {
        NavigationStack {
            List(placeholderItems, id: \.self) { item in
                HStack {
                    Image(systemName: "shippingbox")
                        .foregroundColor(.accentColor)
                    Text(item)
                        .font(.body)
                }
            }
            .navigationTitle("Scanium (iOS)")
        }
    }
}

#Preview {
    ContentView()
}
