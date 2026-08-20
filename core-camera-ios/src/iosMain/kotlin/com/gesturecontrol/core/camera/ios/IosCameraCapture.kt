package com.gesturecontrol.core.camera.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePosition
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create

/**
 * Wraps an AVCaptureSession delivering BGRA camera frames -- the iOS counterpart to
 * `core-camera`'s CameraX-based Android capture. Delivers raw sample buffers via [onFrame]; the
 * caller (a later MediaPipe-wiring stage) turns a sample buffer into an `MPImage`, keeping this
 * class free of any MediaPipe dependency, mirroring the Android side's own layering.
 *
 * MediaPipe's own iOS guidance: `AVCaptureVideoDataOutput` must be configured for
 * `kCVPixelFormatType_32BGRA` for its live-stream detection methods to accept the frames.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCameraCapture(
    private val onFrame: (CMSampleBufferRef?) -> Unit,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    private val session = AVCaptureSession()
    private val outputQueue = dispatch_queue_create("com.gesturecontrol.camera.output", null)

    /** True once [configure] has successfully set up the session; false if the device/inputs
     * couldn't be added (e.g. no camera available, as on most Simulator configurations). */
    var isConfigured: Boolean = false
        private set

    /** No explicit permission request here: `AVCaptureDevice`'s `authorizationStatusForMediaType`/
     * `requestAccessForMediaType:completionHandler:` class methods aren't bound in this
     * Kotlin/Native distribution's `platform.AVFoundation` (both fully unresolved despite being
     * ordinary, non-deprecated ObjC class methods per the real SDK header -- a cinterop gap, not a
     * naming issue). Per Apple's own header doc on `requestAccessForMediaType:`: "the authorization
     * dialog will automatically be shown if the status is AVAuthorizationStatusNotDetermined when
     * creating an AVCaptureDeviceInput" -- exactly what [configureInputsAndOutputs] already does,
     * so the system prompt still appears on first use with no extra code. If access is denied,
     * `AVCaptureDeviceInput.deviceInputWithDevice` returns nil and [configureInputsAndOutputs]
     * already handles that gracefully (see [IosCameraCaptureTest]). */
    fun configure(position: AVCaptureDevicePosition = AVCaptureDevicePositionFront): Boolean {
        session.beginConfiguration()
        configureInputsAndOutputs(position)
        session.commitConfiguration()
        return isConfigured
    }

    private fun configureInputsAndOutputs(position: AVCaptureDevicePosition) {
        val discoverySession = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position,
        )
        val device = discoverySession.devices.firstOrNull() as? AVCaptureDevice ?: return

        val input = memScoped {
            val errorVar = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
            AVCaptureDeviceInput.deviceInputWithDevice(device, errorVar.ptr)
        } ?: return
        if (!session.canAddInput(input)) return
        session.addInput(input)

        val output = AVCaptureVideoDataOutput()
        output.setSampleBufferDelegate(this, outputQueue)
        output.videoSettings = mapOf(kCVPixelBufferPixelFormatTypeKey to kCVPixelFormatType_32BGRA)
        if (!session.canAddOutput(output)) return
        session.addOutput(output)

        isConfigured = true
    }

    /** `startRunning`/`stopRunning` are blocking calls -- Apple's own guidance is to never call
     * them on the main thread, since they can take long enough to visibly stall the UI, and
     * (observed here) can corrupt an unrelated CADisplayLink-driven render loop (Compose's Metal
     * redrawer) via run-loop re-entrancy if the caller is on main. */
    fun start() {
        dispatch_async(outputQueue) { if (!session.running) session.startRunning() }
    }

    fun stop() {
        dispatch_async(outputQueue) { if (session.running) session.stopRunning() }
    }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        onFrame(didOutputSampleBuffer)
    }
}
