package com.gesturecontrol.core.ml.ios

import com.gesturecontrol.core.ml.ios.mediapipe.MPPBaseOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPDelegate
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
import platform.Foundation.NSProcessInfo
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
        val baseOptions = MPPBaseOptions().apply {
            this.modelAssetPath = modelAssetPath
            // Without an explicit delegate, MediaPipe falls back to CPU-only XNNPACK, which is
            // measurably slower for this model on real device hardware than GPU delegation. The
            // Simulator's Metal delegate can't actually do this ("Apple Software Renderer", no
            // real GPU passthrough) and fails calculator-graph init outright, so it needs CPU too
            // -- confirmed via SIMULATOR_DEVICE_NAME, the env var Xcode/simctl always sets for a
            // Simulator-hosted process and never sets for a real device.
            this.delegate = if (isRunningOnSimulator()) MPPDelegate.MPPDelegateCPU else MPPDelegate.MPPDelegateGPU
        }
        val options = MPPHandLandmarkerOptions().apply {
            this.baseOptions = baseOptions
            this.runningMode = MPPRunningMode.MPPRunningModeLiveStream
            this.numHands = 1L
            this.handLandmarkerLiveStreamDelegate = this@IosLiveHandLandmarker
            // MediaPipe's hand landmarker only runs its expensive full palm-detection pass
            // periodically, tracking cheaply frame-to-frame once a hand is acquired -- confirmed
            // on a real device (iPhone XS/A12) as the actual cause of "recognition is very slow,
            // but fast once it catches on": at the default (stricter) confidence thresholds,
            // initial acquisition rarely clears the bar under normal handheld framing/lighting,
            // so the app waits through several full-redetection cycles before ever tracking a
            // hand. Lowering these (MediaPipe's own defaults are undocumented here, but visibly
            // strict) trades a bit of false-positive risk for reliably faster acquisition --
            // reasonable for a drawing app where GestureSmoother's majority-vote window already
            // absorbs brief misclassifications.
            this.minHandDetectionConfidence = 0.3f
            this.minHandPresenceConfidence = 0.3f
            this.minTrackingConfidence = 0.3f
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

    private fun isRunningOnSimulator(): Boolean =
        NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
}
