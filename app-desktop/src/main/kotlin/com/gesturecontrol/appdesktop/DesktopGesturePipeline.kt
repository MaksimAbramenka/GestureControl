package com.gesturecontrol.appdesktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gesturecontrol.core.engine.desktop.toNativeState
import com.gesturecontrol.core.ml.desktop.DesktopGestureRecognizer
import com.gesturecontrol.core.ml.desktop.HandTrackingSidecarClient
import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.gesture.GestureInputMapper
import com.gesturecontrol.domain.gesture.GestureSmoother
import com.gesturecontrol.domain.hand.HandLandmarks
import com.gesturecontrol.domain.hand.ImageDimensions
import com.gesturecontrol.domain.hand.NormalizedPoint
import com.gesturecontrol.domain.hand.ViewportDimensions
import com.gesturecontrol.domain.hand.toViewportNormalizedPoint
import java.io.File

/**
 * Owns and wires together the live desktop gesture pipeline: the sidecar's landmarks -> gesture
 * classification -> smoothing -> [GestureInputMapper] -> the native scene -- the desktop
 * counterpart to iOS's `GesturePipeline` (`iosShared`), reusing the exact same `domain`-layer
 * pieces. Lives in `app-desktop` rather than `core-ml-desktop`/`core-engine-desktop` individually
 * since it's genuinely the glue between them, the same role `iosShared` plays for iOS.
 *
 * [onResult] fires on [HandTrackingSidecarClient]'s own reader thread, not the AWT/Compose UI
 * thread -- [gestureClass]/[fps] are plain Compose `mutableStateOf`, safe to write from any thread
 * (the snapshot state system handles that), same as iOS's own delegate-queue callback does for its
 * own `mutableStateOf` properties.
 */
class DesktopGesturePipeline(
    pythonExecutable: File,
    scriptPath: File,
    modelPath: File,
    private val viewportWidth: () -> Int,
    private val viewportHeight: () -> Int,
    private val submitInput: (x: Float, y: Float, state: Int, pressure: Float, timestampMs: Long) -> Unit,
) {
    private val gestureSmoother = GestureSmoother()
    private val gestureInputMapper = GestureInputMapper()
    private var lastResultTimestampMs: Long? = null

    private val client = HandTrackingSidecarClient(
        pythonExecutable = pythonExecutable,
        scriptPath = scriptPath,
        modelPath = modelPath,
        onResult = ::handleResult,
    )

    var gestureClass: GestureClass? by mutableStateOf(null)
        private set

    var fingertip: NormalizedPoint? by mutableStateOf(null)
        private set

    var fps: Float by mutableStateOf(0f)
        private set

    fun start() = client.start()

    fun stop() = client.stop()

    private fun handleResult(landmarks: HandLandmarks?, imageDimensions: ImageDimensions, timestampMs: Long) {
        updateFps(timestampMs)

        val smoothedGestureClass = landmarks?.let(DesktopGestureRecognizer::recognize)
            ?.gestureClass?.let(gestureSmoother::smooth)
        gestureClass = smoothedGestureClass
        val fingertip = landmarks?.indexFingertip?.let { viewportFingertip(it, imageDimensions) }
        this.fingertip = fingertip

        gestureInputMapper.map(smoothedGestureClass, fingertip, timestampMs).forEach { command ->
            submitInput(command.x, command.y, command.state.toNativeState(), command.pressure, command.timestampMs)
        }
    }

    private fun updateFps(timestampMs: Long) {
        val last = lastResultTimestampMs
        lastResultTimestampMs = timestampMs
        val deltaMs = if (last == null) return else timestampMs - last
        if (deltaMs <= 0) return

        val instantFps = 1000f / deltaMs
        fps = if (fps == 0f) instantFps else fps * 0.9f + instantFps * 0.1f
    }

    private fun viewportFingertip(point: NormalizedPoint, imageDimensions: ImageDimensions): NormalizedPoint? {
        val width = viewportWidth()
        val height = viewportHeight()
        if (width <= 0 || height <= 0) return null

        return point.toViewportNormalizedPoint(
            imageDimensions = imageDimensions,
            viewportDimensions = ViewportDimensions(width.toFloat(), height.toFloat()),
            mirrored = false,
        )
    }
}
