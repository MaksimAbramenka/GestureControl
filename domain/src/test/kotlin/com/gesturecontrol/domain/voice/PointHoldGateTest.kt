package com.gesturecontrol.domain.voice

import com.gesturecontrol.domain.gesture.GestureClass
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PointHoldGateTest {
    @Test
    fun `no gesture never fires`() {
        val gate = PointHoldGate(holdThresholdMs = 500L)

        assertFalse(gate.onFrame(null, timestampMs = 0))
        assertFalse(gate.onFrame(null, timestampMs = 5000))
    }

    @Test
    fun `a gesture other than POINT never fires`() {
        val gate = PointHoldGate(holdThresholdMs = 500L)

        assertFalse(gate.onFrame(GestureClass.HOVER, timestampMs = 0))
        assertFalse(gate.onFrame(GestureClass.DRAW, timestampMs = 1000))
    }

    @Test
    fun `holding POINT does not fire immediately`() {
        val gate = PointHoldGate(holdThresholdMs = 500L)

        assertFalse(gate.onFrame(GestureClass.POINT, timestampMs = 0))
    }

    @Test
    fun `fires once the hold threshold has elapsed while still holding POINT`() {
        val gate = PointHoldGate(holdThresholdMs = 500L)

        gate.onFrame(GestureClass.POINT, timestampMs = 0)
        assertFalse(gate.onFrame(GestureClass.POINT, timestampMs = 400))
        assertTrue(gate.onFrame(GestureClass.POINT, timestampMs = 500))
    }

    @Test
    fun `does not fire again while still holding after the first fire`() {
        val gate = PointHoldGate(holdThresholdMs = 500L)

        gate.onFrame(GestureClass.POINT, timestampMs = 0)
        assertTrue(gate.onFrame(GestureClass.POINT, timestampMs = 500))
        assertFalse(gate.onFrame(GestureClass.POINT, timestampMs = 600))
        assertFalse(gate.onFrame(GestureClass.POINT, timestampMs = 2000))
    }

    @Test
    fun `releasing and re-holding POINT fires again`() {
        val gate = PointHoldGate(holdThresholdMs = 500L)

        gate.onFrame(GestureClass.POINT, timestampMs = 0)
        assertTrue(gate.onFrame(GestureClass.POINT, timestampMs = 500))
        gate.onFrame(null, timestampMs = 600)
        gate.onFrame(GestureClass.POINT, timestampMs = 700)
        assertFalse(gate.onFrame(GestureClass.POINT, timestampMs = 1100))
        assertTrue(gate.onFrame(GestureClass.POINT, timestampMs = 1200))
    }

    @Test
    fun `switching from POINT to a different gesture resets the timer`() {
        val gate = PointHoldGate(holdThresholdMs = 500L)

        gate.onFrame(GestureClass.POINT, timestampMs = 0)
        gate.onFrame(GestureClass.POINT, timestampMs = 400)
        assertFalse(gate.onFrame(GestureClass.HOVER, timestampMs = 450))
        gate.onFrame(GestureClass.POINT, timestampMs = 500)
        assertFalse(gate.onFrame(GestureClass.POINT, timestampMs = 900))
        assertTrue(gate.onFrame(GestureClass.POINT, timestampMs = 1000))
    }

    @Test
    fun `a brief pass through POINT never fires`() {
        val gate = PointHoldGate(holdThresholdMs = 500L)

        gate.onFrame(GestureClass.POINT, timestampMs = 0)
        gate.onFrame(GestureClass.POINT, timestampMs = 100)
        assertFalse(gate.onFrame(null, timestampMs = 200))
    }
}
