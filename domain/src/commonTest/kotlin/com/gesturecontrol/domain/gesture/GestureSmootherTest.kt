package com.gesturecontrol.domain.gesture

import kotlin.test.Test
import kotlin.test.assertEquals

class GestureSmootherTest {
    @Test
    fun `a single frame is returned as-is`() {
        val smoother = GestureSmoother(windowSize = 5)

        val result = smoother.smooth(GestureClass.DRAW)

        assertEquals(GestureClass.DRAW, result)
    }

    @Test
    fun `returns the majority class once the window fills with a clear majority`() {
        val smoother = GestureSmoother(windowSize = 5)

        smoother.smooth(GestureClass.HOVER)
        smoother.smooth(GestureClass.DRAW)
        smoother.smooth(GestureClass.DRAW)
        smoother.smooth(GestureClass.DRAW)
        val result = smoother.smooth(GestureClass.DRAW)

        assertEquals(GestureClass.DRAW, result)
    }

    @Test
    fun `a single-frame flicker does not flip the smoothed result`() {
        val smoother = GestureSmoother(windowSize = 5)

        smoother.smooth(GestureClass.HOVER)
        smoother.smooth(GestureClass.HOVER)
        smoother.smooth(GestureClass.HOVER)
        smoother.smooth(GestureClass.DRAW)
        val result = smoother.smooth(GestureClass.HOVER)

        assertEquals(GestureClass.HOVER, result)
    }

    @Test
    fun `old frames outside the window no longer count toward the majority`() {
        val smoother = GestureSmoother(windowSize = 3)

        smoother.smooth(GestureClass.ERASE)
        smoother.smooth(GestureClass.ERASE)
        smoother.smooth(GestureClass.ERASE)
        smoother.smooth(GestureClass.IDLE)
        smoother.smooth(GestureClass.IDLE)
        val result = smoother.smooth(GestureClass.IDLE)

        assertEquals(GestureClass.IDLE, result)
    }

    @Test
    fun `a sustained gesture change eventually wins out over the previous majority`() {
        val smoother = GestureSmoother(windowSize = 5)

        repeat(5) { smoother.smooth(GestureClass.IDLE) }
        smoother.smooth(GestureClass.DRAW)
        smoother.smooth(GestureClass.DRAW)
        val result = smoother.smooth(GestureClass.DRAW)
        assertEquals(GestureClass.DRAW, result)
    }
}
