package com.gesturecontrol.appdesktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.gesturecontrol.core.engine.desktop.NativeDesktopEngine
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import javax.swing.Timer

// State ordinals mirror gesture_canvas::InputEvent::State -- see DesktopRendererBridge.cpp.
private const val DRAW_START = 2
private const val DRAW_MOVE = 3
private const val DRAW_END = 4

/**
 * Stage 3 of the desktop port (see the project plan, §6b): proves the same native renderer
 * Stage 2 verified against a hidden GLFW window also works embedded in a real, visible Compose
 * Desktop window via AWTGLCanvas/SwingPanel -- the AWT-integrated way to get a real desktop
 * OpenGL context, as opposed to GLFW's own standalone-window context (used for the headless
 * DesktopRendererBridgeTest instead, where there's no Swing tree to embed into). Manually feeds
 * one test stroke -- no camera/gesture pipeline yet, that's Stage 4+.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "GestureControl Desktop (Stage 3 smoke test)") {
        val canvas = remember { createTestStrokeCanvas() }
        // AWTGLCanvas.render() (initGL() once, then paintGL()) is not wired to AWT's own
        // paint/repaint mechanism -- confirmed by inspecting the class's public API (a separate
        // render() method exists precisely because the caller drives the render loop, the same
        // "explicit render entry point, not an implicit callback" design Android/iOS both already
        // use here). A Swing Timer stands in for the real per-frame trigger a live camera/gesture
        // pipeline will provide from Stage 4 onward.
        remember(canvas) {
            Timer(16) { canvas.render() }.apply { start() }
        }
        SwingPanel(modifier = Modifier.fillMaxSize(), factory = { canvas })
    }
}

private fun createTestStrokeCanvas(): AWTGLCanvas {
    val glData = GLData().apply {
        majorVersion = 3
        minorVersion = 3
        profile = GLData.Profile.CORE
    }
    return object : AWTGLCanvas(glData) {
        private var sceneHandle = 0L
        private var rendererHandle = 0L
        private var initialized = false

        override fun initGL() {
            GL.createCapabilities()
            sceneHandle = NativeDesktopEngine.nativeSceneCreate()
            rendererHandle = NativeDesktopEngine.nativeRendererCreate()

            NativeDesktopEngine.nativeSceneSetBrushColor(sceneHandle, 1.0f, 0.0f, 0.0f)
            NativeDesktopEngine.nativeSceneSetBrushSize(sceneHandle, 0.05f)
            NativeDesktopEngine.nativeSceneSubmitInput(sceneHandle, 0.2f, 0.5f, DRAW_START, 1.0f, 0L)
            NativeDesktopEngine.nativeSceneSubmitInput(sceneHandle, 0.5f, 0.3f, DRAW_MOVE, 1.0f, 10L)
            NativeDesktopEngine.nativeSceneSubmitInput(sceneHandle, 0.8f, 0.5f, DRAW_END, 1.0f, 20L)

            initialized = NativeDesktopEngine.nativeRendererInit(rendererHandle)
        }

        override fun paintGL() {
            if (!initialized) return
            NativeDesktopEngine.nativeRendererResize(rendererHandle, width, height)
            NativeDesktopEngine.nativeRendererDraw(rendererHandle, sceneHandle)
            swapBuffers()
        }
    }
}
