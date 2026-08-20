package com.gesturecontrol.core.engine.ios

import com.gesturecontrol.core.engine.ios.bridge.GCRenderView
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_create
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_draw
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_init_onscreen
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_present
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_create
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_set_brush_color
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_set_brush_size
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_submit_input
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreGraphics.CGRectMake

// State ordinals mirror gesture_canvas::InputEvent::State.
private const val DRAW_START = 2
private const val DRAW_MOVE = 3
private const val DRAW_END = 4

/** Subclasses the Objective-C++ [GCRenderView] (a CAEAGLLayer-backed UIView -- +layerClass is a
 * class-method override, which Kotlin/Native can't express when subclassing an Objective-C
 * class, so that one piece lives in ios-shim/GCRenderView.mm) to add the actual rendering setup,
 * which Kotlin/Native *can* do as an ordinary instance-method override.
 *
 * Stage 6c's proof that Stage 3's offscreen-verified renderer also works on a real on-screen
 * drawable: binds once real bounds are known (layoutSubviews, matching the official
 * Compose-Multiplatform-iOS guidance over UIKitView's own update/onResize hooks, which don't fire
 * with reliably-final layout sizes) and draws one hardcoded stroke. Live camera-driven drawing is
 * a later stage's concern. */
@OptIn(ExperimentalForeignApi::class)
class GestureCanvasView : GCRenderView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val scene = gc_scene_create()
    private val renderer = gc_renderer_create()
    private var boundToDrawable = false

    override fun layoutSubviews() {
        super.layoutSubviews()
        renderIfReady()
    }

    private fun renderIfReady() {
        if (!boundToDrawable) {
            val hasSize = bounds.useContents { size.width > 0.0 && size.height > 0.0 }
            if (!hasSize) return

            val bound = memScoped {
                val widthVar = alloc<kotlinx.cinterop.IntVar>()
                val heightVar = alloc<kotlinx.cinterop.IntVar>()
                val layerPtr = interpretCPointer<CPointed>(layer.objcPtr())
                gc_renderer_init_onscreen(renderer, layerPtr, widthVar.ptr, heightVar.ptr) &&
                    widthVar.value > 0 && heightVar.value > 0
            }
            if (!bound) return
            boundToDrawable = true
        }

        gc_scene_set_brush_color(scene, 1.0f, 0.2f, 0.2f)
        gc_scene_set_brush_size(scene, 0.05f)
        gc_scene_submit_input(scene, 0.2f, 0.5f, DRAW_START, 1.0f, 0L)
        gc_scene_submit_input(scene, 0.5f, 0.3f, DRAW_MOVE, 1.0f, 16L)
        gc_scene_submit_input(scene, 0.8f, 0.5f, DRAW_END, 1.0f, 32L)

        gc_renderer_draw(renderer, scene)
        gc_renderer_present(renderer)
    }
}
