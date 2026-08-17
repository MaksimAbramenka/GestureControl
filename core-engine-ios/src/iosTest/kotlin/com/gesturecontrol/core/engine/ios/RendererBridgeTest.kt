package com.gesturecontrol.core.engine.ios

import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_capture
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_create
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_destroy
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_draw
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_init
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_create
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_destroy
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_set_brush_color
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_set_brush_size
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_submit_input
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlin.test.Test
import kotlin.test.assertTrue

// State ordinals mirror gesture_canvas::InputEvent::State.
private const val DRAW_START = 2
private const val DRAW_MOVE = 3
private const val DRAW_END = 4

// Proves the OpenGL ES rendering pipeline itself works on iOS -- StrokeRenderer/RibbonTessellator
// compiled against iOS's OpenGLES headers, driven through an offscreen EAGLContext, actually
// produces the expected pixels. This is the rendering-port proof for Stage 3, ahead of wiring a
// live camera/UI in later stages.
@OptIn(ExperimentalForeignApi::class)
class RendererBridgeTest {
    @Test
    fun `drawing a red stroke produces a red pixel in the captured frame`() {
        val width = 64
        val height = 64
        val scene = gc_scene_create()
        val renderer = gc_renderer_create()
        try {
            gc_scene_set_brush_color(scene, 1.0f, 0.0f, 0.0f)
            gc_scene_set_brush_size(scene, 0.3f)
            gc_scene_submit_input(scene, 0.2f, 0.5f, DRAW_START, 1.0f, 0L)
            gc_scene_submit_input(scene, 0.5f, 0.5f, DRAW_MOVE, 1.0f, 10L)
            gc_scene_submit_input(scene, 0.8f, 0.5f, DRAW_END, 1.0f, 20L)

            assertTrue(gc_renderer_init(renderer, width, height), "renderer init failed")
            gc_renderer_draw(renderer, scene)

            memScoped {
                val bufferSize = width * height * 4
                val pixels = allocArray<UByteVar>(bufferSize)
                assertTrue(gc_renderer_capture(renderer, pixels, bufferSize), "capture failed")

                // Scans for a saturated red pixel anywhere, rather than assuming a fixed location:
                // PointSmoother intentionally lags behind large jumps between closely-spaced
                // timestamps, so exactly where the stroke lands isn't this test's concern -- only
                // that the renderer actually draws the color it's given, somewhere.
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
                assertTrue(foundRedPixel, "expected at least one saturated red pixel in the captured frame")
            }
        } finally {
            gc_renderer_destroy(renderer)
            gc_scene_destroy(scene)
        }
    }
}
