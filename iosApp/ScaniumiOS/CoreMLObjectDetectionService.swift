import Foundation
import Vision
import CoreML
import CoreGraphics

final class CoreMLObjectDetectionService: ObjectDetectionService {
    private let model: VNCoreMLModel
    private let queue = DispatchQueue(label: "com.scanium.coreml.objectdetection", qos: .userInitiated)

    init(model: MLModel) throws {
        self.model = try VNCoreMLModel(for: model)
    }

    func detectObjects(in image: CGImage, orientation: CGImagePropertyOrientation = .up) async throws -> [DetectedObject] {
        try await withCheckedThrowingContinuation { continuation in
            queue.async {
                let request = VNCoreMLRequest(model: model) { request, _ in
                    let observations = (request.results as? [VNRecognizedObjectObservation]) ?? []
                    let objects = observations.map { observation -> DetectedObject in
                        let bestLabel = observation.labels.first
                        let rect = NormalizedRect(visionBoundingBox: observation.boundingBox).clamped()
                        return DetectedObject(
                            label: bestLabel?.identifier,
                            confidence: Float(bestLabel?.confidence ?? 0),
                            boundingBox: rect,
                            trackingId: observation.uuid.uuidString,
                            thumbnail: nil
                        )
                    }
                    continuation.resume(returning: objects)
                }
                request.imageCropAndScaleOption = .scaleFill

                let handler = VNImageRequestHandler(cgImage: image, orientation: orientation, options: [:])
                do {
                    try handler.perform([request])
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
