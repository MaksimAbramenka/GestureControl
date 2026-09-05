package com.gesturecontrol.appdesktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.gesturecontrol.core.engine.desktop.NativeDesktopEngine
import com.gesturecontrol.core.ui.camera.BRUSH_COLOR_OPTIONS
import com.gesturecontrol.core.ui.camera.BrushColorOption
import com.gesturecontrol.core.ui.camera.BrushControls
import com.gesturecontrol.core.ui.camera.BrushSizeOption
import com.gesturecontrol.core.ui.camera.FpsLabel
import com.gesturecontrol.core.ui.camera.GestureCursorOverlay
import com.gesturecontrol.core.ui.camera.GestureStateLabel
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.io.File
import javax.swing.Timer

/**
 * Stage 6 of the desktop port (project plan, §6b), revised after live testing: the native scene
 * renders into an offscreen FBO instead of a visible window's own default framebuffer, and the
 * captured pixels are shown via a plain Compose `Image`. `AWTGLCanvas` (kept only to own a real
 * OpenGL context) is a heavyweight AWT component -- by design, those always draw on top of every
 * other Compose element in the same window regardless of code/z-order, an open JetBrains platform
 * limitation (JetBrains/compose-multiplatform#3739) with no reliable fix -- the documented
 * `compose.interop.blending` escape hatch was tried directly and caused a real JVM-level crash
 * (SIGSEGV inside libjvm.dylib itself, not a catchable Kotlin exception), not just an incomplete
 * fix. Rendering offscreen and displaying a captured bitmap sidesteps the whole problem: nothing
 * heavyweight is left in the visible tree, so `FpsLabel`, `GestureStateLabel`, `BrushControls`,
 * and `GestureCursorOverlay` all render (and receive clicks) correctly. The real cost, stated
 * plainly: a per-frame CPU pixel-readback-and-reupload round trip, not Android/iOS's direct
 * hardware-composited surface.
 */
private class DesktopCanvas(
    private val initialColor: BrushColorOption,
    private val initialSize: BrushSizeOption,
    private val targetSize: () -> IntSize,
    private val onFrameCaptured: (ByteArray, Int, Int) -> Unit,
) : AWTGLCanvas(
    GLData().apply {
        majorVersion = 3
        minorVersion = 3
        profile = GLData.Profile.CORE
    },
) {
    var sceneHandle = 0L
        private set
    var rendererHandle = 0L
        private set
    var initialized = false
        private set

    private var offscreenWidth = 0
    private var offscreenHeight = 0

    override fun initGL() {
        GL.createCapabilities()
        sceneHandle = NativeDesktopEngine.nativeSceneCreate()
        rendererHandle = NativeDesktopEngine.nativeRendererCreate()
        // Explicit, not relying on SceneGraph's own hardcoded defaults happening to match --
        // mirrors iOS's MainViewController applying the initial selection right after creating
        // its own GestureCanvasView, rather than waiting for the first carousel tap.
        NativeDesktopEngine.nativeSceneSetBrushColor(sceneHandle, initialColor.r, initialColor.g, initialColor.b)
        NativeDesktopEngine.nativeSceneSetBrushSize(sceneHandle, initialSize.size)
        initialized = NativeDesktopEngine.nativeRendererInit(rendererHandle)
    }

    override fun paintGL() {
        if (!initialized) return
        val size = targetSize()
        val width = size.width.coerceAtLeast(1)
        val height = size.height.coerceAtLeast(1)

        if (width != offscreenWidth || height != offscreenHeight) {
            if (!NativeDesktopEngine.nativeRendererCreateOffscreenTarget(rendererHandle, width, height)) return
            offscreenWidth = width
            offscreenHeight = height
        }

        NativeDesktopEngine.nativeRendererResize(rendererHandle, width, height)
        NativeDesktopEngine.nativeRendererDraw(rendererHandle, sceneHandle)
        NativeDesktopEngine.nativeRendererCapture(width, height)?.let { onFrameCaptured(it, width, height) }
        // No swapBuffers() -- this canvas's own default framebuffer is never shown to the user.
    }
}

