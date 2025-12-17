import Foundation
import CoreGraphics
import AVFoundation

protocol FrameSource: AnyObject {
    var delegate: FrameSourceDelegate? { get set }
    func start()
    func stop()
}

protocol FrameSourceDelegate: AnyObject {
    func frameSource(_ source: FrameSource, didOutput imageRef: ImageRef, orientation: CGImagePropertyOrientation)
}

struct BarcodeDetection {
    let payload: String
    let boundingBox: NormalizedRect
}

struct TextBlock {
    let text: String
    let boundingBox: NormalizedRect
}

struct DetectedObject {
    let label: String?
    let confidence: Float
    let boundingBox: NormalizedRect
    let trackingId: String?
    let thumbnail: ImageRef?
}

protocol BarcodeService {
    func detectBarcodes(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> [BarcodeDetection]
}

protocol TextRecognitionService {
    func recognizeText(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> [TextBlock]
}

protocol ObjectDetectionService {
    func detectObjects(in image: CGImage, orientation: CGImagePropertyOrientation) async throws -> [DetectedObject]
}
