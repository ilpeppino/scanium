import Foundation
import Vision
import CoreGraphics

final class VisionBarcodeService: BarcodeService {
    private let queue = DispatchQueue(label: "com.scanium.vision.barcode", qos: .userInitiated)

    func detectBarcodes(in image: CGImage, orientation: CGImagePropertyOrientation = .up) async throws -> [BarcodeDetection] {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                let request = VNDetectBarcodesRequest()
                request.symbologies = VNBarcodeSymbology.all

                let handler = VNImageRequestHandler(cgImage: image, orientation: orientation, options: [:])
                do {
                    try handler.perform([request])
                    let observations = (request.results as? [VNBarcodeObservation]) ?? []
                    let detections = observations.compactMap { observation -> BarcodeDetection? in
                        guard let payload = observation.payloadStringValue else { return nil }
                        let rect = NormalizedRect(visionBoundingBox: observation.boundingBox).clamped()
                        return BarcodeDetection(payload: payload, boundingBox: rect)
                    }
                    continuation.resume(returning: detections)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
