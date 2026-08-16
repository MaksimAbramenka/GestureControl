package com.gesturecontrol.core.engine.ios

import com.gesturecontrol.core.engine.ios.bridge.gc_scene_can_redo
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_can_undo
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_create
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_destroy
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_set_brush_color
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_set_brush_size
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_stroke_count
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_stroke_g
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_stroke_point_count
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_stroke_r
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_stroke_width
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_submit_input
import com.gesturecontrol.core.engine.ios.bridge.gc_scene_undo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// State ordinals mirror gesture_canvas::InputEvent::State.
private const val DRAW_START = 2
private const val DRAW_MOVE = 3
private const val DRAW_END = 4

// Proves the Kotlin/Native cinterop boundary into the shared C++ core actually works -- the same
// SceneGraph logic the GoogleTest suite verifies on the host, now reachable from Kotlin on iOS.
@OptIn(ExperimentalForeignApi::class)
class GestureCanvasBridgeTest {
    @Test
    fun `draw start then move then end produces one stroke with three points`() {
        val scene = gc_scene_create()
        try {
            gc_scene_submit_input(scene, 0.1f, 0.1f, DRAW_START, 1.0f, 0L)
            gc_scene_submit_input(scene, 0.2f, 0.2f, DRAW_MOVE, 1.0f, 10L)
            gc_scene_submit_input(scene, 0.3f, 0.3f, DRAW_END, 1.0f, 20L)

            assertEquals(1, gc_scene_stroke_count(scene))
            assertEquals(3, gc_scene_stroke_point_count(scene, 0))
        } finally {
            gc_scene_destroy(scene)
        }
    }

    @Test
    fun `brush color and size apply to a new stroke`() {
        val scene = gc_scene_create()
        try {
            gc_scene_set_brush_color(scene, 1.0f, 0.0f, 0.0f)
            gc_scene_set_brush_size(scene, 0.05f)
            gc_scene_submit_input(scene, 0.5f, 0.5f, DRAW_START, 1.0f, 0L)
            gc_scene_submit_input(scene, 0.5f, 0.5f, DRAW_END, 1.0f, 10L)

            assertEquals(1.0f, gc_scene_stroke_r(scene, 0))
            assertEquals(0.0f, gc_scene_stroke_g(scene, 0))
            assertEquals(0.05f, gc_scene_stroke_width(scene, 0))
        } finally {
            gc_scene_destroy(scene)
        }
    }

    @Test
    fun `undo removes the last completed stroke`() {
        val scene = gc_scene_create()
        try {
            gc_scene_submit_input(scene, 0.1f, 0.1f, DRAW_START, 1.0f, 0L)
            gc_scene_submit_input(scene, 0.1f, 0.1f, DRAW_END, 1.0f, 10L)
            assertEquals(1, gc_scene_stroke_count(scene))
            assertTrue(gc_scene_can_undo(scene))

            gc_scene_undo(scene)

            assertEquals(0, gc_scene_stroke_count(scene))
            assertFalse(gc_scene_can_undo(scene))
            assertTrue(gc_scene_can_redo(scene))
        } finally {
            gc_scene_destroy(scene)
        }
    }
}
