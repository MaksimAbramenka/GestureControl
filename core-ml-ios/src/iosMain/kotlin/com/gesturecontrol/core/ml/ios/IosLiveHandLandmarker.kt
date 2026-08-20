package com.gesturecontrol.core.ml.ios

import com.gesturecontrol.core.ml.ios.mediapipe.MPPBaseOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarker
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerLiveStreamDelegateProtocol
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerResult
import com.gesturecontrol.core.ml.ios.mediapipe.MPPImage
import com.gesturecontrol.core.ml.ios.mediapipe.MPPRunningMode
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * Owns a MediaPipe HandLandmarker running in LIVE_STREAM mode against live camera frames -- the
 * counterpart to [IosGestureRecognizer], which only classifies an already-produced result.
 *
 * Results arrive asynchronously via [MPPHandLandmarkerLiveStreamDelegateProtocol], on a private
 * serial dispatch queue MediaPipe creates for itself (per its own header doc comment) -- not the
 * camera's output queue and not the main thread -- so [onResult] must hop to whatever thread the
 * caller actually needs.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosLiveHandLandmarker(
    modelAssetPath: String,
    private val onResult: (MPPHandLandmarkerResult?, Long) -> Unit,
) : NSObject(), MPPHandLandmarkerLiveStreamDelegateProtocol {

    private val landmarker: MPPHandLandmarker

    init {
        val baseOptions = MPPBaseOptions().apply { this.modelAssetPath = modelAssetPath }
        val options = MPPHandLandmarkerOptions().apply {
            this.baseOptions = baseOptions
            this.runningMode = MPPRunningMode.MPPRunningModeLiveStream
            this.numHands = 1L
            this.handLandmarkerLiveStreamDelegate = this@IosLiveHandLandmarker
        }
        // modelAssetPath is a committed build asset (see MainViewController.handLandmarkerModelPath),
        // not user input -- a failure here is a real bug (bad bundling, wrong MediaPipe API usage),
        // so this fails fast with the real reason rather than degrading gracefully.
        landmarker = memScoped {
            val errorVar = alloc<ObjCObjectVar<NSError?>>()
            try {
                MPPHandLandmarker(options = options, error = errorVar.ptr)
            } catch (npe: NullPointerException) {
                // MPPHandLandmarker's Kotlin binding is (incorrectly) non-nullable, so a nil
                // return throws here instead of surfacing as null -- errorVar is still populated
                // by the underlying Objective-C call by this point, so recover the real reason.
                val reason = errorVar.value?.localizedDescription ?: "no NSError provided"
                throw IllegalStateException("MPPHandLandmarker init failed: $reason", npe)
            }
        }
    }

    /** Converts [sampleBuffer] to an `MPImage` and submits it for detection. Returns false if
     * submission failed; the real result, if any, arrives later via [onResult]. */
    fun detectAsync(sampleBuffer: CMSampleBufferRef, timestampMs: Long): Boolean {
        val image = MPPImage(sampleBuffer = sampleBuffer, error = null)
        return landmarker.detectAsyncImage(image, timestampInMilliseconds = timestampMs, error = null)
    }

    override fun handLandmarker(
        handLandmarker: MPPHandLandmarker,
        didFinishDetectionWithResult: MPPHandLandmarkerResult?,
        timestampInMilliseconds: Long,
        error: NSError?,
    ) {
        onResult(didFinishDetectionWithResult, timestampInMilliseconds)
    }
}
