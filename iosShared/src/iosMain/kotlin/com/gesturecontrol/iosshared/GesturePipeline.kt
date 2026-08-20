package com.gesturecontrol.iosshared

import com.gesturecontrol.core.camera.ios.IosCameraCapture
import com.gesturecontrol.core.engine.ios.GestureCanvasView
import com.gesturecontrol.core.engine.ios.toNativeState
import com.gesturecontrol.core.ml.ios.IosGestureRecognizer
import com.gesturecontrol.core.ml.ios.IosLiveHandLandmarker
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerResult
import com.gesturecontrol.domain.gesture.GestureInputMapper
import com.gesturecontrol.domain.gesture.GestureSmoother
import com.gesturecontrol.domain.hand.ImageDimensions
import com.gesturecontrol.domain.hand.NormalizedPoint
import com.gesturecontrol.domain.hand.ViewportDimensions
import com.gesturecontrol.domain.hand.toViewportNormalizedPoint
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth

/**
 * Owns and wires together the live gesture pipeline: camera frames -> MediaPipe hand tracking
 * (LIVE_STREAM) -> gesture classification -> smoothing -> [GestureInputMapper] -> the native
 * scene. Driven end-to-end by the camera's own frame callback and MediaPipe's own async result
 * callback -- there's no Compose recomposition loop to hook into here the way Android's
 * SideEffect-per-recomposition pipeline works, so this class *is* the loop.
 */
@OptIn(ExperimentalForeignApi::class)
class GesturePipeline(
    private val canvasView: GestureCanvasView,
    modelAssetPath: String,
) {
    private val gestureSmoother = GestureSmoother()
    private val gestureInputMapper = GestureInputMapper()

    /** Camera resolution doesn't change mid-session, so this is captured once from the first
     * frame rather than threaded through every async callback. */
    private var imageDimensions: ImageDimensions? = null

    private val landmarker = IosLiveHandLandmarker(modelAssetPath, ::handleResult)
    private val camera = IosCameraCapture(::handleFrame)

    fun start() {
        if (!camera.isConfigured) camera.configure()
        camera.start()
    }

    fun stop() = camera.stop()

    private fun handleFrame(sampleBuffer: CMSampleBufferRef?) {
        if (sampleBuffer == null) return
        if (imageDimensions == null) {
            imageDimensions = readImageDimensions(sampleBuffer)
        }
        val timestampMs = (CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) * 1000).toLong()
        landmarker.detectAsync(sampleBuffer, timestampMs)
    }

    private fun readImageDimensions(sampleBuffer: CMSampleBufferRef): ImageDimensions? {
        val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return null
        val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
        val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
        return if (width > 0 && height > 0) ImageDimensions(width, height) else null
    }

    // Called on MediaPipe's own private serial dispatch queue, not the camera's output queue or
    // the main thread. gc_scene_submit_input/gc_renderer_present (via canvasView.submitInput) are
    // plain C calls with no UIKit/main-thread requirement of their own, so no thread hop is
    // needed here -- unlike touching AppKit/SwiftUI state, EAGL/GL calls are fine off the main
    // thread as long as the same thread that made the context current keeps using it consistently
    // per call, which submitInput's makeCurrent-per-call implementation already guarantees.
    private fun handleResult(result: MPPHandLandmarkerResult?, timestampMs: Long) {
        val recognition = result?.let { IosGestureRecognizer.recognizeFirstHand(it) }
        val gestureClass = recognition?.classifiedGesture?.gestureClass?.let(gestureSmoother::smooth)
        val fingertip = viewportFingertip(recognition?.landmarks?.indexFingertip)

        gestureInputMapper.map(gestureClass, fingertip, timestampMs).forEach { command ->
            canvasView.submitInput(
                x = command.x,
                y = command.y,
                state = command.state.toNativeState(),
                pressure = command.pressure,
                timestampMs = command.timestampMs,
            )
        }
    }

    private fun viewportFingertip(point: NormalizedPoint?): NormalizedPoint? {
        if (point == null) return null
        val image = imageDimensions ?: return null
        if (canvasView.viewportWidth <= 0 || canvasView.viewportHeight <= 0) return null

        return point.toViewportNormalizedPoint(
            imageDimensions = image,
            viewportDimensions = ViewportDimensions(
                width = canvasView.viewportWidth.toFloat(),
                height = canvasView.viewportHeight.toFloat(),
            ),
            mirrored = false,
        )
    }
}
