package com.gesturecontrol.core.camera.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretObjCPointer
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
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSString
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
        // Both sides of this entry need bridging, confirmed via real Kotlin type-checking, not
        // guessing: kCVPixelBufferPixelFormatTypeKey is a raw CPointer<__CFString> (a
        // CoreFoundation constant, never auto-bridged to NSString despite CFString/NSString being
        // toll-free bridged at the ObjC runtime level) -- putting a bare CPointer key or a raw
        // OSType (UInt32) value into the map leaves them unboxed, so AVFoundation silently drops
        // the whole entry at runtime ("unsupported (ignored) keys", confirmed on a real device)
        // and the output ends up in some other default pixel format MediaPipe's LIVE_STREAM
        // detection can't correctly interpret.
        val pixelFormatKey = interpretObjCPointer<NSString>(kCVPixelBufferPixelFormatTypeKey!!.rawValue)
        output.videoSettings = mapOf(pixelFormatKey to NSNumber(unsignedInt = kCVPixelFormatType_32BGRA))
        if (!session.canAddOutput(output)) return
        session.addOutput(output)

        // MPPImage(sampleBuffer:) always reports orientation .up (no rotation applied), so the
        // raw sensor buffer itself must already be upright, or MediaPipe processes a sideways
        // frame -- confirmed on a real device as exactly the cause of wildly unreliable hand
        // detection (a real image, but the model rarely recognizes a 90-degree-rotated hand). The
        // connection only exists after addOutput; only AVCaptureVideoDataOutput/DepthDataOutput
        // physically rotate buffers to match, which is exactly what's needed here. (Confirmed via
        // a real-device isolation test that this is NOT the cause of the separate periodic ~10s
        // camera stall investigated separately -- that persisted identically with this disabled.)
        (output.connections.firstOrNull() as? AVCaptureConnection)?.videoOrientation =
            AVCaptureVideoOrientationPortrait

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
