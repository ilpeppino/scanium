import AVFoundation
import CoreGraphics
import ImageIO
import VideoToolbox

final class AVFoundationFrameSource: NSObject, FrameSource {
    weak var delegate: FrameSourceDelegate?

    private let captureSession = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let sessionQueue = DispatchQueue(label: "com.scanium.avfoundation.frame-source")
    private let ciContext = CIContext()

    override init() {
        super.init()
        configureSession()
    }

    func start() {
        sessionQueue.async {
            guard !self.captureSession.isRunning else { return }
            self.captureSession.startRunning()
        }
    }

    func stop() {
        sessionQueue.async {
            guard self.captureSession.isRunning else { return }
            self.captureSession.stopRunning()
        }
    }

    private func configureSession() {
        sessionQueue.async {
            self.captureSession.beginConfiguration()
            self.captureSession.sessionPreset = .high

            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
                  let input = try? AVCaptureDeviceInput(device: device),
                  self.captureSession.canAddInput(input) else {
                self.captureSession.commitConfiguration()
                return
            }
            self.captureSession.addInput(input)

            if self.captureSession.canAddOutput(self.videoOutput) {
                self.videoOutput.alwaysDiscardsLateVideoFrames = true
                self.videoOutput.setSampleBufferDelegate(self, queue: self.sessionQueue)
                self.captureSession.addOutput(self.videoOutput)
                self.videoOutput.connections.first?.videoOrientation = .portrait
            }

            self.captureSession.commitConfiguration()
        }
    }
}

extension AVFoundationFrameSource: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else { return }
        guard let imageData = encodeJPEG(from: cgImage) else { return }

        let imageRef = ImageRef(bytes: imageData, mimeType: "image/jpeg", width: cgImage.width, height: cgImage.height)
        let orientation = CGImagePropertyOrientation(connection.videoOrientation)

        DispatchQueue.main.async {
            self.delegate?.frameSource(self, didOutput: imageRef, orientation: orientation)
        }
    }

    private func encodeJPEG(from image: CGImage) -> Data? {
        let mutableData = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(mutableData, AVFileType.jpeg as CFString, 1, nil) else {
            return nil
        }
        CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: 0.65] as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            return nil
        }
        return mutableData as Data
    }
}

private extension CGImagePropertyOrientation {
    init(_ orientation: AVCaptureVideoOrientation) {
        switch orientation {
        case .portrait:
            self = .right
        case .portraitUpsideDown:
            self = .left
        case .landscapeRight:
            self = .up
        case .landscapeLeft:
            self = .down
        @unknown default:
            self = .up
        }
    }
}
