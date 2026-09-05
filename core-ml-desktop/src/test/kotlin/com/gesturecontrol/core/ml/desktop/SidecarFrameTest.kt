package com.gesturecontrol.core.ml.desktop

import com.gesturecontrol.domain.hand.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SidecarFrameTest {
    private fun landmarks21(): List<List<Float>> = List(21) { i -> listOf(i * 0.01f, i * 0.02f, i * 0.001f) }

    @Test
    fun `a frame with 21 landmarks and a handedness maps to HandLandmarks`() {
        val frame = SidecarFrame(ts = 100, width = 640, height = 480, handedness = "Right", landmarks = landmarks21())

        val result = frame.toDomain()

        assertEquals(21, result?.points?.size)
        assertEquals(Handedness.RIGHT, result?.handedness)
        assertEquals(640, frame.imageDimensions().width)
        assertEquals(480, frame.imageDimensions().height)
    }

    @Test
    fun `a frame with null landmarks maps to no hand`() {
        val frame = SidecarFrame(ts = 100, width = 640, height = 480, handedness = null, landmarks = null)

        assertNull(frame.toDomain())
    }

    @Test
    fun `a frame with the wrong landmark count is dropped rather than crashing`() {
        val frame =
            SidecarFrame(ts = 100, width = 640, height = 480, handedness = "Left", landmarks = landmarks21().drop(1))

        assertNull(frame.toDomain())
    }

    @Test
    fun `an unrecognized handedness string maps to no handedness rather than crashing`() {
        val frame = SidecarFrame(ts = 100, width = 640, height = 480, handedness = "Unknown", landmarks = landmarks21())

        assertEquals(null, frame.toDomain()?.handedness)
    }
}
