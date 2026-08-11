package com.gesturecontrol.domain.ui

data class DwellZone(val id: String)

/**
 * Fires a step event once a [DwellZone] has been continuously occupied for [intervalMs], then
 * repeats at that same cadence for as long as the same zone stays occupied. Occupying a
 * different zone (or none) resets the timer, so briefly passing through a zone -- or moving
 * straight from one zone to another -- never fires a step.
 */
class EdgeDwellStepper(private val intervalMs: Long = 1200L) {
    private var activeZone: DwellZone? = null
    private var zoneEnteredMs: Long = 0
    private var stepsFiredInZone: Int = 0

    fun onFrame(zone: DwellZone?, timestampMs: Long): Boolean {
        if (zone != activeZone) {
            activeZone = zone
            zoneEnteredMs = timestampMs
            stepsFiredInZone = 0
            return false
        }
        if (zone == null) return false

        val dueSteps = ((timestampMs - zoneEnteredMs) / intervalMs).toInt()
        if (dueSteps > stepsFiredInZone) {
            stepsFiredInZone = dueSteps
            return true
        }
        return false
    }
}
