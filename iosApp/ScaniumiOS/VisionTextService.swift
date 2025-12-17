import Foundation
import Vision
import CoreGraphics

final class VisionTextService: TextRecognitionService {
    private let queue = DispatchQueue(label: "com.scanium.vision.text", qos: .userInitiated)

    func recognizeText(in image: CGImage, orientation: CGImagePropertyOrientation = .up) async throws -> [TextBlock] {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                let request = VNRecognizeTextRequest()
                request.recognitionLevel = .accurate
                request.usesLanguageCorrection = true

                let handler = VNImageRequestHandler(cgImage: image, orientation: orientation, options: [:])
                do {
                    try handler.perform([request])
                    let results = (request.results as? [VNRecognizedTextObservation]) ?? []
                    let mapped = results.compactMap { observation -> TextBlock? in
                        guard let candidate = observation.topCandidates(1).first else { return nil }
                        let rect = NormalizedRect(visionBoundingBox: observation.boundingBox).clamped()
                        return TextBlock(text: candidate.string, boundingBox: rect)
                    }
                    continuation.resume(returning: mapped)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
