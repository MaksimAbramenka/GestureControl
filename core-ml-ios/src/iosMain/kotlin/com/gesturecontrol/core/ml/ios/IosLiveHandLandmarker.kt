package com.gesturecontrol.core.ml.ios

import com.gesturecontrol.core.ml.ios.mediapipe.MPPBaseOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarker
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerLiveStreamDelegateProtocol
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerResult
import com.gesturecontrol.core.ml.ios.mediapipe.MPPImage
import com.gesturecontrol.core.ml.ios.mediapipe.MPPRunningMode
import kotlinx.cinterop.ExperimentalForeignApi
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
@OptIn(ExperimentalForeignApi::class)
class IosLiveHandLandmarker(
    modelAssetPath: String,
    private val onResult: (MPPHandLandmarkerResult?, Long) -> Unit,
) : NSObject(), MPPHandLandmarkerLiveStreamDelegateProtocol {

    private val landmarker: MPPHandLandmarker?

    init {
        val baseOptions = MPPBaseOptions().apply { this.modelAssetPath = modelAssetPath }
        val options = MPPHandLandmarkerOptions().apply {
            this.baseOptions = baseOptions
            this.runningMode = MPPRunningMode.MPPRunningModeLiveStream
            this.numHands = 1L
            this.handLandmarkerLiveStreamDelegate = this@IosLiveHandLandmarker
        }
        landmarker = MPPHandLandmarker(options = options, error = null)
    }

    /** False if the model failed to load (e.g. bad [modelAssetPath]) -- mirrors
     * [MPPHandLandmarker]'s own failable initializer instead of throwing, since a missing model
     * on a real device is a recoverable condition the caller should surface, not a crash. */
    val isReady: Boolean get() = landmarker != null

    /** Converts [sampleBuffer] to an `MPImage` and submits it for detection. Returns false if
     * either the conversion or the submission failed (e.g. [isReady] is false); the real result,
     * if any, arrives later via [onResult]. */
    fun detectAsync(sampleBuffer: CMSampleBufferRef, timestampMs: Long): Boolean {
        val image = MPPImage(sampleBuffer = sampleBuffer, error = null)
        return landmarker?.detectAsyncImage(image, timestampInMilliseconds = timestampMs, error = null) ?: false
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
