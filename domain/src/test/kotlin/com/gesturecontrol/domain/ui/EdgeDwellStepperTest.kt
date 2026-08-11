package com.gesturecontrol.domain.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EdgeDwellStepperTest {
    private val zone = DwellZone("color:left")

    @Test
    fun `no zone never fires`() {
        val stepper = EdgeDwellStepper(intervalMs = 1000L)

        assertFalse(stepper.onFrame(null, timestampMs = 0))
        assertFalse(stepper.onFrame(null, timestampMs = 5000))
    }

    @Test
    fun `entering a zone does not fire immediately`() {
        val stepper = EdgeDwellStepper(intervalMs = 1000L)

        assertFalse(stepper.onFrame(zone, timestampMs = 0))
    }

    @Test
    fun `fires once the interval has elapsed while still in the same zone`() {
        val stepper = EdgeDwellStepper(intervalMs = 1000L)

        stepper.onFrame(zone, timestampMs = 0)
        assertFalse(stepper.onFrame(zone, timestampMs = 900))
        assertTrue(stepper.onFrame(zone, timestampMs = 1000))
    }

    @Test
    fun `repeats at the same cadence while the zone stays occupied`() {
        val stepper = EdgeDwellStepper(intervalMs = 1000L)

        stepper.onFrame(zone, timestampMs = 0)
        assertTrue(stepper.onFrame(zone, timestampMs = 1000))
        assertFalse(stepper.onFrame(zone, timestampMs = 1900))
        assertTrue(stepper.onFrame(zone, timestampMs = 2000))
        assertTrue(stepper.onFrame(zone, timestampMs = 3050))
    }

    @Test
    fun `leaving the zone and returning resets the timer`() {
        val stepper = EdgeDwellStepper(intervalMs = 1000L)

        stepper.onFrame(zone, timestampMs = 0)
        assertFalse(stepper.onFrame(null, timestampMs = 500))
        assertFalse(stepper.onFrame(zone, timestampMs = 600))
        assertFalse(stepper.onFrame(zone, timestampMs = 1500))
        assertTrue(stepper.onFrame(zone, timestampMs = 1600))
    }

    @Test
    fun `switching directly to a different zone resets the timer`() {
        val stepper = EdgeDwellStepper(intervalMs = 1000L)
        val otherZone = DwellZone("size:right")

        stepper.onFrame(zone, timestampMs = 0)
        assertFalse(stepper.onFrame(otherZone, timestampMs = 950))
        assertFalse(stepper.onFrame(otherZone, timestampMs = 1900))
        assertTrue(stepper.onFrame(otherZone, timestampMs = 1950))
    }

    @Test
    fun `a brief pass through a zone never fires`() {
        val stepper = EdgeDwellStepper(intervalMs = 1000L)

        stepper.onFrame(zone, timestampMs = 0)
        stepper.onFrame(zone, timestampMs = 100)
        assertFalse(stepper.onFrame(null, timestampMs = 200))
    }
}
