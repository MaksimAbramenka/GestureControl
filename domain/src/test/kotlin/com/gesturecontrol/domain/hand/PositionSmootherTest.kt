package com.gesturecontrol.domain.hand

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PositionSmootherTest {
    @Test
    fun `the first point is returned as-is`() {
        val smoother = PositionSmoother(smoothingFactor = 0.5f)

        val result = smoother.smooth(NormalizedPoint(0.2f, 0.4f, 0f))

        assertEquals(NormalizedPoint(0.2f, 0.4f, 0f), result)
    }

    @Test
    fun `a null point resets the smoother and returns null`() {
        val smoother = PositionSmoother(smoothingFactor = 0.5f)
        smoother.smooth(NormalizedPoint(0.2f, 0.4f, 0f))

        val result = smoother.smooth(null)

        assertNull(result)
    }

    @Test
    fun `after a reset the next point is returned as-is again, not blended with the old value`() {
        val smoother = PositionSmoother(smoothingFactor = 0.5f)
        smoother.smooth(NormalizedPoint(0.2f, 0.4f, 0f))
        smoother.smooth(null)

        val result = smoother.smooth(NormalizedPoint(0.8f, 0.9f, 0f))

        assertEquals(NormalizedPoint(0.8f, 0.9f, 0f), result)
    }

    @Test
    fun `a subsequent point is blended toward the new value by the smoothing factor`() {
        val smoother = PositionSmoother(smoothingFactor = 0.5f)
        smoother.smooth(NormalizedPoint(0f, 0f, 0f))

        val result = smoother.smooth(NormalizedPoint(1f, 1f, 0f))

        assertEquals(NormalizedPoint(0.5f, 0.5f, 0f), result)
    }

    @Test
    fun `repeated identical jitter around a point converges toward it rather than amplifying`() {
        val smoother = PositionSmoother(smoothingFactor = 0.3f)
        smoother.smooth(NormalizedPoint(0.5f, 0.5f, 0f))

        var result = NormalizedPoint(0f, 0f, 0f)
        repeat(20) {
            result = smoother.smooth(NormalizedPoint(0.6f, 0.4f, 0f))!!
        }

        assertEquals(0.6f, result.x, 0.01f)
        assertEquals(0.4f, result.y, 0.01f)
    }
}
