package com.gesturecontrol.domain.performance

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameRateTrackerTest {
    private val tolerance = 0.01f

    @Test
    fun `first frame has no prior interval to measure -- reports zero`() {
        val tracker = FrameRateTracker()

        assertEquals(0f, tracker.onFrame(1000L), tolerance)
    }

    @Test
    fun `second frame computes instantaneous fps from the interval`() {
        val tracker = FrameRateTracker()
        tracker.onFrame(0L)

        val fps = tracker.onFrame(100L)

        assertEquals(10f, fps, tolerance)
    }

    @Test
    fun `smooths toward the new instantaneous fps instead of jumping directly`() {
        val tracker = FrameRateTracker(smoothingFactor = 0.5f)
        tracker.onFrame(0L)
        tracker.onFrame(100L)

        val fps = tracker.onFrame(150L)

        assertEquals(15f, fps, tolerance)
    }

    @Test
    fun `ignores a duplicate timestamp instead of dividing by zero`() {
        val tracker = FrameRateTracker()
        tracker.onFrame(0L)
        tracker.onFrame(100L)

        val fps = tracker.onFrame(100L)

        assertEquals(10f, fps, tolerance)
    }

    @Test
    fun `ignores an out-of-order timestamp instead of reporting negative fps`() {
        val tracker = FrameRateTracker()
        tracker.onFrame(0L)
        tracker.onFrame(100L)

        val fps = tracker.onFrame(50L)

        assertEquals(10f, fps, tolerance)
    }

    @Test
    fun `zero smoothing factor tracks the instantaneous value exactly`() {
        val tracker = FrameRateTracker(smoothingFactor = 1f)
        tracker.onFrame(0L)
        tracker.onFrame(100L)

        val fps = tracker.onFrame(150L)

        assertEquals(20f, fps, tolerance)
    }
}
