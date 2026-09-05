package com.gesturecontrol.core.engine.desktop

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR
import org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT
import org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE
import org.lwjgl.glfw.GLFW.GLFW_TRUE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import org.lwjgl.opengl.GL
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

// State ordinals mirror gesture_canvas::InputEvent::State -- see DesktopRendererBridge.cpp.
private const val DRAW_START = 2
private const val DRAW_MOVE = 3
private const val DRAW_END = 4

/**
 * Proves the shared native core's rendering path (SceneGraph -> StrokeRenderer ->
 * RibbonTessellator, unchanged from Android/iOS) also produces correct pixels through a *real*
 * desktop OpenGL context -- no ANGLE, no GLES emulation (see the project plan, §6b Stage 2). A
 * hidden GLFW window supplies the context; LWJGL owns window/context lifecycle entirely from
 * Kotlin, unlike Android/iOS where the native side creates its own EGL/EAGL context.
 */
class DesktopRendererBridgeTest {
    companion object {
        private var window: Long = 0

        @JvmStatic
        @BeforeAll
        fun setUpGlfw() {
            check(glfwInit()) { "glfwInit failed" }
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)

            window = glfwCreateWindow(64, 64, "gesture-canvas-desktop-test", 0, 0)
            check(window != 0L) { "glfwCreateWindow failed" }
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
        }

        @JvmStatic
        @AfterAll
        fun tearDownGlfw() {
            if (window != 0L) glfwDestroyWindow(window)
            glfwTerminate()
        }
    }

    @Test
    fun `drawing a red stroke produces a red pixel in the captured frame`() {
        val width = 64
        val height = 64
        val scene = NativeDesktopEngine.nativeSceneCreate()
        val renderer = NativeDesktopEngine.nativeRendererCreate()
        try {
            NativeDesktopEngine.nativeSceneSetBrushColor(scene, 1.0f, 0.0f, 0.0f)
            NativeDesktopEngine.nativeSceneSetBrushSize(scene, 0.3f)
            NativeDesktopEngine.nativeSceneSubmitInput(scene, 0.2f, 0.5f, DRAW_START, 1.0f, 0L)
            NativeDesktopEngine.nativeSceneSubmitInput(scene, 0.5f, 0.5f, DRAW_MOVE, 1.0f, 10L)
            NativeDesktopEngine.nativeSceneSubmitInput(scene, 0.8f, 0.5f, DRAW_END, 1.0f, 20L)

            assertTrue(NativeDesktopEngine.nativeRendererInit(renderer), "renderer init failed")
            NativeDesktopEngine.nativeRendererResize(renderer, width, height)
            NativeDesktopEngine.nativeRendererDraw(renderer, scene)

            val pixels = NativeDesktopEngine.nativeRendererCapture(width, height)
            assertNotNull(pixels, "capture failed")

            // Scans for a saturated red pixel anywhere, matching RendererBridgeTest's (iOS) own
            // reasoning: PointSmoother intentionally lags behind large jumps, so exactly where
            // the stroke lands isn't this test's concern, only that the renderer draws the color
            // it's given, somewhere, through a real desktop GL context.
            var foundRedPixel = false
            for (pixelIndex in 0 until width * height) {
                val offset = pixelIndex * 4
                val r = pixels[offset].toInt() and 0xFF
                val g = pixels[offset + 1].toInt() and 0xFF
                val b = pixels[offset + 2].toInt() and 0xFF
                val a = pixels[offset + 3].toInt() and 0xFF
                if (r > 200 && g < 50 && b < 50 && a > 200) {
                    foundRedPixel = true
                    break
                }
            }
            if (!foundRedPixel) fail("expected at least one saturated red pixel in the captured frame")
        } finally {
            NativeDesktopEngine.nativeRendererDestroy(renderer)
            NativeDesktopEngine.nativeSceneDestroy(scene)
        }
    }
}
