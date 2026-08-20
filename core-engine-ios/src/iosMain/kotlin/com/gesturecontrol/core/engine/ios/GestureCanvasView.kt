package com.gesturecontrol.core.engine.ios

import com.gesturecontrol.core.engine.ios.bridge.GCRenderView
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_create
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_draw
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_init_onscreen
import com.gesturecontrol.core.engine.ios.bridge.gc_renderer_present
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_create
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

/** Subclasses the Objective-C++ [GCRenderView] (a CAEAGLLayer-backed UIView -- +layerClass is a
 * class-method override, which Kotlin/Native can't express when subclassing an Objective-C
 * class, so that one piece lives in ios-shim/GCRenderView.mm) to add the actual rendering setup,
 * which Kotlin/Native *can* do as an ordinary instance-method override.
 *
 * Binds to the real on-screen drawable once real bounds are known (layoutSubviews, matching the
 * official Compose-Multiplatform-iOS guidance over UIKitView's own update/onResize hooks, which
 * don't fire with reliably-final layout sizes). [submitInput] is the input side's only way to
 * reach the native scene -- the live camera/MediaPipe/classifier pipeline (owned elsewhere, since
 * it has nothing to do with rendering) calls it once per gesture-derived input command. */
@OptIn(ExperimentalForeignApi::class)
class GestureCanvasView : GCRenderView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val scene = gc_scene_create()
    private val renderer = gc_renderer_create()

    /** The renderer's actual bound pixel size, i.e. the coordinate space [submitInput]'s x/y are
     * normalized against -- 0 until [layoutSubviews] has bound the drawable. */
    var viewportWidth: Int = 0
        private set
    var viewportHeight: Int = 0
        private set

    private val isBound: Boolean get() = viewportWidth > 0 && viewportHeight > 0

    override fun layoutSubviews() {
        super.layoutSubviews()
        bindRendererIfNeeded()
    }

    private fun bindRendererIfNeeded() {
        if (isBound) return
        val hasSize = bounds.useContents { size.width > 0.0 && size.height > 0.0 }
        if (!hasSize) return

        memScoped {
            val widthVar = alloc<kotlinx.cinterop.IntVar>()
            val heightVar = alloc<kotlinx.cinterop.IntVar>()
            val layerPtr = interpretCPointer<CPointed>(layer.objcPtr())
            if (gc_renderer_init_onscreen(renderer, layerPtr, widthVar.ptr, heightVar.ptr)) {
                viewportWidth = widthVar.value
                viewportHeight = heightVar.value
            }
        }
        // Presents an empty (background-only) frame immediately, rather than leaving the
        // CAEAGLLayer showing undefined content until the first real input arrives.
        if (isBound) renderFrame()
    }

    /** Submits one gesture-derived input command to the native scene and repaints -- simplest
     * correct behavior until a real continuous render loop (a later stage's concern, mirroring
     * Android's Choreographer-driven loop) replaces this draw-per-input approach. [state] mirrors
     * gesture_canvas::InputEvent::State's ordinals (see GestureCanvasBridge.h). */
    fun submitInput(x: Float, y: Float, state: Int, pressure: Float, timestampMs: Long) {
        gc_scene_submit_input(scene, x, y, state, pressure, timestampMs)
        if (isBound) renderFrame()
    }

    private fun renderFrame() {
        gc_renderer_draw(renderer, scene)
        gc_renderer_present(renderer)
    }
}
