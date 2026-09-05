package com.gesturecontrol.appdesktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.gesturecontrol.core.engine.desktop.NativeDesktopEngine
import com.gesturecontrol.core.ui.camera.BRUSH_COLOR_OPTIONS
import com.gesturecontrol.core.ui.camera.BrushColorOption
import com.gesturecontrol.core.ui.camera.BrushControls
import com.gesturecontrol.core.ui.camera.BrushSizeOption
import com.gesturecontrol.core.ui.camera.FpsLabel
import com.gesturecontrol.core.ui.camera.GestureStateLabel
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.io.File
import javax.swing.Timer

/**
 * Stage 6 of the desktop port (project plan, §6b): the full pipeline wired end-to-end -- the
 * sidecar's real hand landmarks drive real gesture classification, smoothing, and native
 * rendering, with the same shared `core-ui` composables (`BrushControls`, `FpsLabel`,
 * `GestureStateLabel`) iOS's own `MainViewController` already reuses rather than rebuilding UI
 * chrome a third time.
 */
private class DesktopCanvas(
    private val initialColor: BrushColorOption,
    private val initialSize: BrushSizeOption,
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

    // getWidth()/getHeight() (java.awt.Component) are logical points; on a Retina display the
    // real OpenGL backing store is 2x that. Resizing the viewport to the logical size left the
    // rendered content confined to one quarter of the actual framebuffer (bottom-left, matching
    // exactly what glViewport(0, 0, smallerWidth, smallerHeight) draws into by default) -- the
    // framebuffer* accessors report the real physical pixel dimensions instead.
    override fun paintGL() {
        if (!initialized) return
        NativeDesktopEngine.nativeRendererResize(rendererHandle, framebufferWidth, framebufferHeight)
        NativeDesktopEngine.nativeRendererDraw(rendererHandle, sceneHandle)
        swapBuffers()
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "GestureControl Desktop") {
        var selectedColor by remember { mutableStateOf(BRUSH_COLOR_OPTIONS.first()) }
        var selectedSize by remember { mutableStateOf(BrushSizeOption.MEDIUM) }

        val canvas = remember { DesktopCanvas(selectedColor, selectedSize) }
        remember(canvas) { Timer(16) { canvas.render() }.apply { start() } }

        val pipeline = remember {
            DesktopGesturePipeline(
                pythonExecutable = File(System.getProperty("gesture.canvas.sidecar.python")),
                scriptPath = File(System.getProperty("gesture.canvas.sidecar.script")),
                modelPath = File(System.getProperty("gesture.canvas.sidecar.model")),
                viewportWidth = { canvas.framebufferWidth },
                viewportHeight = { canvas.framebufferHeight },
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

        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
            SwingPanel(modifier = Modifier.fillMaxSize(), factory = { canvas })
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