private fun rgbaBytesToImageBitmap(pixels: ByteArray, width: Int, height: Int): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL))
    bitmap.installPixels(pixels)
    return bitmap.asComposeImageBitmap()
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "GestureControl Desktop") {
        var selectedColor by remember { mutableStateOf(BRUSH_COLOR_OPTIONS.first()) }
        var selectedSize by remember { mutableStateOf(BrushSizeOption.MEDIUM) }
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        var frameBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

        val canvas = remember {
            DesktopCanvas(
                initialColor = selectedColor,
                initialSize = selectedSize,
                targetSize = { canvasSize },
                onFrameCaptured = { pixels, width, height ->
                    frameBitmap = rgbaBytesToImageBitmap(pixels, width, height)
                },
            )
        }
        val renderTimer = remember(canvas) { Timer(16) { canvas.render() }.apply { start() } }
        // Without this, the Timer keeps firing canvas.render() -- calling straight into JNI --
        // after window close starts tearing down the AWT peer/GL context underneath it, which
        // crashed with a real SIGSEGV inside libjvm.dylib on shutdown (confirmed live, not
        // theoretical). Declared before the pipeline's own DisposableEffect below so Compose's
        // reverse-order disposal stops the pipeline (no more submitInput calls) first, then
        // rendering -- input stops before the thing it feeds does, not the other way around.
        DisposableEffect(renderTimer) {
            onDispose { renderTimer.stop() }
        }

        val pipeline = remember {
            DesktopGesturePipeline(
                pythonExecutable = File(System.getProperty("gesture.canvas.sidecar.python")),
                scriptPath = File(System.getProperty("gesture.canvas.sidecar.script")),
                modelPath = File(System.getProperty("gesture.canvas.sidecar.model")),
                viewportWidth = { canvasSize.width },
                viewportHeight = { canvasSize.height },
                submitInput = { x, y, state, pressure, timestampMs ->
                    // Landmarks can start arriving before initGL() has run (the sidecar's own
                    // camera+model startup and this canvas's first Timer-driven render race each
                    // other) -- sceneHandle is a raw native pointer under the hood, and calling
                    // into it before nativeSceneCreate() has actually run would crash, not just
                    // no-op, so this guard is load-bearing, not defensive-for-its-own-sake.
                    if (canvas.initialized) {
                        NativeDesktopEngine.nativeSceneSubmitInput(
                            canvas.sceneHandle,
                            x,
                            y,
                            state,
                            pressure,
                            timestampMs,
                        )
                    }
                },
            )
        }
        DisposableEffect(pipeline) {
            pipeline.start()
            onDispose { pipeline.stop() }
        }

        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.DarkGray)
                .onSizeChanged { canvasSize = it },
        ) {
            // Tiny and never actually shown -- purely to own a real OpenGL context via
            // AWTGLCanvas; see this file's own top comment for why it can't be the visible
            // surface directly.
            SwingPanel(modifier = Modifier.size(1.dp), factory = { canvas })

            frameBitmap?.let { bitmap ->
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
            }

            GestureCursorOverlay(
                fingertip = pipeline.fingertip,
                gestureClass = pipeline.gestureClass,
                brushColor = selectedColor.composeColor,
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FpsLabel(fps = pipeline.fps)
                GestureStateLabel(gestureClass = pipeline.gestureClass)
            }
            BrushControls(
                selectedColor = selectedColor,
                selectedSize = selectedSize,
                onSelectColor = { option ->
                    selectedColor = option
                    if (canvas.initialized) {
                        NativeDesktopEngine.nativeSceneSetBrushColor(canvas.sceneHandle, option.r, option.g, option.b)
                    }
                },
                onSelectSize = { option ->
                    selectedSize = option
                    if (canvas.initialized) {
                        NativeDesktopEngine.nativeSceneSetBrushSize(canvas.sceneHandle, option.size)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
        }
    }
}
